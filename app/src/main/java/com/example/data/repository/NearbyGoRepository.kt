package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.engine.DeliveryFeeEngine
import com.example.domain.engine.OrderLifecycleEngine
import com.example.domain.engine.PaymentGatewayAdapter
import com.example.domain.engine.SettlementSplitCalculator
import com.example.domain.model.ApprovalStatus
import com.example.domain.model.AuditLog
import com.example.domain.model.CartItem
import com.example.domain.model.Complaint
import com.example.domain.model.ComplaintStatus
import com.example.domain.model.Coupon
import com.example.domain.model.DeliveryPartner
import com.example.domain.model.Order
import com.example.domain.model.OrderStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentRecord
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PayoutAccount
import com.example.domain.model.PlatformSettings
import com.example.domain.model.PortalRole
import com.example.domain.model.Product
import com.example.domain.model.Review
import com.example.domain.model.SettlementMode
import com.example.domain.model.SettlementRecord
import com.example.domain.model.SettlementStatus
import com.example.domain.model.Store
import com.example.domain.model.StoreCategory
import com.example.domain.model.UserNotification
import com.example.domain.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface NearbyGoRepository {
    fun observeUserProfile(userId: String): Flow<UserProfile?>
    suspend fun ensureUserProfile(userId: String, displayName: String, email: String): Result<UserProfile>
    suspend fun updateUserProfile(
        userId: String,
        displayName: String,
        phone: String,
        savedAddresses: List<String>,
        activePortal: PortalRole
    ): Result<Unit>

    fun observeApprovedStores(): Flow<List<Store>>
    fun observeMyStores(ownerId: String): Flow<List<Store>>
    fun observeAllStoresForAdmin(): Flow<List<Store>>
    suspend fun createStore(
        ownerId: String,
        isAdmin: Boolean,
        name: String,
        category: StoreCategory,
        description: String,
        address: String,
        latitude: Double,
        longitude: Double,
        openingHours: String,
        autoAcceptOrders: Boolean
    ): Result<String>
    suspend fun updateStoreOperationalSettings(store: Store, isOpen: Boolean, autoAcceptOrders: Boolean): Result<Unit>
    suspend fun setStoreApprovalStatus(storeId: String, status: ApprovalStatus, actorId: String, actorEmail: String): Result<Unit>

    fun observeAvailableProducts(): Flow<List<Product>>
    fun observeMyProducts(ownerId: String): Flow<List<Product>>
    suspend fun upsertProduct(
        productId: String?,
        storeId: String,
        ownerId: String,
        name: String,
        category: String,
        description: String,
        priceInr: Double,
        discountPercent: Long,
        stockCount: Long,
        isAvailable: Boolean,
        imageUrl: String,
        unitLabel: String
    ): Result<String>

    fun observeCart(userId: String): Flow<List<CartItem>>
    suspend fun addToCart(userId: String, product: Product, storeName: String): Result<Unit>
    suspend fun updateCartQuantity(userId: String, cartItemId: String, newQuantity: Long): Result<Unit>
    suspend fun clearCart(userId: String): Result<Unit>

    fun observeCustomerOrders(customerId: String): Flow<List<Order>>
    fun observeSellerOrders(sellerId: String): Flow<List<Order>>
    fun observePartnerAssignedOrders(partnerId: String): Flow<List<Order>>
    fun observeReadyUnassignedOrders(): Flow<List<Order>>
    fun observeAllOrdersForAdmin(): Flow<List<Order>>

    suspend fun placeOrder(
        customerId: String,
        customerName: String,
        store: Store,
        cartItems: List<CartItem>,
        deliveryAddress: String,
        distanceMeters: Long,
        coupon: Coupon?,
        paymentMethod: PaymentMethod,
        paymentReference: String,
        settings: PlatformSettings,
        gatewayAdapter: PaymentGatewayAdapter
    ): Result<String>

    suspend fun advanceOrderBySeller(order: Order, nextStatus: OrderStatus): Result<Unit>
    suspend fun cancelOrderByCustomer(order: Order): Result<Unit>
    suspend fun claimOrderByPartner(order: Order, partner: DeliveryPartner): Result<Unit>
    suspend fun releaseOrderByPartner(order: Order, partnerId: String, reason: String): Result<Unit>
    suspend fun advanceOrderByPartner(order: Order, nextStatus: OrderStatus, partner: DeliveryPartner): Result<Unit>

    fun observeDeliveryPartner(partnerId: String): Flow<DeliveryPartner?>
    fun observeAllDeliveryPartnersForAdmin(): Flow<List<DeliveryPartner>>
    suspend fun upsertDeliveryPartnerProfile(partnerId: String, name: String, phone: String, vehicleType: String, isAdmin: Boolean): Result<Unit>
    suspend fun updatePartnerStatusAndLocation(partner: DeliveryPartner, isOnline: Boolean, lat: Double, lng: Double): Result<Unit>
    suspend fun setPartnerApprovalStatus(partnerId: String, status: ApprovalStatus, actorId: String, actorEmail: String): Result<Unit>

    fun observeActiveCoupons(): Flow<List<Coupon>>
    fun observeAllCouponsForAdmin(): Flow<List<Coupon>>
    suspend fun createCoupon(code: String, title: String, discountType: String, discountValue: Double, minOrderInr: Double, actorId: String, actorEmail: String): Result<Unit>

    fun observeReviews(): Flow<List<Review>>
    suspend fun submitReview(order: Order, customerName: String, rating: Long, comment: String): Result<Unit>

    fun observeCustomerComplaints(customerId: String): Flow<List<Complaint>>
    fun observeAllComplaintsForAdmin(): Flow<List<Complaint>>
    suspend fun submitComplaint(customerId: String, customerName: String, orderId: String, subject: String, description: String): Result<String>
    suspend fun resolveComplaintByAdmin(complaint: Complaint, status: ComplaintStatus, resolutionNote: String, actorId: String, actorEmail: String): Result<Unit>

    fun observeNotifications(userId: String): Flow<List<UserNotification>>
    suspend fun markNotificationRead(userId: String, notificationId: String): Result<Unit>

    fun observePlatformSettings(): Flow<PlatformSettings>
    suspend fun updatePlatformSettings(settings: PlatformSettings, actorId: String, actorEmail: String): Result<Unit>

    fun observeMySettlements(recipientId: String): Flow<List<SettlementRecord>>
    fun observeAllSettlementsForAdmin(): Flow<List<SettlementRecord>>
    fun observeAllPaymentsForAdmin(): Flow<List<PaymentRecord>>
    fun observeMyPayoutAccounts(userId: String): Flow<List<PayoutAccount>>
    fun observeAllPayoutAccountsForAdmin(): Flow<List<PayoutAccount>>
    suspend fun upsertPayoutAccount(userId: String, role: String, holderName: String, maskedDest: String, ifsc: String, isAdmin: Boolean): Result<Unit>
    suspend fun verifyPayoutAccountKyc(accountId: String, actorId: String, actorEmail: String): Result<Unit>
    suspend fun settleManualPayout(settlement: SettlementRecord, paymentReference: String, existingSettlements: List<SettlementRecord>, actorId: String, actorEmail: String): Result<Unit>
    suspend fun triggerAutomaticPayout(settlement: SettlementRecord, payoutAccount: PayoutAccount?, settings: PlatformSettings, gatewayAdapter: PaymentGatewayAdapter, existingSettlements: List<SettlementRecord>, actorId: String, actorEmail: String): Result<Unit>
    suspend fun requestOrProcessRefund(order: Order, isAdminAction: Boolean, actorId: String, actorEmail: String): Result<Unit>

    fun observeAuditLogsForAdmin(): Flow<List<AuditLog>>
    suspend fun seedDemoMarketplaceIfAdmin(adminId: String, adminName: String, adminEmail: String): Result<Unit>
}

