package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PortalRole(val label: String) {
    CUSTOMER("Customer"),
    SELLER("Seller"),
    DELIVERY("Partner"),
    ADMIN("Super Admin")
}

enum class StoreCategory(val label: String) {
    GROCERY("Grocery"),
    FOOD("Food"),
    MEDICINES("Medicines"),
    LOCAL_SHOP("Local Shop")
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    SUSPENDED
}

enum class OrderStatus {
    PLACED,
    ACCEPTED,
    PREPARING,
    READY,
    ASSIGNED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    REJECTED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED;

    val isTerminal: Boolean
        get() = this == DELIVERED || this == REJECTED || this == CANCELLED || this == REFUNDED

    val isActive: Boolean
        get() = this in setOf(
            PLACED, ACCEPTED, PREPARING, READY, ASSIGNED, PICKED_UP, OUT_FOR_DELIVERY
        )
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    NETBANKING("Net Banking"),
    WALLET("NearbyGo Wallet"),
    RAZORPAY_TEST("Razorpay Test"),
    COD("Cash on Delivery"),
    MANUAL_VERIFICATION("Manual Verification")
}

enum class PaymentStatus {
    PENDING,
    VERIFIED,
    FAILED,
    REFUND_PENDING,
    REFUNDED
}

enum class SettlementMode {
    MANUAL,
    AUTOMATIC
}

enum class SettlementStatus {
    PENDING,
    PAID,
    FAILED
}

enum class ComplaintStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val activePortal: String = PortalRole.CUSTOMER.name,
    val savedAddresses: List<String> = emptyList(),
    val walletBalanceInr: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Store(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val category: String = StoreCategory.GROCERY.name,
    val description: String = "",
    val address: String = "",
    val latitude: Double = 12.9716,
    val longitude: Double = 77.5946,
    val openingHours: String = "07:00 AM - 11:00 PM",
    val isOpen: Boolean = true,
    val autoAcceptOrders: Boolean = false,
    val approvalStatus: String = ApprovalStatus.PENDING.name,
    val rating: Double = 4.6,
    val reviewCount: Long = 0L,
    val bannerImageKey: String = "grocery",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val ownerId: String = "",
    val name: String = "",
    val category: String = "Essentials",
    val description: String = "",
    val priceInr: Double = 0.0,
    val discountPercent: Long = 0L,
    val stockCount: Long = 0L,
    val isAvailable: Boolean = true,
    val imageUrl: String = "",
    val unitLabel: String = "1 pack",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectivePriceInr: Double
        get() {
            val pct = discountPercent.coerceIn(0L, 90L)
            return kotlin.math.round((priceInr * (100.0 - pct) / 100.0) * 100.0) / 100.0
        }
}

@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val productId: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val productName: String = "",
    val unitPriceInr: Double = 0.0,
    val discountPercent: Long = 0L,
    val quantity: Long = 1L,
    val unitLabel: String = "1 pack",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveUnitPriceInr: Double
        get() {
            val pct = discountPercent.coerceIn(0L, 90L)
            return kotlin.math.round((unitPriceInr * (100.0 - pct) / 100.0) * 100.0) / 100.0
        }

    val lineTotalInr: Double
        get() = kotlin.math.round((effectiveUnitPriceInr * quantity) * 100.0) / 100.0
}

@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val sellerId: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val deliveryPartnerId: String = "",
    val deliveryPartnerName: String = "",
    val status: String = OrderStatus.PLACED.name,
    val paymentMethod: String = PaymentMethod.UPI.name,
    val paymentStatus: String = PaymentStatus.PENDING.name,
    val paymentReference: String = "",
    val subtotalInr: Double = 0.0,
    val discountInr: Double = 0.0,
    val deliveryFeeInr: Double = 0.0,
    val totalInr: Double = 0.0,
    val distanceMeters: Long = 0L,
    val deliveryAddress: String = "",
    val couponCode: String = "",
    val itemsSummary: List<String> = emptyList(),
    val timelineEvents: List<String> = emptyList(),
    val sellerEarningInr: Double = 0.0,
    val deliveryEarningInr: Double = 0.0,
    val platformCommissionInr: Double = 0.0,
    val platformNetInr: Double = 0.0,
    val settlementStatus: String = SettlementStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeliveryPartner(
    @DocumentId val id: String = "",
    val partnerId: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleType: String = "Electric Scooter",
    val approvalStatus: String = ApprovalStatus.PENDING.name,
    val isOnline: Boolean = false,
    val isBusy: Boolean = false,
    val activeOrderId: String = "",
    val currentLat: Double = 12.9716,
    val currentLng: Double = 77.5946,
    val completedDeliveries: Long = 0L,
    val totalEarningsInr: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeliveryAssignment(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val partnerId: String = "",
    val sellerId: String = "",
    val customerId: String = "",
    val status: String = "OFFERED",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val sellerId: String = "",
    val amountInr: Double = 0.0,
    val method: String = PaymentMethod.UPI.name,
    val gatewayMode: String = SettlementMode.MANUAL.name,
    val status: String = PaymentStatus.PENDING.name,
    val providerReference: String = "",
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SettlementRecord(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val recipientRole: String = "SELLER",
    val orderId: String = "",
    val grossAmountInr: Double = 0.0,
    val commissionInr: Double = 0.0,
    val netPayableInr: Double = 0.0,
    val payoutMode: String = SettlementMode.MANUAL.name,
    val status: String = SettlementStatus.PENDING.name,
    val paymentReference: String = "",
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PayoutAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "SELLER",
    val accountHolderName: String = "",
    val maskedDestination: String = "",
    val ifscCode: String = "",
    val kycStatus: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Coupon(
    @DocumentId val id: String = "",
    val code: String = "",
    val title: String = "",
    val discountType: String = "FLAT",
    val discountValue: Double = 0.0,
    val minOrderInr: Double = 0.0,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val storeId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Complaint(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val orderId: String = "",
    val subject: String = "",
    val description: String = "",
    val status: String = ComplaintStatus.OPEN.name,
    val adminResolutionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val orderId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "",
    val onlinePaymentsEnabled: Boolean = true,
    val codEnabled: Boolean = true,
    val settlementMode: String = SettlementMode.MANUAL.name,
    val platformCommissionPercent: Double = 10.0,
    val sellerPayoutPercent: Double = 85.0,
    val deliveryBaseFeeInr: Double = 5.0,
    val deliveryPer500mInr: Double = 5.0,
    val minPayoutThresholdInr: Double = 100.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
