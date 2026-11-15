package com.example.domain.engine

import com.example.domain.model.Order
import com.example.domain.model.OrderStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PayoutAccount
import com.example.domain.model.PlatformSettings
import com.example.domain.model.SettlementMode
import com.example.domain.model.SettlementRecord
import com.example.domain.model.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyGoEnginesTest {

    @Test
    fun deliveryFeeEngine_tiered500mSlabs_matchesExactSpecification() {
        val settings = PlatformSettings(deliveryBaseFeeInr = 5.0, deliveryPer500mInr = 5.0)

        assertEquals(5.0, DeliveryFeeEngine.calculateQuote(350L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(5.0, DeliveryFeeEngine.calculateQuote(500L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(10.0, DeliveryFeeEngine.calculateQuote(501L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(10.0, DeliveryFeeEngine.calculateQuote(1000L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(15.0, DeliveryFeeEngine.calculateQuote(1250L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(20.0, DeliveryFeeEngine.calculateQuote(2000L, settings).getOrThrow().deliveryFeeInr, 0.01)
        assertEquals(25.0, DeliveryFeeEngine.calculateQuote(2100L, settings).getOrThrow().deliveryFeeInr, 0.01)
    }

    @Test
    fun deliveryFeeEngine_rejectsNegativeOrOutOfRangeDistance() {
        val settings = PlatformSettings()
        assertTrue(DeliveryFeeEngine.calculateQuote(-10L, settings).isFailure)
        assertTrue(DeliveryFeeEngine.calculateQuote(50_000L, settings).isFailure)
    }

    @Test
    fun orderLifecycleEngine_validatesTransitionsAndBusyPartnerLock() {
        assertTrue(OrderLifecycleEngine.canTransition(OrderStatus.PLACED, OrderStatus.ACCEPTED))
        assertFalse(OrderLifecycleEngine.canTransition(OrderStatus.PLACED, OrderStatus.DELIVERED))
        assertFalse(OrderLifecycleEngine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED))

        val readyOrder = Order(id = "ord_1", status = OrderStatus.READY.name, deliveryPartnerId = "")
        assertTrue(OrderLifecycleEngine.canPartnerClaim(readyOrder, "p_1", partnerIsBusy = false).isSuccess)
        assertTrue(OrderLifecycleEngine.canPartnerClaim(readyOrder, "p_1", partnerIsBusy = true).isFailure)

        val assignedOrder = readyOrder.copy(status = OrderStatus.ASSIGNED.name, deliveryPartnerId = "p_1")
        assertTrue(OrderLifecycleEngine.canPartnerClaim(assignedOrder, "p_2", partnerIsBusy = false).isFailure)
    }

    @Test
    fun paymentAndSettlementEngine_validatesSplitsManualRefAndIdempotency() {
        val settings = PlatformSettings(
            platformCommissionPercent = 10.0,
            settlementMode = SettlementMode.MANUAL.name,
            minPayoutThresholdInr = 100.0
        )
        val split = SettlementSplitCalculator.computeSplit(
            orderTotalInr = 420.0,
            productSubtotalAfterDiscountInr = 400.0,
            deliveryFeeInr = 20.0,
            settings = settings
        )
        assertEquals(40.0, split.platformCommissionInr, 0.01)
        assertEquals(360.0, split.sellerPayableInr, 0.01)
        assertEquals(17.0, split.deliveryPartnerEarningInr, 0.01)
        assertEquals(43.0, split.platformNetRemainingInr, 0.01)

        val gateway = NearbyGoPaymentGateway()
        assertTrue(gateway.verifyCheckoutPayment(PaymentMethod.UPI, 420.0, "", settings).isFailure)
        assertTrue(gateway.verifyCheckoutPayment(PaymentMethod.UPI, 420.0, "UTR12345", settings).isSuccess)
        assertTrue(gateway.verifyCheckoutPayment(PaymentMethod.COD, 420.0, "", settings).isSuccess)

        val pending = SettlementRecord(
            id = "stl_1",
            recipientId = "s_1",
            orderId = "ord_1",
            netPayableInr = 360.0,
            status = SettlementStatus.PENDING.name,
            idempotencyKey = "stl_seller_ord_1"
        )
        assertTrue(SettlementSplitCalculator.validateManualSettlement(pending, "", emptyList()).isFailure)
        assertTrue(SettlementSplitCalculator.validateManualSettlement(pending, "UTR998877", emptyList()).isSuccess)

        val alreadyPaid = pending.copy(status = SettlementStatus.PAID.name)
        assertTrue(
            SettlementSplitCalculator.validateManualSettlement(pending, "UTR998877", listOf(alreadyPaid)).isFailure
        )

        val kycPending = PayoutAccount(userId = "s_1", kycStatus = "PENDING")
        assertTrue(
            SettlementSplitCalculator.checkAutomaticPayoutEligibility(
                pending,
                kycPending,
                settings.copy(settlementMode = SettlementMode.AUTOMATIC.name),
                gateway,
                emptyList()
            ).isFailure
        )
    }
}
