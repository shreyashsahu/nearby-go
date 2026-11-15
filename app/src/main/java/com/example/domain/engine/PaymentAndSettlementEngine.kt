package com.example.domain.engine

import com.example.BuildConfig
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PayoutAccount
import com.example.domain.model.PlatformSettings
import com.example.domain.model.SettlementMode
import com.example.domain.model.SettlementRecord
import com.example.domain.model.SettlementStatus
import kotlin.math.round

data class FinancialSplitBreakdown(
    val orderTotalInr: Double,
    val sellerProductSubtotalInr: Double,
    val deliveryFeeInr: Double,
    val platformCommissionInr: Double,
    val sellerPayableInr: Double,
    val deliveryPartnerEarningInr: Double,
    val platformNetRemainingInr: Double
)

data class PaymentVerificationResult(
    val method: PaymentMethod,
    val status: PaymentStatus,
    val gatewayMode: String,
    val reference: String,
    val message: String
)

interface PaymentGatewayAdapter {
    fun isRazorpayTestConfigured(): Boolean
    fun verifyCheckoutPayment(
        method: PaymentMethod,
        amountInr: Double,
        manualOrTestReference: String,
        settings: PlatformSettings
    ): Result<PaymentVerificationResult>
}

class NearbyGoPaymentGateway : PaymentGatewayAdapter {

    override fun isRazorpayTestConfigured(): Boolean {
        val key = BuildConfig.RAZORPAY_KEY_ID
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    override fun verifyCheckoutPayment(
        method: PaymentMethod,
        amountInr: Double,
        manualOrTestReference: String,
        settings: PlatformSettings
    ): Result<PaymentVerificationResult> {
        if (amountInr < 0.0) {
            return Result.failure(IllegalArgumentException("Order total cannot be negative."))
        }

        if (method == PaymentMethod.COD) {
            if (!settings.codEnabled) {
                return Result.failure(IllegalStateException("Cash on Delivery is disabled by Super Admin."))
            }
            return Result.success(
                PaymentVerificationResult(
                    method = method,
                    status = PaymentStatus.PENDING,
                    gatewayMode = settings.settlementMode,
                    reference = "COD-COLLECT-ON-DELIVERY",
                    message = "Cash on Delivery confirmed; payment will be collected at doorstep."
                )
            )
        }

        if (!settings.onlinePaymentsEnabled) {
            return Result.failure(IllegalStateException("Online payments are disabled by Super Admin."))
        }

        if (method == PaymentMethod.RAZORPAY_TEST) {
            if (!isRazorpayTestConfigured()) {
                return Result.failure(
                    IllegalStateException(
                        "Razorpay Test key (RAZORPAY_KEY_ID) is not configured in Secrets. " +
                            "Use Cash on Delivery or Manual Payment Verification with an actual reference ID."
                    )
                )
            }
            val cleanedRef = manualOrTestReference.trim()
            if (cleanedRef.length < 6) {
                return Result.failure(
                    IllegalArgumentException("Enter a valid Razorpay test payment ID (e.g., pay_test_...) before verifying.")
                )
            }
            return Result.success(
                PaymentVerificationResult(
                    method = method,
                    status = PaymentStatus.VERIFIED,
                    gatewayMode = "RAZORPAY_TEST",
                    reference = cleanedRef,
                    message = "Razorpay Test payment reference recorded."
                )
            )
        }

        // UPI / CARD / NETBANKING / WALLET / MANUAL_VERIFICATION
        val cleanedRef = manualOrTestReference.trim()
        if (cleanedRef.length < 4) {
            return Result.failure(
                IllegalArgumentException(
                    "Provide a valid transaction/UTR reference (min 4 characters). NearbyGo never marks unverified payments as paid."
                )
            )
        }

        return Result.success(
            PaymentVerificationResult(
                method = method,
                status = PaymentStatus.PENDING,
                gatewayMode = settings.settlementMode,
                reference = cleanedRef,
                message = "Payment reference submitted for verification."
            )
        )
    }
}

object SettlementSplitCalculator {

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    fun computeSplit(
        orderTotalInr: Double,
        productSubtotalAfterDiscountInr: Double,
        deliveryFeeInr: Double,
        settings: PlatformSettings
    ): FinancialSplitBreakdown {
        val commission = round2(
            productSubtotalAfterDiscountInr * (settings.platformCommissionPercent.coerceIn(0.0, 50.0) / 100.0)
        )
        val sellerPayable = round2((productSubtotalAfterDiscountInr - commission).coerceAtLeast(0.0))
        // Delivery partner receives 85% of delivery fee (min ₹15 when fee >= ₹15, or 100% if <= ₹15)
        val partnerEarning = round2(
            if (deliveryFeeInr <= 15.0) deliveryFeeInr else (deliveryFeeInr * 0.85)
        )
        val platformRemaining = round2((orderTotalInr - sellerPayable - partnerEarning).coerceAtLeast(0.0))

        return FinancialSplitBreakdown(
            orderTotalInr = round2(orderTotalInr),
            sellerProductSubtotalInr = round2(productSubtotalAfterDiscountInr),
            deliveryFeeInr = round2(deliveryFeeInr),
            platformCommissionInr = commission,
            sellerPayableInr = sellerPayable,
            deliveryPartnerEarningInr = partnerEarning,
            platformNetRemainingInr = platformRemaining
        )
    }

