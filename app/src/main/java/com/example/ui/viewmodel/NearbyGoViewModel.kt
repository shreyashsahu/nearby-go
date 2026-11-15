package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.UiState
import com.example.core.unwrapFirestoreException
import com.example.data.ai.AiImageAspect
import com.example.data.ai.AiImageResolution
import com.example.data.ai.ChatTurn
import com.example.data.ai.GeminiChatTier
import com.example.data.ai.NearbyGoAiRepository
import com.example.data.maps.MapsService
import com.example.data.repository.FirestoreNearbyGoRepository
import com.example.data.repository.NearbyGoRepository
import com.example.domain.engine.DeliveryFeeEngine
import com.example.domain.engine.DeliveryFeeQuote
import com.example.domain.engine.NearbyGoPaymentGateway
import com.example.domain.engine.PaymentGatewayAdapter
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
import com.example.domain.model.PayoutAccount
import com.example.domain.model.PlatformSettings
import com.example.domain.model.PortalRole
import com.example.domain.model.Product
import com.example.domain.model.Review
import com.example.domain.model.SettlementRecord
import com.example.domain.model.Store
import com.example.domain.model.StoreCategory
import com.example.domain.model.UserNotification
import com.example.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(
        val bitmap: Bitmap,
        val savedPath: String,
        val resolutionLabel: String
    ) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

data class CustomerLocationState(
    val addressText: String = "Indiranagar 100ft Road, Bengaluru",
    val latitude: Double = 12.9716,
    val longitude: Double = 77.6412,
    val isFromDeviceGps: Boolean = false,
    val manualDistanceMeters: Long = 1200L,
    val isMapsConfigured: Boolean = false,
    val statusNote: String = "Manual address active (set MAPS_API_KEY in Secrets for live Google Routes)",
    val isLoadingLocation: Boolean = false
)