class FirestoreNearbyGoRepository(
    private val db: FirebaseFirestore
) : NearbyGoRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun <T> Flow<T>.logAndRethrow(op: OperationType, path: String): Flow<T> = catch { error ->
        val firestoreErr = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreErr == null && error is CancellationException) throw error
        handleFirestoreError(firestoreErr ?: error, op, path)
        throw (firestoreErr ?: error)
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.GET, "users/$userId")

    override suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String
    ): Result<UserProfile> = runCatching {
        val docRef = db.collection("users").document(userId)
        val snap = docRef.get().await()
        val existing = snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
        if (existing != null) {
            return@runCatching existing
        }
        val cleanName = displayName.ifBlank { email.substringBefore("@").ifBlank { "NearbyGo User" } }.take(80)
        val payload = mapOf(
            "userId" to userId,
            "displayName" to cleanName,
            "email" to email.ifBlank { "$userId@nearbygo.app" },
            "phone" to "",
            "activePortal" to PortalRole.CUSTOMER.name,
            "savedAddresses" to listOf("Indiranagar 100ft Road, Bengaluru"),
            "walletBalanceInr" to 250.0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        UserProfile(
            id = userId,
            userId = userId,
            displayName = cleanName,
            email = email,
            activePortal = PortalRole.CUSTOMER.name,
            savedAddresses = listOf("Indiranagar 100ft Road, Bengaluru"),
            walletBalanceInr = 250.0,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "users/$userId") }

    override suspend fun updateUserProfile(
        userId: String,
        displayName: String,
        phone: String,
        savedAddresses: List<String>,
        activePortal: PortalRole
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId)
        docRef.update(
            mapOf(
                "displayName" to displayName.trim().take(80),
                "phone" to phone.trim().take(25),
                "savedAddresses" to savedAddresses.take(10),
                "activePortal" to activePortal.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "users/$userId") }

    override fun observeApprovedStores(): Flow<List<Store>> =
        db.collection("stores")
            .whereEqualTo("approvalStatus", ApprovalStatus.APPROVED.name)
            .snapshots()
            .map { snap -> snap.toObjects(Store::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "stores")

    override fun observeMyStores(ownerId: String): Flow<List<Store>> =
        db.collection("stores")
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snap -> snap.toObjects(Store::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "stores")

    override fun observeAllStoresForAdmin(): Flow<List<Store>> =
        db.collection("stores")
            .snapshots()
            .map { snap -> snap.toObjects(Store::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "stores")

    override suspend fun createStore(
        ownerId: String,
        isAdmin: Boolean,
        name: String,
        category: StoreCategory,
        description: String,
        address: String,
        latitude: Double,
        longitude: Double,
        openingHours: String,
        autoAcceptOrders: Boolean
    ): Result<String> = runCatching {
        val storeId = "store_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val status = if (isAdmin) ApprovalStatus.APPROVED.name else ApprovalStatus.PENDING.name
        val payload = mapOf(
            "ownerId" to ownerId,
            "name" to name.trim().take(100),
            "category" to category.name,
            "description" to description.trim().take(500),
            "address" to address.trim().take(250),
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "openingHours" to openingHours.trim().ifBlank { "08:00 AM - 10:00 PM" }.take(80),
            "isOpen" to true,
            "autoAcceptOrders" to autoAcceptOrders,
            "approvalStatus" to status,
            "rating" to 4.7,
            "reviewCount" to 0L,
            "bannerImageKey" to category.name.lowercase(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("stores").document(storeId).set(payload).await()
        storeId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "stores") }

    override suspend fun updateStoreOperationalSettings(
        store: Store,
        isOpen: Boolean,
        autoAcceptOrders: Boolean
    ): Result<Unit> = runCatching {
        db.collection("stores").document(store.id).update(
            mapOf(
                "isOpen" to isOpen,
                "autoAcceptOrders" to autoAcceptOrders,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "stores/${store.id}") }

    override suspend fun setStoreApprovalStatus(
        storeId: String,
        status: ApprovalStatus,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        db.collection("stores").document(storeId).update(
            mapOf(
                "approvalStatus" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(actorId, actorEmail, "STORE_${status.name}", "STORE", storeId, "Updated store approval to ${status.name}")
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "stores/$storeId") }

    override fun observeAvailableProducts(): Flow<List<Product>> =
        db.collection("products")
            .whereEqualTo("isAvailable", true)
            .snapshots()
            .map { snap -> snap.toObjects(Product::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "products")

    override fun observeMyProducts(ownerId: String): Flow<List<Product>> =
        db.collection("products")
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snap -> snap.toObjects(Product::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "products")

    override suspend fun upsertProduct(
        productId: String?,
        storeId: String,
        ownerId: String,
        name: String,
        category: String,
        description: String,
        priceInr: Double,
        discountPercent: Long,
        stockCount: Long,
        isAvailable: Boolean,
        imageUrl: String,
        unitLabel: String
    ): Result<String> = runCatching {
        val docId = productId?.takeIf { it.isNotBlank() }
            ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("products").document(docId)
        if (productId.isNullOrBlank()) {
            docRef.set(
                mapOf(
                    "storeId" to storeId,
                    "ownerId" to ownerId,
                    "name" to name.trim().take(120),
                    "category" to category.trim().ifBlank { "General" }.take(50),
                    "description" to description.trim().take(500),
                    "priceInr" to priceInr.coerceIn(1.0, 500000.0),
                    "discountPercent" to discountPercent.coerceIn(0L, 90L),
                    "stockCount" to stockCount.coerceIn(0L, 100000L),
                    "isAvailable" to (isAvailable && stockCount > 0L),
                    "imageUrl" to imageUrl.take(500),
                    "unitLabel" to unitLabel.trim().ifBlank { "1 unit" }.take(40),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "name" to name.trim().take(120),
                    "category" to category.trim().ifBlank { "General" }.take(50),
                    "description" to description.trim().take(500),
                    "priceInr" to priceInr.coerceIn(1.0, 500000.0),
                    "discountPercent" to discountPercent.coerceIn(0L, 90L),
                    "stockCount" to stockCount.coerceIn(0L, 100000L),
                    "isAvailable" to (isAvailable && stockCount > 0L),
                    "imageUrl" to imageUrl.take(500),
                    "unitLabel" to unitLabel.trim().ifBlank { "1 unit" }.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        docId
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "products") }

    override fun observeCart(userId: String): Flow<List<CartItem>> =
        db.collection("users").document(userId).collection("cart")
            .snapshots()
            .map { snap -> snap.toObjects(CartItem::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "users/$userId/cart")

    override suspend fun addToCart(userId: String, product: Product, storeName: String): Result<Unit> = runCatching {
        require(product.stockCount > 0L && product.isAvailable) { "${product.name} is currently out of stock." }
        val cartRef = db.collection("users").document(userId).collection("cart")
        val existingSnap = cartRef.get().await()
        val existingItems = existingSnap.toObjects(CartItem::class.java, ServerTimestampBehavior.ESTIMATE)
        if (existingItems.isNotEmpty() && existingItems.any { it.storeId != product.storeId }) {
            // Single-store cart integrity: clear prior store items cleanly
            existingSnap.documents.forEach { it.reference.delete().await() }
        }
        val itemDocRef = cartRef.document(product.id)
        val currentQty = existingItems.firstOrNull { it.productId == product.id }?.quantity ?: 0L
        val nextQty = (currentQty + 1L).coerceAtMost(product.stockCount.coerceAtMost(99L))
        itemDocRef.set(
            mapOf(
                "productId" to product.id,
                "storeId" to product.storeId,
                "storeName" to storeName.take(100),
                "productName" to product.name.take(120),
                "unitPriceInr" to product.priceInr,
                "discountPercent" to product.discountPercent,
                "quantity" to nextQty,
                "unitLabel" to product.unitLabel.take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "users/$userId/cart") }

    override suspend fun updateCartQuantity(userId: String, cartItemId: String, newQuantity: Long): Result<Unit> = runCatching {
        val itemRef = db.collection("users").document(userId).collection("cart").document(cartItemId)
        if (newQuantity <= 0L) {
            itemRef.delete().await()
        } else {
            itemRef.update(
                mapOf(
                    "quantity" to newQuantity.coerceIn(1L, 99L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "users/$userId/cart/$cartItemId") }

    override suspend fun clearCart(userId: String): Result<Unit> = runCatching {
        val docs = db.collection("users").document(userId).collection("cart").get().await()
        docs.documents.forEach { it.reference.delete().await() }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "users/$userId/cart") }

    override fun observeCustomerOrders(customerId: String): Flow<List<Order>> =
        db.collection("orders")
            .whereEqualTo("customerId", customerId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "orders")

    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> =
        db.collection("orders")
            .whereEqualTo("sellerId", sellerId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "orders")

    override fun observePartnerAssignedOrders(partnerId: String): Flow<List<Order>> =
        db.collection("orders")
            .whereEqualTo("deliveryPartnerId", partnerId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "orders")

    override fun observeReadyUnassignedOrders(): Flow<List<Order>> =
        db.collection("orders")
            .whereEqualTo("status", OrderStatus.READY.name)
            .whereEqualTo("deliveryPartnerId", "")
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "orders")

    override fun observeAllOrdersForAdmin(): Flow<List<Order>> =
        db.collection("orders")
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "orders")

    override suspend fun placeOrder(
        customerId: String,
        customerName: String,
        store: Store,
        cartItems: List<CartItem>,
        deliveryAddress: String,
        distanceMeters: Long,
        coupon: Coupon?,
        paymentMethod: PaymentMethod,
        paymentReference: String,
        settings: PlatformSettings,
        gatewayAdapter: PaymentGatewayAdapter
    ): Result<String> = runCatching {
        require(cartItems.isNotEmpty()) { "Your cart is empty." }
        require(deliveryAddress.trim().length >= 3) { "Please select or enter a valid delivery address." }

        val subtotal = cartItems.sumOf { it.lineTotalInr }
        val discount = if (coupon != null && coupon.isActive && subtotal >= coupon.minOrderInr) {
            if (coupon.discountType == "PERCENT") {
                kotlin.math.round((subtotal * coupon.discountValue / 100.0) * 100.0) / 100.0
            } else {
                coupon.discountValue.coerceAtMost(subtotal)
            }
        } else {
            0.0
        }
        val quote = DeliveryFeeEngine.calculateQuote(distanceMeters, settings).getOrThrow()
        val productAfterDiscount = (subtotal - discount).coerceAtLeast(0.0)
        val total = kotlin.math.round((productAfterDiscount + quote.deliveryFeeInr) * 100.0) / 100.0

        val paymentResult = gatewayAdapter.verifyCheckoutPayment(
            method = paymentMethod,
            amountInr = total,
            manualOrTestReference = paymentReference,
            settings = settings
        ).getOrThrow()

        val split = SettlementSplitCalculator.computeSplit(
            orderTotalInr = total,
            productSubtotalAfterDiscountInr = productAfterDiscount,
            deliveryFeeInr = quote.deliveryFeeInr,
            settings = settings
        )

        val orderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val initialStatus = OrderStatus.PLACED.name
        val timeline = listOf("PLACED")

        val orderPayload = mapOf(
            "customerId" to customerId,
            "customerName" to customerName.take(80),
            "sellerId" to store.ownerId,
            "storeId" to store.id,
            "storeName" to store.name.take(100),
            "deliveryPartnerId" to "",
            "deliveryPartnerName" to "",
            "status" to initialStatus,
            "paymentMethod" to paymentResult.method.name,
            "paymentStatus" to paymentResult.status.name,
            "paymentReference" to paymentResult.reference.take(120),
            "subtotalInr" to subtotal,
            "discountInr" to discount,
            "deliveryFeeInr" to quote.deliveryFeeInr,
            "totalInr" to total,
            "distanceMeters" to distanceMeters,
            "deliveryAddress" to deliveryAddress.trim().take(250),
            "couponCode" to (coupon?.code ?: ""),
            "itemsSummary" to cartItems.take(50).map { "${it.quantity}x ${it.productName} (₹${it.lineTotalInr.toInt()})" },
            "timelineEvents" to timeline,
            "sellerEarningInr" to split.sellerPayableInr,
            "deliveryEarningInr" to split.deliveryPartnerEarningInr,
            "platformCommissionInr" to split.platformCommissionInr,
            "platformNetInr" to split.platformNetRemainingInr,
            "settlementStatus" to SettlementStatus.PENDING.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val paymentPayload = mapOf(
            "orderId" to orderId,
            "customerId" to customerId,
            "sellerId" to store.ownerId,
            "amountInr" to total,
            "method" to paymentResult.method.name,
            "gatewayMode" to paymentResult.gatewayMode,
            "status" to paymentResult.status.name,
            "providerReference" to paymentResult.reference.take(120),
            "idempotencyKey" to "pay_$orderId",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(db.collection("orders").document(orderId), orderPayload)
        batch.set(db.collection("payments").document(paymentId), paymentPayload)
        batch.commit().await()

        // If store has auto-accept enabled, seller portal or immediate update moves PLACED -> ACCEPTED
        if (store.autoAcceptOrders && store.ownerId == customerId) {
            db.collection("orders").document(orderId).update(
                mapOf(
                    "status" to OrderStatus.ACCEPTED.name,
                    "timelineEvents" to listOf("PLACED", "ACCEPTED (Auto)"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        sendUserNotification(
            userId = customerId,
            title = "Order Placed (#${orderId.takeLast(6)})",
            message = "Your order at ${store.name} for ₹${total.toInt()} has been placed.",
            orderId = orderId
        )
        clearCart(customerId)
        orderId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "orders") }

    override suspend fun advanceOrderBySeller(order: Order, nextStatus: OrderStatus): Result<Unit> = runCatching {
        val current = OrderStatus.valueOf(order.status)
        OrderLifecycleEngine.validateTransition(current, nextStatus).getOrThrow()
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(order.timelineEvents, nextStatus.name)
        db.collection("orders").document(order.id).update(
            mapOf(
                "status" to nextStatus.name,
                "timelineEvents" to updatedTimeline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        sendUserNotification(
            userId = order.customerId,
            title = "Order ${nextStatus.name.replace('_', ' ')}",
            message = "${order.storeName} updated your order #${order.id.takeLast(6)} to ${nextStatus.name}.",
            orderId = order.id
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override suspend fun cancelOrderByCustomer(order: Order): Result<Unit> = runCatching {
        val current = OrderStatus.valueOf(order.status)
        OrderLifecycleEngine.validateTransition(current, OrderStatus.CANCELLED).getOrThrow()
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(order.timelineEvents, OrderStatus.CANCELLED.name)
        db.collection("orders").document(order.id).update(
            mapOf(
                "status" to OrderStatus.CANCELLED.name,
                "timelineEvents" to updatedTimeline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override suspend fun claimOrderByPartner(order: Order, partner: DeliveryPartner): Result<Unit> = runCatching {
        OrderLifecycleEngine.canPartnerClaim(order, partner.partnerId, partner.isBusy).getOrThrow()
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(
            order.timelineEvents,
            "ASSIGNED (${partner.name})"
        )
        val assignmentId = "asgn_${order.id}_${partner.partnerId}".take(120)
        db.runTransaction { txn ->
            val orderRef = db.collection("orders").document(order.id)
            val snap = txn.get(orderRef)
            val currentStatus = snap.getString("status") ?: ""
            val currentPartner = snap.getString("deliveryPartnerId") ?: ""
            if (currentStatus != OrderStatus.READY.name || currentPartner.isNotBlank()) {
                throw IllegalStateException("Order was already claimed by another delivery partner.")
            }
            txn.update(
                orderRef,
                mapOf(
                    "deliveryPartnerId" to partner.partnerId,
                    "deliveryPartnerName" to partner.name.take(80),
                    "status" to OrderStatus.ASSIGNED.name,
                    "timelineEvents" to updatedTimeline,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            txn.update(
                db.collection("deliveryPartners").document(partner.partnerId),
                mapOf(
                    "isBusy" to true,
                    "activeOrderId" to order.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            txn.set(
                db.collection("deliveryAssignments").document(assignmentId),
                mapOf(
                    "orderId" to order.id,
                    "partnerId" to partner.partnerId,
                    "sellerId" to order.sellerId,
                    "customerId" to order.customerId,
                    "status" to "ACCEPTED",
                    "rejectionReason" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        sendUserNotification(
            userId = order.customerId,
            title = "Delivery Partner Assigned",
            message = "${partner.name} accepted your delivery for #${order.id.takeLast(6)}.",
            orderId = order.id
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override suspend fun releaseOrderByPartner(order: Order, partnerId: String, reason: String): Result<Unit> = runCatching {
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(
            order.timelineEvents,
            "REASSIGN_REQUESTED"
        )
        val assignmentId = "asgn_rej_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val batch = db.batch()
        batch.update(
            db.collection("orders").document(order.id),
            mapOf(
                "deliveryPartnerId" to "",
                "deliveryPartnerName" to "",
                "status" to OrderStatus.READY.name,
                "timelineEvents" to updatedTimeline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("deliveryPartners").document(partnerId),
            mapOf(
                "isBusy" to false,
                "activeOrderId" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            db.collection("deliveryAssignments").document(assignmentId),
            mapOf(
                "orderId" to order.id,
                "partnerId" to partnerId,
                "sellerId" to order.sellerId,
                "customerId" to order.customerId,
                "status" to "REJECTED",
                "rejectionReason" to reason.ifBlank { "Partner unavailable" }.take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override suspend fun advanceOrderByPartner(
        order: Order,
        nextStatus: OrderStatus,
        partner: DeliveryPartner
    ): Result<Unit> = runCatching {
        val current = OrderStatus.valueOf(order.status)
        OrderLifecycleEngine.validateTransition(current, nextStatus).getOrThrow()
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(order.timelineEvents, nextStatus.name)

        val batch = db.batch()
        batch.update(
            db.collection("orders").document(order.id),
            mapOf(
                "status" to nextStatus.name,
                "timelineEvents" to updatedTimeline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (nextStatus == OrderStatus.DELIVERED) {
            batch.update(
                db.collection("deliveryPartners").document(partner.partnerId),
                mapOf(
                    "isBusy" to false,
                    "activeOrderId" to "",
                    "completedDeliveries" to (partner.completedDeliveries + 1L),
                    "totalEarningsInr" to (partner.totalEarningsInr + order.deliveryEarningInr),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // Create pending seller and delivery settlements idempotently
            val sellerSettleId = "stl_seller_${order.id}".take(120)
            val partnerSettleId = "stl_partner_${order.id}".take(120)
            batch.set(
                db.collection("settlements").document(sellerSettleId),
                mapOf(
                    "recipientId" to order.sellerId,
                    "recipientRole" to "SELLER",
                    "orderId" to order.id,
                    "grossAmountInr" to (order.subtotalInr - order.discountInr).coerceAtLeast(0.0),
                    "commissionInr" to order.platformCommissionInr,
                    "netPayableInr" to order.sellerEarningInr,
                    "payoutMode" to SettlementMode.MANUAL.name,
                    "status" to SettlementStatus.PENDING.name,
                    "paymentReference" to "",
                    "idempotencyKey" to sellerSettleId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("settlements").document(partnerSettleId),
                mapOf(
                    "recipientId" to partner.partnerId,
                    "recipientRole" to "DELIVERY",
                    "orderId" to order.id,
                    "grossAmountInr" to order.deliveryFeeInr,
                    "commissionInr" to 0.0,
                    "netPayableInr" to order.deliveryEarningInr,
                    "payoutMode" to SettlementMode.MANUAL.name,
                    "status" to SettlementStatus.PENDING.name,
                    "paymentReference" to "",
                    "idempotencyKey" to partnerSettleId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.commit().await()
        sendUserNotification(
            userId = order.customerId,
            title = "Delivery Update: ${nextStatus.name.replace('_', ' ')}",
            message = "Your order #${order.id.takeLast(6)} is now ${nextStatus.name.replace('_', ' ')}.",
            orderId = order.id
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override fun observeDeliveryPartner(partnerId: String): Flow<DeliveryPartner?> =
        db.collection("deliveryPartners").document(partnerId)
            .snapshots()
            .map { it.toObject(DeliveryPartner::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.GET, "deliveryPartners/$partnerId")

    override fun observeAllDeliveryPartnersForAdmin(): Flow<List<DeliveryPartner>> =
        db.collection("deliveryPartners")
            .snapshots()
            .map { it.toObjects(DeliveryPartner::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "deliveryPartners")

    override suspend fun upsertDeliveryPartnerProfile(
        partnerId: String,
        name: String,
        phone: String,
        vehicleType: String,
        isAdmin: Boolean
    ): Result<Unit> = runCatching {
        val docRef = db.collection("deliveryPartners").document(partnerId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "partnerId" to partnerId,
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(25),
                    "vehicleType" to vehicleType.trim().ifBlank { "Electric Scooter" }.take(40),
                    "approvalStatus" to if (isAdmin) ApprovalStatus.APPROVED.name else ApprovalStatus.PENDING.name,
                    "isOnline" to false,
                    "isBusy" to false,
                    "activeOrderId" to "",
                    "currentLat" to 12.9716,
                    "currentLng" to 77.5946,
                    "completedDeliveries" to 0L,
                    "totalEarningsInr" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(25),
                    "vehicleType" to vehicleType.trim().ifBlank { "Electric Scooter" }.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "deliveryPartners/$partnerId") }

    override suspend fun updatePartnerStatusAndLocation(
        partner: DeliveryPartner,
        isOnline: Boolean,
        lat: Double,
        lng: Double
    ): Result<Unit> = runCatching {
        db.collection("deliveryPartners").document(partner.partnerId).update(
            mapOf(
                "isOnline" to isOnline,
                "currentLat" to lat.coerceIn(-90.0, 90.0),
                "currentLng" to lng.coerceIn(-180.0, 180.0),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "deliveryPartners/${partner.partnerId}") }

    override suspend fun setPartnerApprovalStatus(
        partnerId: String,
        status: ApprovalStatus,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        db.collection("deliveryPartners").document(partnerId).update(
            mapOf(
                "approvalStatus" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(actorId, actorEmail, "PARTNER_${status.name}", "DELIVERY_PARTNER", partnerId, "Partner status set to ${status.name}")
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "deliveryPartners/$partnerId") }

    override fun observeActiveCoupons(): Flow<List<Coupon>> =
        db.collection("coupons")
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { it.toObjects(Coupon::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "coupons")

    override fun observeAllCouponsForAdmin(): Flow<List<Coupon>> =
        db.collection("coupons")
            .snapshots()
            .map { it.toObjects(Coupon::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "coupons")

    override suspend fun createCoupon(
        code: String,
        title: String,
        discountType: String,
        discountValue: Double,
        minOrderInr: Double,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        val cleanCode = code.trim().uppercase().replace(" ", "").take(32)
        require(cleanCode.length >= 2) { "Coupon code must be at least 2 characters." }
        val couponId = "cpn_${cleanCode.lowercase()}"
        db.collection("coupons").document(couponId).set(
            mapOf(
                "code" to cleanCode,
                "title" to title.trim().take(100),
                "discountType" to if (discountType == "PERCENT") "PERCENT" else "FLAT",
                "discountValue" to discountValue.coerceAtLeast(1.0),
                "minOrderInr" to minOrderInr.coerceAtLeast(0.0),
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(actorId, actorEmail, "COUPON_CREATED", "COUPON", couponId, "Created coupon $cleanCode")
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "coupons") }

    override fun observeReviews(): Flow<List<Review>> =
        db.collection("reviews")
            .whereGreaterThanOrEqualTo("rating", 1L)
            .snapshots()
            .map { it.toObjects(Review::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "reviews")

    override suspend fun submitReview(
        order: Order,
        customerName: String,
        rating: Long,
        comment: String
    ): Result<Unit> = runCatching {
        val reviewId = "rev_${order.id}"
        db.collection("reviews").document(reviewId).set(
            mapOf(
                "orderId" to order.id,
                "storeId" to order.storeId,
                "customerId" to order.customerId,
                "customerName" to customerName.take(80),
                "rating" to rating.coerceIn(1L, 5L),
                "comment" to comment.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "reviews") }

    override fun observeCustomerComplaints(customerId: String): Flow<List<Complaint>> =
        db.collection("complaints")
            .whereEqualTo("customerId", customerId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Complaint::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "complaints")

    override fun observeAllComplaintsForAdmin(): Flow<List<Complaint>> =
        db.collection("complaints")
            .snapshots()
            .map { snap ->
                snap.toObjects(Complaint::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "complaints")

    override suspend fun submitComplaint(
        customerId: String,
        customerName: String,
        orderId: String,
        subject: String,
        description: String
    ): Result<String> = runCatching {
        val compId = "cmp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("complaints").document(compId).set(
            mapOf(
                "customerId" to customerId,
                "customerName" to customerName.take(80),
                "orderId" to orderId.take(128),
                "subject" to subject.trim().take(120),
                "description" to description.trim().take(1000),
                "status" to ComplaintStatus.OPEN.name,
                "adminResolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        compId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "complaints") }

    override suspend fun resolveComplaintByAdmin(
        complaint: Complaint,
        status: ComplaintStatus,
        resolutionNote: String,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        db.collection("complaints").document(complaint.id).update(
            mapOf(
                "status" to status.name,
                "adminResolutionNote" to resolutionNote.trim().take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(actorId, actorEmail, "COMPLAINT_${status.name}", "COMPLAINT", complaint.id, resolutionNote.take(200))
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "complaints/${complaint.id}") }

    override fun observeNotifications(userId: String): Flow<List<UserNotification>> =
        db.collection("users").document(userId).collection("notifications")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserNotification::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "users/$userId/notifications")

    override suspend fun markNotificationRead(userId: String, notificationId: String): Result<Unit> = runCatching {
        db.collection("users").document(userId)
            .collection("notifications").document(notificationId)
            .update("isRead", true)
            .await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "users/$userId/notifications/$notificationId") }

    override fun observePlatformSettings(): Flow<PlatformSettings> =
        db.collection("platformSettings").document("global")
            .snapshots()
            .map { snap ->
                snap.toObject(PlatformSettings::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: PlatformSettings(id = "global")
            }
            .logAndRethrow(OperationType.GET, "platformSettings/global")

    override suspend fun updatePlatformSettings(
        settings: PlatformSettings,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        db.collection("platformSettings").document("global").set(
            mapOf(
                "onlinePaymentsEnabled" to settings.onlinePaymentsEnabled,
                "codEnabled" to settings.codEnabled,
                "settlementMode" to settings.settlementMode,
                "platformCommissionPercent" to settings.platformCommissionPercent.coerceIn(0.0, 50.0),
                "sellerPayoutPercent" to settings.sellerPayoutPercent.coerceIn(50.0, 100.0),
                "deliveryBaseFeeInr" to settings.deliveryBaseFeeInr.coerceAtLeast(0.0),
                "deliveryPer500mInr" to settings.deliveryPer500mInr.coerceAtLeast(0.0),
                "minPayoutThresholdInr" to settings.minPayoutThresholdInr.coerceAtLeast(0.0),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(
            actorId, actorEmail, "SETTINGS_UPDATED", "PLATFORM_SETTINGS", "global",
            "Mode=${settings.settlementMode}, Comm=${settings.platformCommissionPercent}%"
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "platformSettings/global") }

    override fun observeMySettlements(recipientId: String): Flow<List<SettlementRecord>> =
        db.collection("settlements")
            .whereEqualTo("recipientId", recipientId)
            .snapshots()
            .map { snap ->
                snap.toObjects(SettlementRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "settlements")

    override fun observeAllSettlementsForAdmin(): Flow<List<SettlementRecord>> =
        db.collection("settlements")
            .snapshots()
            .map { snap ->
                snap.toObjects(SettlementRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "settlements")

    override fun observeAllPaymentsForAdmin(): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "payments")

    override fun observeMyPayoutAccounts(userId: String): Flow<List<PayoutAccount>> =
        db.collection("payoutAccounts")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { it.toObjects(PayoutAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "payoutAccounts")

    override fun observeAllPayoutAccountsForAdmin(): Flow<List<PayoutAccount>> =
        db.collection("payoutAccounts")
            .snapshots()
            .map { it.toObjects(PayoutAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
            .logAndRethrow(OperationType.LIST, "payoutAccounts")

    override suspend fun upsertPayoutAccount(
        userId: String,
        role: String,
        holderName: String,
        maskedDest: String,
        ifsc: String,
        isAdmin: Boolean
    ): Result<Unit> = runCatching {
        val accountId = "pa_${role.lowercase()}_$userId".take(120)
        val docRef = db.collection("payoutAccounts").document(accountId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "userId" to userId,
                    "role" to role,
                    "accountHolderName" to holderName.trim().take(100),
                    "maskedDestination" to maskedDest.trim().take(100),
                    "ifscCode" to ifsc.trim().uppercase().take(20),
                    "kycStatus" to if (isAdmin) "VERIFIED" else "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "accountHolderName" to holderName.trim().take(100),
                    "maskedDestination" to maskedDest.trim().take(100),
                    "ifscCode" to ifsc.trim().uppercase().take(20),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "payoutAccounts") }

    override suspend fun verifyPayoutAccountKyc(
        accountId: String,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        db.collection("payoutAccounts").document(accountId).update(
            mapOf(
                "kycStatus" to "VERIFIED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(actorId, actorEmail, "KYC_VERIFIED", "PAYOUT_ACCOUNT", accountId, "Verified payout KYC")
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "payoutAccounts/$accountId") }

    override suspend fun settleManualPayout(
        settlement: SettlementRecord,
        paymentReference: String,
        existingSettlements: List<SettlementRecord>,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        val ref = SettlementSplitCalculator.validateManualSettlement(
            settlement = settlement,
            paymentReference = paymentReference,
            existingSettlements = existingSettlements
        ).getOrThrow()

        db.collection("settlements").document(settlement.id).update(
            mapOf(
                "payoutMode" to SettlementMode.MANUAL.name,
                "status" to SettlementStatus.PAID.name,
                "paymentReference" to ref,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(
            actorId, actorEmail, "MANUAL_SETTLEMENT_PAID", "SETTLEMENT", settlement.id,
            "Settled ₹${settlement.netPayableInr} with ref $ref"
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "settlements/${settlement.id}") }

    override suspend fun triggerAutomaticPayout(
        settlement: SettlementRecord,
        payoutAccount: PayoutAccount?,
        settings: PlatformSettings,
        gatewayAdapter: PaymentGatewayAdapter,
        existingSettlements: List<SettlementRecord>,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        SettlementSplitCalculator.checkAutomaticPayoutEligibility(
            settlement = settlement,
            payoutAccount = payoutAccount,
            settings = settings,
            gatewayAdapter = gatewayAdapter,
            existingSettlements = existingSettlements
        ).getOrThrow()

        val autoRef = "rzp_route_${settlement.idempotencyKey.take(24)}"
        db.collection("settlements").document(settlement.id).update(
            mapOf(
                "payoutMode" to SettlementMode.AUTOMATIC.name,
                "status" to SettlementStatus.PAID.name,
                "paymentReference" to autoRef,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog(
            actorId, actorEmail, "AUTOMATIC_PAYOUT_INITIATED", "SETTLEMENT", settlement.id,
            "Auto payout ₹${settlement.netPayableInr} ref $autoRef"
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "settlements/${settlement.id}") }

    override suspend fun requestOrProcessRefund(
        order: Order,
        isAdminAction: Boolean,
        actorId: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        val current = OrderStatus.valueOf(order.status)
        val target = if (isAdminAction && current == OrderStatus.REFUND_PENDING) {
            OrderStatus.REFUNDED
        } else {
            OrderStatus.REFUND_PENDING
        }
        OrderLifecycleEngine.validateTransition(current, target).getOrThrow()
        val updatedTimeline = OrderLifecycleEngine.appendTimelineEvent(order.timelineEvents, target.name)
        db.collection("orders").document(order.id).update(
            mapOf(
                "status" to target.name,
                "paymentStatus" to if (target == OrderStatus.REFUNDED) PaymentStatus.REFUNDED.name else PaymentStatus.REFUND_PENDING.name,
                "timelineEvents" to updatedTimeline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        if (isAdminAction) {
            writeAuditLog(actorId, actorEmail, "ORDER_${target.name}", "ORDER", order.id, "Refund state -> ${target.name}")
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "orders/${order.id}") }

    override fun observeAuditLogsForAdmin(): Flow<List<AuditLog>> =
        db.collection("auditLogs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logAndRethrow(OperationType.LIST, "auditLogs")

    override suspend fun seedDemoMarketplaceIfAdmin(
        adminId: String,
        adminName: String,
        adminEmail: String
    ): Result<Unit> = runCatching {
        val storesSnap = db.collection("stores").limit(1).get().await()
        if (!storesSnap.isEmpty) return@runCatching

        val settingsRef = db.collection("platformSettings").document("global")
        if (!settingsRef.get().await().exists()) {
            settingsRef.set(
                mapOf(
                    "onlinePaymentsEnabled" to true,
                    "codEnabled" to true,
                    "settlementMode" to SettlementMode.MANUAL.name,
                    "platformCommissionPercent" to 10.0,
                    "sellerPayoutPercent" to 85.0,
                    "deliveryBaseFeeInr" to 5.0,
                    "deliveryPer500mInr" to 5.0,
                    "minPayoutThresholdInr" to 100.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val demoStores = listOf(
            Triple("store_freshmart", "FreshMart Hyperlocal", StoreCategory.GROCERY) to Pair("12th Main, Indiranagar", 12.9718 to 77.6412),
            Triple("store_spicebistro", "Spice & Ember Kitchen", StoreCategory.FOOD) to Pair("100ft Road, Koramangala", 12.9352 to 77.6245),
            Triple("store_medicare", "CarePlus 24x7 Pharmacy", StoreCategory.MEDICINES) to Pair("CMH Road, Ulsoor", 12.9784 to 77.6408),
            Triple("store_craftlocal", "Neighborhood Craft & Stationery", StoreCategory.LOCAL_SHOP) to Pair("Church Street, Central", 12.9756 to 77.6042)
        )

        val batch = db.batch()
        demoStores.forEach { (meta, loc) ->
            val (id, name, cat) = meta
            val (addr, coords) = loc
            batch.set(
                db.collection("stores").document(id),
                mapOf(
                    "ownerId" to adminId,
                    "name" to name,
                    "category" to cat.name,
                    "description" to "Verified hyperlocal ${cat.label.lowercase()} partner delivering under 25 mins.",
                    "address" to addr,
                    "latitude" to coords.first,
                    "longitude" to coords.second,
                    "openingHours" to "07:00 AM - 11:30 PM",
                    "isOpen" to true,
                    "autoAcceptOrders" to false,
                    "approvalStatus" to ApprovalStatus.APPROVED.name,
                    "rating" to 4.8,
                    "reviewCount" to 124L,
                    "bannerImageKey" to cat.name.lowercase(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val demoProducts = listOf(
            listOf("prod_avocado", "store_freshmart", "Organic Hass Avocados", "Fruits", "240.0", "10", "35", "2 pcs"),
            listOf("prod_milk", "store_freshmart", "Farm Fresh A2 Cow Milk", "Dairy", "78.0", "0", "60", "1 L"),
            listOf("prod_sourdough", "store_freshmart", "Artisan Multigrain Sourdough", "Bakery", "165.0", "5", "18", "400 g"),
            listOf("prod_biryani", "store_spicebistro", "Hyderabadi Paneer Dum Bowl", "Mains", "289.0", "15", "40", "1 bowl"),
            listOf("prod_coldbrew", "store_spicebistro", "Single-Origin Cold Brew", "Beverages", "150.0", "0", "50", "250 ml"),
            listOf("prod_firstaid", "store_medicare", "Compact Home First-Aid Kit", "Care", "399.0", "10", "20", "1 kit"),
            listOf("prod_electrolytes", "store_medicare", "Hydration Electrolyte Pack", "Wellness", "95.0", "0", "80", "10 sachets"),
            listOf("prod_notebook", "store_craftlocal", "Dotted Grid Linen Journal", "Stationery", "320.0", "12", "25", "160 pages")
        )

        demoProducts.forEach { p ->
            batch.set(
                db.collection("products").document(p[0]),
                mapOf(
                    "storeId" to p[1],
                    "ownerId" to adminId,
                    "name" to p[2],
                    "category" to p[3],
                    "description" to "Freshly stocked ${p[2]} from our verified neighborhood store.",
                    "priceInr" to p[4].toDouble(),
                    "discountPercent" to p[5].toLong(),
                    "stockCount" to p[6].toLong(),
                    "isAvailable" to true,
                    "imageUrl" to "",
                    "unitLabel" to p[7],
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.set(
            db.collection("coupons").document("cpn_welcome50"),
            mapOf(
                "code" to "WELCOME50",
                "title" to "Flat ₹50 off on orders ₹250+",
                "discountType" to "FLAT",
                "discountValue" to 50.0,
                "minOrderInr" to 250.0,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            db.collection("coupons").document("cpn_fresh15"),
            mapOf(
                "code" to "FRESH15",
                "title" to "15% off on hyperlocal essentials",
                "discountType" to "PERCENT",
                "discountValue" to 15.0,
                "minOrderInr" to 150.0,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        writeAuditLog(adminId, adminEmail, "SEED_MARKETPLACE", "SYSTEM", "demo_seed", "Initialized demo stores, products, and coupons by $adminName")
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "seed") }

    private suspend fun sendUserNotification(
        userId: String,
        title: String,
        message: String,
        orderId: String
    ) {
        runCatching {
            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            db.collection("users").document(userId)
                .collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "title" to title.take(120),
                        "message" to message.take(400),
                        "orderId" to orderId.take(128),
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
    }

    private suspend fun writeAuditLog(
        actorId: String,
        actorEmail: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("auditLogs").document(logId).set(
            mapOf(
                "actorId" to actorId.take(128),
                "actorEmail" to actorEmail.take(120),
                "action" to action.take(100),
                "targetType" to targetType.take(60),
                "targetId" to targetId.take(128),
                "details" to details.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