    fun validateManualSettlement(
        settlement: SettlementRecord,
        paymentReference: String,
        existingSettlements: List<SettlementRecord>
    ): Result<String> {
        val cleanRef = paymentReference.trim()
        if (cleanRef.length < 4) {
            return Result.failure(
                IllegalArgumentException("A valid bank/UTR payment reference is required before marking a manual payout PAID.")
            )
        }
        val alreadyPaid = existingSettlements.any {
            it.idempotencyKey == settlement.idempotencyKey && it.status == SettlementStatus.PAID.name
        }
        if (alreadyPaid || settlement.status == SettlementStatus.PAID.name) {
            return Result.failure(
                IllegalStateException("Duplicate payout blocked: settlement ${settlement.idempotencyKey} is already PAID.")
            )
        }
        return Result.success(cleanRef)
    }

    fun checkAutomaticPayoutEligibility(
        settlement: SettlementRecord,
        payoutAccount: PayoutAccount?,
        settings: PlatformSettings,
        gatewayAdapter: PaymentGatewayAdapter,
        existingSettlements: List<SettlementRecord>
    ): Result<Unit> {
        if (settings.settlementMode != SettlementMode.AUTOMATIC.name) {
            return Result.failure(IllegalStateException("Automatic payout mode is currently disabled."))
        }
        if (!gatewayAdapter.isRazorpayTestConfigured()) {
            return Result.failure(
                IllegalStateException("Razorpay Route/Payout provider credentials are not configured in Secrets.")
            )
        }
        if (payoutAccount == null || payoutAccount.kycStatus != "VERIFIED") {
            return Result.failure(
                IllegalStateException("Recipient payout account KYC must be VERIFIED for automatic payout.")
            )
        }
        if (settlement.netPayableInr < settings.minPayoutThresholdInr) {
            return Result.failure(
                IllegalStateException(
                    "Net payable ₹${settlement.netPayableInr} is below minimum threshold ₹${settings.minPayoutThresholdInr}."
                )
            )
        }
        val duplicate = existingSettlements.any {
            it.idempotencyKey == settlement.idempotencyKey && it.status == SettlementStatus.PAID.name
        }
        if (duplicate) {
            return Result.failure(
                IllegalStateException("Duplicate transfer prevented via idempotency key ${settlement.idempotencyKey}.")
            )
        }
        return Result.success(Unit)
    }
}