class NearbyGoViewModel(
    private val repository: NearbyGoRepository,
    private val mapsService: MapsService,
    private val aiRepository: NearbyGoAiRepository,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    isEmailVerified: Boolean
) : ViewModel() {

    val isSuperAdmin: Boolean =
        isEmailVerified && currentUserEmail.equals("shreyashsahu1500@gmail.com", ignoreCase = true)

    val paymentGateway: PaymentGatewayAdapter = NearbyGoPaymentGateway()

    private val _activePortal = MutableStateFlow(PortalRole.CUSTOMER)
    val activePortal: StateFlow<PortalRole> = _activePortal.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _locationState = MutableStateFlow(
        CustomerLocationState(
            isMapsConfigured = mapsService.isMapsApiConfigured(),
            statusNote = if (mapsService.isMapsApiConfigured()) {
                "Google Maps Platform ready"
            } else {
                "Manual address mode (Set MAPS_API_KEY in Secrets for live Geocoding/Routes)"
            }
        )
    )
    val locationState: StateFlow<CustomerLocationState> = _locationState.asStateFlow()

    // AI Chatbot State
    private val _chatTier = MutableStateFlow(GeminiChatTier.GENERAL)
    val chatTier: StateFlow<GeminiChatTier> = _chatTier.asStateFlow()

    private val _chatTurns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val chatTurns: StateFlow<List<ChatTurn>> = _chatTurns.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private var chatJob: Job? = null

    // AI Image Studio State
    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _selectedImageResolution = MutableStateFlow(AiImageResolution.SIZE_1K)
    val selectedImageResolution: StateFlow<AiImageResolution> = _selectedImageResolution.asStateFlow()

    private val _selectedImageAspect = MutableStateFlow(AiImageAspect.SQUARE_1_1)
    val selectedImageAspect: StateFlow<AiImageAspect> = _selectedImageAspect.asStateFlow()

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = error.unwrapFirestoreException()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Stream error [$label]", root)
                emit(UiState.Error(root.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiState("profile")

    val platformSettings: StateFlow<PlatformSettings> =
        repository.observePlatformSettings()
            .catch { emit(PlatformSettings()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlatformSettings())

    val approvedStores: StateFlow<UiState<List<Store>>> =
        repository.observeApprovedStores().toUiState("stores")

    val myStores: StateFlow<UiState<List<Store>>> =
        repository.observeMyStores(currentUserId).toUiState("my_stores")

    val availableProducts: StateFlow<UiState<List<Product>>> =
        repository.observeAvailableProducts().toUiState("products")

    val myProducts: StateFlow<UiState<List<Product>>> =
        repository.observeMyProducts(currentUserId).toUiState("my_products")

    val cartItems: StateFlow<UiState<List<CartItem>>> =
        repository.observeCart(currentUserId).toUiState("cart")

    val customerOrders: StateFlow<UiState<List<Order>>> =
        repository.observeCustomerOrders(currentUserId).toUiState("customer_orders")

    val sellerOrders: StateFlow<UiState<List<Order>>> =
        repository.observeSellerOrders(currentUserId).toUiState("seller_orders")

    val partnerAssignedOrders: StateFlow<UiState<List<Order>>> =
        repository.observePartnerAssignedOrders(currentUserId).toUiState("partner_orders")

    val readyUnassignedOrders: StateFlow<UiState<List<Order>>> =
        repository.observeReadyUnassignedOrders().toUiState("ready_orders")

    val deliveryPartnerProfile: StateFlow<UiState<DeliveryPartner?>> =
        repository.observeDeliveryPartner(currentUserId).toUiState("delivery_partner")

    val activeCoupons: StateFlow<UiState<List<Coupon>>> =
        repository.observeActiveCoupons().toUiState("coupons")

    val reviews: StateFlow<UiState<List<Review>>> =
        repository.observeReviews().toUiState("reviews")

    val customerComplaints: StateFlow<UiState<List<Complaint>>> =
        repository.observeCustomerComplaints(currentUserId).toUiState("complaints")

    val notifications: StateFlow<UiState<List<UserNotification>>> =
        repository.observeNotifications(currentUserId).toUiState("notifications")

    val mySettlements: StateFlow<UiState<List<SettlementRecord>>> =
        repository.observeMySettlements(currentUserId).toUiState("my_settlements")

    val myPayoutAccounts: StateFlow<UiState<List<PayoutAccount>>> =
        repository.observeMyPayoutAccounts(currentUserId).toUiState("my_payout_accounts")

    // Super Admin exclusive streams (only subscribed when isSuperAdmin == true)
    val adminAllStores: StateFlow<UiState<List<Store>>> =
        if (isSuperAdmin) repository.observeAllStoresForAdmin().toUiState("admin_stores")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllOrders: StateFlow<UiState<List<Order>>> =
        if (isSuperAdmin) repository.observeAllOrdersForAdmin().toUiState("admin_orders")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllPartners: StateFlow<UiState<List<DeliveryPartner>>> =
        if (isSuperAdmin) repository.observeAllDeliveryPartnersForAdmin().toUiState("admin_partners")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllComplaints: StateFlow<UiState<List<Complaint>>> =
        if (isSuperAdmin) repository.observeAllComplaintsForAdmin().toUiState("admin_complaints")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllSettlements: StateFlow<UiState<List<SettlementRecord>>> =
        if (isSuperAdmin) repository.observeAllSettlementsForAdmin().toUiState("admin_settlements")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllPayments: StateFlow<UiState<List<PaymentRecord>>> =
        if (isSuperAdmin) repository.observeAllPaymentsForAdmin().toUiState("admin_payments")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllPayoutAccounts: StateFlow<UiState<List<PayoutAccount>>> =
        if (isSuperAdmin) repository.observeAllPayoutAccountsForAdmin().toUiState("admin_payout_accounts")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAllCoupons: StateFlow<UiState<List<Coupon>>> =
        if (isSuperAdmin) repository.observeAllCouponsForAdmin().toUiState("admin_coupons")
        else MutableStateFlow(UiState.Success(emptyList()))

    val adminAuditLogs: StateFlow<UiState<List<AuditLog>>> =
        if (isSuperAdmin) repository.observeAuditLogsForAdmin().toUiState("admin_audit_logs")
        else MutableStateFlow(UiState.Success(emptyList()))

    val currentDeliveryFeeQuote: StateFlow<Result<DeliveryFeeQuote>> =
        combine(_locationState, platformSettings) { loc, settings ->
            DeliveryFeeEngine.calculateQuote(
                distanceMeters = loc.manualDistanceMeters,
                settings = settings,
                sourceLabel = if (loc.isMapsConfigured && loc.isFromDeviceGps) "Google Maps / GPS" else "Configured Distance"
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            DeliveryFeeEngine.calculateQuote(1200L, PlatformSettings())
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, currentUserName, currentUserEmail)
            if (isSuperAdmin) {
                repository.seedDemoMarketplaceIfAdmin(currentUserId, currentUserName, currentUserEmail)
            }
        }
    }

    fun switchPortal(role: PortalRole) {
        _activePortal.value = role
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    // --- Location & Maps Actions ---
    fun fetchDeviceGpsLocation() {
        viewModelScope.launch {
            _locationState.value = _locationState.value.copy(isLoadingLocation = true)
            val locResult = mapsService.fetchCurrentDeviceLocation()
            locResult.onSuccess { loc ->
                val resolved = mapsService.reverseGeocode(loc.latitude, loc.longitude)
                _locationState.value = _locationState.value.copy(
                    addressText = resolved.formattedAddress,
                    latitude = resolved.latitude,
                    longitude = resolved.longitude,
                    isFromDeviceGps = true,
                    statusNote = if (resolved.isFromMapsApi) "Live GPS + Google Geocoding" else "Live Device GPS Fix",
                    isLoadingLocation = false
                )
                showBanner("Updated location from GPS.")
            }.onFailure { err ->
                _locationState.value = _locationState.value.copy(
                    isLoadingLocation = false,
                    statusNote = err.message ?: "GPS unavailable; using manual address."
                )
                showBanner(err.message ?: "GPS location unavailable.")
            }
        }
    }

    fun updateManualAddress(address: String, distanceMeters: Long) {
        val clean = address.trim()
        if (clean.length < 3) {
            showBanner("Please enter a valid address (min 3 characters).")
            return
        }
        viewModelScope.launch {
            if (mapsService.isMapsApiConfigured()) {
                _locationState.value = _locationState.value.copy(isLoadingLocation = true)
                mapsService.geocodeManualAddress(clean).onSuccess { resolved ->
                    _locationState.value = _locationState.value.copy(
                        addressText = resolved.formattedAddress,
                        latitude = resolved.latitude,
                        longitude = resolved.longitude,
                        isFromDeviceGps = false,
                        manualDistanceMeters = distanceMeters.coerceIn(100L, 30000L),
                        statusNote = "Resolved via Google Geocoding API",
                        isLoadingLocation = false
                    )
                    showBanner("Address verified via Google Maps.")
                    return@launch
                }.onFailure {
                    _locationState.value = _locationState.value.copy(isLoadingLocation = false)
                }
            }
            _locationState.value = _locationState.value.copy(
                addressText = clean,
                isFromDeviceGps = false,
                manualDistanceMeters = distanceMeters.coerceIn(100L, 30000L),
                statusNote = "Manual address & distance (${distanceMeters}m)"
            )
            showBanner("Delivery address updated.")
        }
    }

    suspend fun computeStoreDistanceMeters(store: Store): Long {
        val loc = _locationState.value
        val route = mapsService.computeRouteDistance(
            originLat = store.latitude,
            originLng = store.longitude,
            destLat = loc.latitude,
            destLng = loc.longitude
        )
        return if (loc.isFromDeviceGps || mapsService.isMapsApiConfigured()) {
            route.distanceMeters
        } else {
            loc.manualDistanceMeters
        }
    }

    // --- Customer Actions ---
    fun addToCart(product: Product, storeName: String) {
        viewModelScope.launch {
            repository.addToCart(currentUserId, product, storeName)
                .onSuccess { showBanner("Added ${product.name} to cart") }
                .onFailure { showBanner(it.message ?: "Could not add item") }
        }
    }

    fun updateCartQty(item: CartItem, newQty: Long) {
        viewModelScope.launch {
            repository.updateCartQuantity(currentUserId, item.id, newQty)
                .onFailure { showBanner(it.message ?: "Could not update cart") }
        }
    }

    fun placeOrder(
        store: Store,
        items: List<CartItem>,
        coupon: Coupon?,
        paymentMethod: PaymentMethod,
        paymentReference: String
    ) {
        viewModelScope.launch {
            val dist = computeStoreDistanceMeters(store)
            repository.placeOrder(
                customerId = currentUserId,
                customerName = currentUserName.ifBlank { "Customer" },
                store = store,
                cartItems = items,
                deliveryAddress = _locationState.value.addressText,
                distanceMeters = dist,
                coupon = coupon,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference,
                settings = platformSettings.value,
                gatewayAdapter = paymentGateway
            ).onSuccess { orderId ->
                showBanner("Order #${orderId.takeLast(6)} placed successfully!")
            }.onFailure { err ->
                showBanner(err.message ?: "Order failed")
            }
        }
    }

    fun reorderItems(order: Order, availableProducts: List<Product>) {
        viewModelScope.launch {
            val storeProducts = availableProducts.filter { it.storeId == order.storeId && it.stockCount > 0 }
            if (storeProducts.isEmpty()) {
                showBanner("No in-stock items from ${order.storeName} are currently available.")
                return@launch
            }
            var added = 0
            order.itemsSummary.forEach { summary ->
                val prod = storeProducts.firstOrNull { summary.contains(it.name, ignoreCase = true) }
                if (prod != null) {
                    repository.addToCart(currentUserId, prod, order.storeName)
                    added++
                }
            }
            if (added > 0) showBanner("Added $added item(s) from ${order.storeName} to cart.")
            else showBanner("Could not match past items to live inventory.")
        }
    }

    fun cancelOrder(order: Order) {
        viewModelScope.launch {
            repository.cancelOrderByCustomer(order)
                .onSuccess { showBanner("Order #${order.id.takeLast(6)} cancelled.") }
                .onFailure { showBanner(it.message ?: "Cannot cancel order") }
        }
    }

    fun submitReview(order: Order, rating: Long, comment: String) {
        viewModelScope.launch {
            repository.submitReview(order, currentUserName.ifBlank { "Customer" }, rating, comment)
                .onSuccess { showBanner("Thank you for your ${rating}★ review!") }
                .onFailure { showBanner(it.message ?: "Review failed") }
        }
    }

    fun submitComplaint(orderId: String, subject: String, description: String) {
        viewModelScope.launch {
            repository.submitComplaint(currentUserId, currentUserName, orderId, subject, description)
                .onSuccess { showBanner("Complaint filed privately with Super Admin support.") }
                .onFailure { showBanner(it.message ?: "Complaint failed") }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(currentUserId, id)
        }
    }

    fun updateCustomerProfile(name: String, phone: String, addresses: List<String>) {
        viewModelScope.launch {
            repository.updateUserProfile(currentUserId, name, phone, addresses, _activePortal.value)
                .onSuccess { showBanner("Profile saved.") }
                .onFailure { showBanner(it.message ?: "Save failed") }
        }
    }

    // --- Seller Actions ---
    fun createStore(
        name: String,
        category: StoreCategory,
        description: String,
        address: String,
        openingHours: String,
        autoAccept: Boolean
    ) {
        viewModelScope.launch {
            repository.createStore(
                ownerId = currentUserId,
                isAdmin = isSuperAdmin,
                name = name,
                category = category,
                description = description,
                address = address,
                latitude = _locationState.value.latitude,
                longitude = _locationState.value.longitude,
                openingHours = openingHours,
                autoAcceptOrders = autoAccept
            ).onSuccess {
                showBanner(
                    if (isSuperAdmin) "Store created & auto-approved."
                    else "Store submitted for Super Admin verification."
                )
            }.onFailure { showBanner(it.message ?: "Store creation failed") }
        }
    }

    fun toggleStoreSettings(store: Store, isOpen: Boolean, autoAccept: Boolean) {
        viewModelScope.launch {
            repository.updateStoreOperationalSettings(store, isOpen, autoAccept)
                .onFailure { showBanner(it.message ?: "Update failed") }
        }
    }

    fun upsertProduct(
        productId: String?,
        storeId: String,
        name: String,
        category: String,
        description: String,
        priceInr: Double,
        discountPercent: Long,
        stockCount: Long,
        imageUrl: String,
        unitLabel: String
    ) {
        viewModelScope.launch {
            repository.upsertProduct(
                productId = productId,
                storeId = storeId,
                ownerId = currentUserId,
                name = name,
                category = category,
                description = description,
                priceInr = priceInr,
                discountPercent = discountPercent,
                stockCount = stockCount,
                isAvailable = stockCount > 0L,
                imageUrl = imageUrl,
                unitLabel = unitLabel
            ).onSuccess {
                showBanner("Product '$name' saved.")
            }.onFailure { showBanner(it.message ?: "Failed to save product") }
        }
    }

    fun advanceOrderBySeller(order: Order, nextStatus: OrderStatus) {
        viewModelScope.launch {
            repository.advanceOrderBySeller(order, nextStatus)
                .onSuccess { showBanner("Order #${order.id.takeLast(6)} -> ${nextStatus.name}") }
                .onFailure { showBanner(it.message ?: "Transition denied") }
        }
    }

    fun savePayoutAccount(role: String, holderName: String, maskedDest: String, ifsc: String) {
        viewModelScope.launch {
            repository.upsertPayoutAccount(currentUserId, role, holderName, maskedDest, ifsc, isSuperAdmin)
                .onSuccess { showBanner("Payout account saved.") }
                .onFailure { showBanner(it.message ?: "Could not save payout account") }
        }
    }

    // --- Delivery Partner Actions ---
    fun registerDeliveryPartner(name: String, phone: String, vehicleType: String) {
        viewModelScope.launch {
            repository.upsertDeliveryPartnerProfile(currentUserId, name, phone, vehicleType, isSuperAdmin)
                .onSuccess {
                    showBanner(
                        if (isSuperAdmin) "Partner profile registered & approved."
                        else "Partner registered. Pending Super Admin approval."
                    )
                }
                .onFailure { showBanner(it.message ?: "Partner registration failed") }
        }
    }

    fun togglePartnerOnline(partner: DeliveryPartner, goOnline: Boolean) {
        viewModelScope.launch {
            if (goOnline) {
                val gps = mapsService.fetchCurrentDeviceLocation().getOrNull()
                val lat = gps?.latitude ?: _locationState.value.latitude
                val lng = gps?.longitude ?: _locationState.value.longitude
                repository.updatePartnerStatusAndLocation(partner, true, lat, lng)
            } else {
                repository.updatePartnerStatusAndLocation(partner, false, partner.currentLat, partner.currentLng)
            }
        }
    }

    fun claimDeliveryOrder(order: Order, partner: DeliveryPartner) {
        viewModelScope.launch {
            repository.claimOrderByPartner(order, partner)
                .onSuccess { showBanner("Delivery assigned: #${order.id.takeLast(6)}") }
                .onFailure { showBanner(it.message ?: "Could not claim order") }
        }
    }

    fun rejectDeliveryOrder(order: Order, reason: String) {
        viewModelScope.launch {
            repository.releaseOrderByPartner(order, currentUserId, reason)
                .onSuccess { showBanner("Order #${order.id.takeLast(6)} released for reassignment.") }
                .onFailure { showBanner(it.message ?: "Release failed") }
        }
    }

    fun advanceDeliveryOrder(order: Order, nextStatus: OrderStatus, partner: DeliveryPartner) {
        viewModelScope.launch {
            repository.advanceOrderByPartner(order, nextStatus, partner)
                .onSuccess { showBanner("Delivery updated -> ${nextStatus.name.replace('_', ' ')}") }
                .onFailure { showBanner(it.message ?: "Update failed") }
        }
    }

    // --- Super Admin Actions ---
    fun setStoreApproval(storeId: String, status: ApprovalStatus) {
        viewModelScope.launch {
            repository.setStoreApprovalStatus(storeId, status, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Store status -> ${status.name}") }
                .onFailure { showBanner(it.message ?: "Failed") }
        }
    }

    fun setPartnerApproval(partnerId: String, status: ApprovalStatus) {
        viewModelScope.launch {
            repository.setPartnerApprovalStatus(partnerId, status, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Partner status -> ${status.name}") }
                .onFailure { showBanner(it.message ?: "Failed") }
        }
    }

    fun verifyPayoutAccountKyc(accountId: String) {
        viewModelScope.launch {
            repository.verifyPayoutAccountKyc(accountId, currentUserId, currentUserEmail)
                .onSuccess { showBanner("KYC Verified for payout account.") }
                .onFailure { showBanner(it.message ?: "KYC verification failed") }
        }
    }

    fun updatePlatformSettings(settings: PlatformSettings) {
        viewModelScope.launch {
            repository.updatePlatformSettings(settings, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Platform payment & pricing settings saved.") }
                .onFailure { showBanner(it.message ?: "Settings save failed") }
        }
    }

    fun settleManualPayout(settlement: SettlementRecord, reference: String, allSettlements: List<SettlementRecord>) {
        viewModelScope.launch {
            repository.settleManualPayout(settlement, reference, allSettlements, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Manual payout settled with ref $reference.") }
                .onFailure { showBanner(it.message ?: "Settlement rejected") }
        }
    }

    fun triggerAutoPayout(
        settlement: SettlementRecord,
        payoutAccounts: List<PayoutAccount>,
        allSettlements: List<SettlementRecord>
    ) {
        viewModelScope.launch {
            val account = payoutAccounts.firstOrNull { it.userId == settlement.recipientId }
            repository.triggerAutomaticPayout(
                settlement = settlement,
                payoutAccount = account,
                settings = platformSettings.value,
                gatewayAdapter = paymentGateway,
                existingSettlements = allSettlements,
                actorId = currentUserId,
                actorEmail = currentUserEmail
            ).onSuccess {
                showBanner("Automatic payout executed cleanly.")
            }.onFailure {
                showBanner(it.message ?: "Automatic payout blocked")
            }
        }
    }

    fun processRefund(order: Order) {
        viewModelScope.launch {
            repository.requestOrProcessRefund(order, isAdminAction = isSuperAdmin, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Refund status updated for #${order.id.takeLast(6)}.") }
                .onFailure { showBanner(it.message ?: "Refund failed") }
        }
    }

    fun createCoupon(code: String, title: String, type: String, value: Double, minOrder: Double) {
        viewModelScope.launch {
            repository.createCoupon(code, title, type, value, minOrder, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Coupon $code published.") }
                .onFailure { showBanner(it.message ?: "Coupon creation failed") }
        }
    }

    fun resolveComplaint(complaint: Complaint, status: ComplaintStatus, note: String) {
        viewModelScope.launch {
            repository.resolveComplaintByAdmin(complaint, status, note, currentUserId, currentUserEmail)
                .onSuccess { showBanner("Complaint updated -> ${status.name}") }
                .onFailure { showBanner(it.message ?: "Could not update complaint") }
        }
    }

    fun seedDemoMarketplace() {
        viewModelScope.launch {
            repository.seedDemoMarketplaceIfAdmin(currentUserId, currentUserName, currentUserEmail)
                .onSuccess { showBanner("Demo marketplace stores & products synced.") }
                .onFailure { showBanner(it.message ?: "Seed failed") }
        }
    }

    // --- Gemini Multi-Turn Chatbot Actions ---
    fun selectChatTier(tier: GeminiChatTier) {
        _chatTier.value = tier
    }

    fun clearChatHistory() {
        chatJob?.cancel()
        _chatTurns.value = emptyList()
        _streamingReply.value = null
        _chatError.value = null
    }

    fun sendChatMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        chatJob?.cancel()
        val prior = _chatTurns.value
        val tier = _chatTier.value
        val role = _activePortal.value

        _chatTurns.value = prior + ChatTurn(isUser = true, text = trimmed)
        _streamingReply.value = ""
        _chatError.value = null

        chatJob = viewModelScope.launch {
            val acc = StringBuilder()
            try {
                aiRepository.streamChatResponse(trimmed, tier, role, prior).collect { textSoFar ->
                    acc.clear()
                    acc.append(textSoFar)
                    _streamingReply.value = textSoFar
                }
                _chatTurns.value = _chatTurns.value + ChatTurn(
                    isUser = false,
                    text = acc.toString(),
                    modelLabel = tier.label
                )
                _streamingReply.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                _streamingReply.value = null
                _chatError.value = e.localizedMessage ?: "Gemini chat request failed."
            }
        }
    }

    // --- Gemini 1K / 2K / 4K Image Studio Actions ---
    fun selectImageResolution(resolution: AiImageResolution) {
        _selectedImageResolution.value = resolution
    }

    fun selectImageAspect(aspect: AiImageAspect) {
        _selectedImageAspect.value = aspect
    }

    fun generateProductImage(prompt: String) {
        val resolution = _selectedImageResolution.value
        val aspect = _selectedImageAspect.value
        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            val res = aiRepository.generateProductImage(prompt, resolution, aspect)
            res.onSuccess { (bmp, path) ->
                _imageGenState.value = ImageGenUiState.Success(bmp, path, resolution.label)
            }.onFailure { err ->
                _imageGenState.value = ImageGenUiState.Error(err.message ?: "Image generation failed.")
            }
        }
    }

    companion object {
        private const val TAG = "NearbyGoVM"

        fun provideFactory(
            currentUserId: String,
            currentUserName: String,
            currentUserEmail: String,
            isEmailVerified: Boolean
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NearbyGoViewModel(
                    repository = FirestoreNearbyGoRepository(db),
                    mapsService = MapsService(app),
                    aiRepository = NearbyGoAiRepository(app),
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail,
                    isEmailVerified = isEmailVerified
                )
            }
        }
    }
}
