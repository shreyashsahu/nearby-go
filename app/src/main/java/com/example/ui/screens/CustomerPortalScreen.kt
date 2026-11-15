package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.UiState
import com.example.domain.engine.DeliveryFeeEngine
import com.example.domain.model.Coupon
import com.example.domain.model.Order
import com.example.domain.model.OrderStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Product
import com.example.domain.model.Store
import com.example.domain.model.StoreCategory
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LocationControlBar
import com.example.ui.components.OrderTimelineBar
import com.example.ui.components.StatusPill
import com.example.ui.components.orderStatusColor
import com.example.ui.viewmodel.NearbyGoViewModel
import java.io.File
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerPortalScreen(
    viewModel: NearbyGoViewModel,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    onOpenAiChat: () -> Unit
) {
    val context = LocalContext.current
    val storesState by viewModel.approvedStores.collectAsStateWithLifecycle()
    val productsState by viewModel.availableProducts.collectAsStateWithLifecycle()
    val cartState by viewModel.cartItems.collectAsStateWithLifecycle()
    val ordersState by viewModel.customerOrders.collectAsStateWithLifecycle()
    val couponsState by viewModel.activeCoupons.collectAsStateWithLifecycle()
    val reviewsState by viewModel.reviews.collectAsStateWithLifecycle()
    val complaintsState by viewModel.customerComplaints.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val settings by viewModel.platformSettings.collectAsStateWithLifecycle()
    val feeQuote by viewModel.currentDeliveryFeeQuote.collectAsStateWithLifecycle()

    val cartItems = (cartState as? UiState.Success)?.data.orEmpty()
    val cartCount = cartItems.sumOf { it.quantity.toInt() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.fetchDeviceGpsLocation()
        } else {
            viewModel.showBanner("Location permission denied. Enter your address manually below.")
        }
    }

    var showManualAddressDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { onSelectSubTab(0) },
                text = { Text("Shop", maxLines = 1) },
                icon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { onSelectSubTab(1) },
                text = { Text("Cart", maxLines = 1) },
                icon = {
                    BadgedBox(badge = { if (cartCount > 0) Badge { Text("$cartCount") } }) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { onSelectSubTab(2) },
                text = { Text("Orders", maxLines = 1) },
                icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedSubTab == 3,
                onClick = { onSelectSubTab(3) },
                text = { Text("Account", maxLines = 1) },
                icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        when (selectedSubTab) {
            0 -> CustomerShopTab(
                storesState = storesState,
                productsState = productsState,
                reviewsCount = (reviewsState as? UiState.Success)?.data?.size ?: 0,
                locationState = locationState,
                onRequestGps = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onEditManualAddress = { showManualAddressDialog = true },
                onAddToCart = { product, storeName -> viewModel.addToCart(product, storeName) },
                onOpenCart = { onSelectSubTab(1) },
                onOpenAiChat = onOpenAiChat,
                cartCount = cartCount
            )

            1 -> CustomerCartCheckoutTab(
                cartItems = cartItems,
                stores = (storesState as? UiState.Success)?.data.orEmpty(),
                coupons = (couponsState as? UiState.Success)?.data.orEmpty(),
                locationState = locationState,
                settings = settings,
                feeQuoteResult = feeQuote,
                onUpdateQty = { item, qty -> viewModel.updateCartQty(item, qty) },
                onPlaceOrder = { store, items, coupon, method, ref ->
                    viewModel.placeOrder(store, items, coupon, method, ref)
                    onSelectSubTab(2)
                },
                onBrowseStores = { onSelectSubTab(0) }
            )

            2 -> CustomerOrdersTab(
                ordersState = ordersState,
                availableProducts = (productsState as? UiState.Success)?.data.orEmpty(),
                onCancelOrder = { viewModel.cancelOrder(it) },
                onReorder = { order, prods ->
                    viewModel.reorderItems(order, prods)
                    onSelectSubTab(1)
                },
                onSubmitReview = { order, rating, comment ->
                    viewModel.submitReview(order, rating, comment)
                },
                onRequestRefund = { viewModel.processRefund(it) },
                onOpenMapsStore = { order ->
                    val uri = Uri.parse("geo:0,0?q=${Uri.encode(order.storeName + " " + order.deliveryAddress)}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
            )

            3 -> CustomerAccountSupportTab(
                userProfileState = profileState,
                notificationsState = notificationsState,
                complaintsState = complaintsState,
                orders = (ordersState as? UiState.Success)?.data.orEmpty(),
                onSaveProfile = { name, phone, addrs -> viewModel.updateCustomerProfile(name, phone, addrs) },
                onMarkNotificationRead = { viewModel.markNotificationRead(it) },
                onSubmitComplaint = { orderId, subject, desc -> viewModel.submitComplaint(orderId, subject, desc) }
            )
        }
    }

    if (showManualAddressDialog) {
        var addrInput by remember { mutableStateOf(locationState.addressText) }
        var distInput by remember { mutableLongStateOf(locationState.manualDistanceMeters) }
        AlertDialog(
            onDismissRequest = { showManualAddressDialog = false },
            title = { Text("Delivery Address & Distance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = addrInput,
                        onValueChange = { addrInput = it },
                        label = { Text("Street / Landmark") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Route Distance: ${distInput}m (Fee: ₹${DeliveryFeeEngine.calculateQuote(distInput, settings).getOrNull()?.deliveryFeeInr?.toInt() ?: 5})",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = distInput.toFloat(),
                        onValueChange = { distInput = (it / 100f).roundToLong() * 100L },
                        valueRange = 200f..5000f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateManualAddress(addrInput, distInput)
                    showManualAddressDialog = false
                }) { Text("Save Address") }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddressDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CustomerShopTab(
    storesState: UiState<List<Store>>,
    productsState: UiState<List<Product>>,
    reviewsCount: Int,
    locationState: com.example.ui.viewmodel.CustomerLocationState,
    onRequestGps: () -> Unit,
    onEditManualAddress: () -> Unit,
    onAddToCart: (Product, String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenAiChat: () -> Unit,
    cartCount: Int
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<StoreCategory?>(null) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var selectedProductDetail by remember { mutableStateOf<Product?>(null) }

    val stores = (storesState as? UiState.Success)?.data.orEmpty()
    val products = (productsState as? UiState.Success)?.data.orEmpty()

    val filteredStores = remember(stores, selectedCategory, searchQuery) {
        stores.filter { store ->
            (selectedCategory == null || store.category == selectedCategory!!.name) &&
                (searchQuery.isBlank() || store.name.contains(searchQuery, ignoreCase = true) ||
                    store.address.contains(searchQuery, ignoreCase = true))
        }
    }

    val storeLookup = remember(stores) { stores.associateBy { it.id } }

    val filteredProducts = remember(products, filteredStores, selectedStoreId, searchQuery) {
        val allowedStoreIds = filteredStores.map { it.id }.toSet()
        products.filter { prod ->
            (selectedStoreId == null || prod.storeId == selectedStoreId) &&
                (selectedStoreId != null || prod.storeId in allowedStoreIds) &&
                (searchQuery.isBlank() ||
                    prod.name.contains(searchQuery, ignoreCase = true) ||
                    prod.category.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LocationControlBar(
                locationState = locationState,
                onRequestGps = onRequestGps,
                onEditManualAddress = onEditManualAddress
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_marketplace),
                        contentDescription = "Neighborhood commerce banner",
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "15-Min Hyperlocal Express",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Delivery starts at ₹5 for 0–500m • $reviewsCount verified reviews",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onOpenAiChat,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Ask Gemini Shopping Concierge →")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                placeholder = { Text("Search groceries, meals, medicines, shops...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = {
                            selectedCategory = null
                            selectedStoreId = null
                        },
                        label = { Text("All") }
                    )
                }
                items(StoreCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                            selectedStoreId = null
                        },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nearby Stores (${filteredStores.size})", style = MaterialTheme.typography.titleMedium)
                if (selectedStoreId != null) {
                    TextButton(onClick = { selectedStoreId = null }) { Text("Show All Stores") }
                }
            }
        }

        if (storesState is UiState.Loading) {
            item { CircularProgressIndicator() }
        } else if (filteredStores.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No approved stores nearby",
                    subtitle = "Switch to Seller to register a store and Super Admin to approve it."
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredStores, key = { it.id }) { store ->
                        val isSelected = selectedStoreId == store.id
                        Card(
                            onClick = { selectedStoreId = if (isSelected) null else store.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.width(230.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = if (store.isOpen) "OPEN" else "CLOSED",
                                        color = if (store.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("${store.rating}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    text = store.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = store.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = store.openingHours,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Products (${filteredProducts.size})", style = MaterialTheme.typography.titleMedium)
                if (cartCount > 0) {
                    OutlinedButton(onClick = onOpenCart, modifier = Modifier.height(48.dp)) {
                        Text("View Cart ($cartCount)")
                    }
                }
            }
        }

        items(filteredProducts, key = { it.id }) { product ->
            val store = storeLookup[product.storeId]
            val storeName = store?.name ?: "Local Shop"
            Card(
                onClick = { selectedProductDetail = product },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (product.imageUrl.isNotBlank() && File(product.imageUrl).exists()) {
                        AsyncImage(
                            model = File(product.imageUrl),
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else if (product.imageUrl.startsWith("content:") || product.imageUrl.startsWith("http")) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = product.name,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$storeName • ${product.unitLabel} • ${product.stockCount} in stock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "₹${product.effectivePriceInr.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (product.discountPercent > 0L) {
                                Text(
                                    text = "₹${product.priceInr.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StatusPill("${product.discountPercent}% OFF", MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    Button(
                        onClick = { onAddToCart(product, storeName) },
                        enabled = product.stockCount > 0L && (store?.isOpen != false),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_to_cart_${product.id}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add ${product.name}", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }
    }

    if (selectedProductDetail != null) {
        val prod = selectedProductDetail!!
        val storeName = storeLookup[prod.storeId]?.name ?: "Local Store"
        AlertDialog(
            onDismissRequest = { selectedProductDetail = null },
            title = { Text(prod.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$storeName • ${prod.category}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(prod.description.ifBlank { "Freshly stocked neighborhood product." }, style = MaterialTheme.typography.bodyMedium)
                    Text("Price: ₹${prod.effectivePriceInr.toInt()} (${prod.unitLabel})", style = MaterialTheme.typography.titleMedium)
                    Text("Available Stock: ${prod.stockCount}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddToCart(prod, storeName)
                    selectedProductDetail = null
                }) { Text("Add to Cart") }
            },
            dismissButton = {
                TextButton(onClick = { selectedProductDetail = null }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomerCartCheckoutTab(
    cartItems: List<com.example.domain.model.CartItem>,
    stores: List<Store>,
    coupons: List<Coupon>,
    locationState: com.example.ui.viewmodel.CustomerLocationState,
    settings: com.example.domain.model.PlatformSettings,
    feeQuoteResult: Result<com.example.domain.engine.DeliveryFeeQuote>,
    onUpdateQty: (com.example.domain.model.CartItem, Long) -> Unit,
    onPlaceOrder: (Store, List<com.example.domain.model.CartItem>, Coupon?, PaymentMethod, String) -> Unit,
    onBrowseStores: () -> Unit
) {
    var couponCodeInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf<Coupon?>(null) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.COD) }
    var paymentRefInput by remember { mutableStateOf("") }

    if (cartItems.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                title = "Your NearbyGo cart is empty",
                subtitle = "Browse nearby grocery, restaurant, pharmacy, and craft shops to start an order.",
                icon = Icons.Filled.ShoppingCart,
                actionLabel = "Browse Stores",
                onAction = onBrowseStores
            )
        }
        return
    }

    val storeId = cartItems.first().storeId
    val store = stores.firstOrNull { it.id == storeId } ?: Store(id = storeId, name = cartItems.first().storeName)
    val subtotal = cartItems.sumOf { it.lineTotalInr }
    val activeCoupon = appliedCoupon?.takeIf { subtotal >= it.minOrderInr }
    val discount = if (activeCoupon != null) {
        if (activeCoupon.discountType == "PERCENT") {
            kotlin.math.round((subtotal * activeCoupon.discountValue / 100.0) * 100.0) / 100.0
        } else {
            activeCoupon.discountValue.coerceAtMost(subtotal)
        }
    } else 0.0
    val feeQuote = feeQuoteResult.getOrNull()
    val deliveryFee = feeQuote?.deliveryFeeInr ?: 0.0
    val total = ((subtotal - discount).coerceAtLeast(0.0) + deliveryFee)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Store: ${store.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(cartItems, key = { it.id }) { item ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "₹${item.effectiveUnitPriceInr.toInt()} × ${item.quantity} = ₹${item.lineTotalInr.toInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onUpdateQty(item, item.quantity - 1L) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                    }
                    Text("${item.quantity}", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onUpdateQty(item, item.quantity + 1L) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Coupons & Promotions", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = couponCodeInput,
                            onValueChange = { couponCodeInput = it.uppercase() },
                            label = { Text("Coupon Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                appliedCoupon = coupons.firstOrNull {
                                    it.code.equals(couponCodeInput.trim(), ignoreCase = true)
                                }
                            },
                            modifier = Modifier.height(52.dp)
                        ) { Text("Apply") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        coupons.forEach { coupon ->
                            FilterChip(
                                selected = appliedCoupon?.code == coupon.code,
                                onClick = {
                                    appliedCoupon = if (appliedCoupon?.code == coupon.code) null else coupon
                                    couponCodeInput = coupon.code
                                },
                                label = { Text("${coupon.code} (${coupon.title})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Order & Delivery Fee Breakdown", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Delivering to: ${locationState.addressText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (feeQuote != null) {
                        Text(
                            "${feeQuote.distanceMeters}m (${feeQuote.slabCount} × 500m slab) • ETA ~${feeQuote.estimatedMinutes} mins • ${feeQuote.sourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BillRow("Items Subtotal", "₹${subtotal.toInt()}")
                    if (discount > 0) {
                        BillRow("Coupon Discount (${activeCoupon?.code})", "-₹${discount.toInt()}")
                    }
                    BillRow("Delivery Fee", "₹${deliveryFee.toInt()}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BillRow("Total Payable", "₹${total.toInt()}", bold = true)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payment Method", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethod.entries.forEach { method ->
                            val enabled = if (method == PaymentMethod.COD) settings.codEnabled else settings.onlinePaymentsEnabled
                            FilterChip(
                                selected = selectedMethod == method,
                                enabled = enabled,
                                onClick = { selectedMethod = method },
                                label = { Text(method.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    if (selectedMethod != PaymentMethod.COD) {
                        OutlinedTextField(
                            value = paymentRefInput,
                            onValueChange = { paymentRefInput = it },
                            label = { Text("Payment / UTR / Razorpay Test Reference") },
                            placeholder = { Text("e.g., UTR998811 or pay_test_123") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "NearbyGo verifies references before settlement and never marks unverified payments as paid.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onPlaceOrder(store, cartItems, activeCoupon, selectedMethod, paymentRefInput) },
                enabled = feeQuoteResult.isSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("place_order_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Place Order • ₹${total.toInt()}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BillRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CustomerOrdersTab(
    ordersState: UiState<List<Order>>,
    availableProducts: List<Product>,
    onCancelOrder: (Order) -> Unit,
    onReorder: (Order, List<Product>) -> Unit,
    onSubmitReview: (Order, Long, String) -> Unit,
    onRequestRefund: (Order) -> Unit,
    onOpenMapsStore: (Order) -> Unit
) {
    var reviewTarget by remember { mutableStateOf<Order?>(null) }

    when (ordersState) {
        is UiState.Loading -> Column(Modifier.padding(24.dp)) { CircularProgressIndicator() }
        is UiState.Error -> Column(Modifier.padding(16.dp)) {
            EmptyStateCard("Unable to load orders", ordersState.message)
        }
        is UiState.Success -> {
            val activeOrders = ordersState.data.filter { OrderStatus.valueOf(it.status).isActive }
            val pastOrders = ordersState.data.filterNot { OrderStatus.valueOf(it.status).isActive }
            if (ordersState.data.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    EmptyStateCard("No orders yet", "Place an order from the Shop tab to track live delivery status.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeOrders.isNotEmpty()) {
                        item { Text("Active Orders (${activeOrders.size})", style = MaterialTheme.typography.titleMedium) }
                        items(activeOrders, key = { it.id }) { order ->
                            CustomerOrderCard(
                                order = order,
                                onCancel = { onCancelOrder(order) },
                                onReorder = {},
                                onRate = {},
                                onRefund = { onRequestRefund(order) },
                                onTrackMap = { onOpenMapsStore(order) }
                            )
                        }
                    }
                    if (pastOrders.isNotEmpty()) {
                        item { Text("Delivered & History (${pastOrders.size})", style = MaterialTheme.typography.titleMedium) }
                        items(pastOrders, key = { it.id }) { order ->
                            CustomerOrderCard(
                                order = order,
                                onCancel = {},
                                onReorder = { onReorder(order, availableProducts) },
                                onRate = { reviewTarget = order },
                                onRefund = { onRequestRefund(order) },
                                onTrackMap = { onOpenMapsStore(order) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (reviewTarget != null) {
        val ord = reviewTarget!!
        var rating by remember { mutableLongStateOf(5L) }
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("Rate ${ord.storeName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1L..5L).forEach { star ->
                            FilterChip(
                                selected = rating == star,
                                onClick = { rating = star },
                                label = { Text("${star}★") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Share your feedback") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitReview(ord, rating, comment)
                    reviewTarget = null
                }) { Text("Submit Review") }
            },
            dismissButton = {
                TextButton(onClick = { reviewTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CustomerOrderCard(
    order: Order,
    onCancel: () -> Unit,
    onReorder: () -> Unit,
    onRate: () -> Unit,
    onRefund: () -> Unit,
    onTrackMap: () -> Unit
) {
    val statusEnum = runCatching { OrderStatus.valueOf(order.status) }.getOrDefault(OrderStatus.PLACED)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("#${order.id.takeLast(6)} • ${order.paymentMethod} (${order.paymentStatus})", style = MaterialTheme.typography.labelSmall)
                }
                StatusPill(order.status.replace('_', ' '), orderStatusColor(order.status))
            }

            Text(order.itemsSummary.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            Text(
                "Total: ₹${order.totalInr.toInt()} (Delivery ₹${order.deliveryFeeInr.toInt()} for ${order.distanceMeters}m)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (order.deliveryPartnerName.isNotBlank()) {
                Text("Partner: ${order.deliveryPartnerName}", style = MaterialTheme.typography.bodySmall)
            }

            OrderTimelineBar(order.timelineEvents)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (statusEnum == OrderStatus.PLACED || statusEnum == OrderStatus.ACCEPTED) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                        Text("Cancel")
                    }
                }
                if (statusEnum.isActive) {
                    OutlinedButton(onClick = onTrackMap, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Map")
                    }
                }
                if (statusEnum == OrderStatus.DELIVERED) {
                    Button(onClick = onReorder, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reorder")
                    }
                    OutlinedButton(onClick = onRate, modifier = Modifier.height(48.dp)) {
                        Text("Rate")
                    }
                    OutlinedButton(onClick = onRefund, modifier = Modifier.height(48.dp)) {
                        Text("Refund")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerAccountSupportTab(
    userProfileState: UiState<com.example.domain.model.UserProfile?>,
    notificationsState: UiState<List<com.example.domain.model.UserNotification>>,
    complaintsState: UiState<List<com.example.domain.model.Complaint>>,
    orders: List<Order>,
    onSaveProfile: (String, String, List<String>) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSubmitComplaint: (String, String, String) -> Unit
) {
    val profile = (userProfileState as? UiState.Success)?.data
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val complaints = (complaintsState as? UiState.Success)?.data.orEmpty()

    var nameInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phoneInput by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
    var addrInput by remember(profile?.savedAddresses) {
        mutableStateOf(profile?.savedAddresses?.joinToString(" | ").orEmpty())
    }
    var subject by remember { mutableStateOf("") }
    var complaintDesc by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer Profile & Wallet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Wallet Balance: ₹${(profile?.walletBalanceInr ?: 0.0).toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addrInput,
                        onValueChange = { addrInput = it },
                        label = { Text("Saved Addresses (separate with |)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val list = addrInput.split("|").map { it.trim() }.filter { it.isNotBlank() }
                            onSaveProfile(nameInput, phoneInput, list)
                        },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Save Profile") }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SupportAgent, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Private Customer Support & Complaints", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Complaints are private and visible ONLY to you and authorized Super Admin staff (hidden from sellers and delivery partners).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Issue Subject") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = complaintDesc,
                        onValueChange = { complaintDesc = it },
                        label = { Text("Describe the issue") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSubmitComplaint(orders.firstOrNull()?.id.orEmpty(), subject, complaintDesc)
                            subject = ""
                            complaintDesc = ""
                        },
                        enabled = subject.length >= 3 && complaintDesc.length >= 5,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("submit_complaint_button")
                    ) { Text("Submit Private Complaint") }

                    complaints.forEach { cmp ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cmp.subject, style = MaterialTheme.typography.labelLarge)
                                    StatusPill(cmp.status)
                                }
                                Text(cmp.description, style = MaterialTheme.typography.bodySmall)
                                if (cmp.adminResolutionNote.isNotBlank()) {
                                    Text("Admin Note: ${cmp.adminResolutionNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Notifications (${notifications.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (notifications.isEmpty()) {
                        Text("No notifications yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        notifications.take(10).forEach { ntf ->
                            Surface(
                                onClick = { if (!ntf.isRead) onMarkNotificationRead(ntf.id) },
                                color = if (ntf.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(ntf.title, style = MaterialTheme.typography.labelLarge)
                                    Text(ntf.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
