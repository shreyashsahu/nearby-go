package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.domain.model.Order
import com.example.domain.model.OrderStatus
import com.example.domain.model.Product
import com.example.domain.model.StoreCategory
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiTile
import com.example.ui.components.StatusPill
import com.example.ui.components.orderStatusColor
import com.example.ui.viewmodel.NearbyGoViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SellerPortalScreen(
    viewModel: NearbyGoViewModel,
    pendingAiImagePath: String?,
    onClearPendingAiImage: () -> Unit,
    onOpenAiImageStudio: () -> Unit
) {
    val myStoresState by viewModel.myStores.collectAsStateWithLifecycle()
    val myProductsState by viewModel.myProducts.collectAsStateWithLifecycle()
    val sellerOrdersState by viewModel.sellerOrders.collectAsStateWithLifecycle()
    val settlementsState by viewModel.mySettlements.collectAsStateWithLifecycle()
    val payoutAccountsState by viewModel.myPayoutAccounts.collectAsStateWithLifecycle()

    val stores = (myStoresState as? UiState.Success)?.data.orEmpty()
    val products = (myProductsState as? UiState.Success)?.data.orEmpty()
    val orders = (sellerOrdersState as? UiState.Success)?.data.orEmpty()
    val settlements = (settlementsState as? UiState.Success)?.data.orEmpty()
    val payoutAccounts = (payoutAccountsState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateStoreDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var attachedImagePath by remember { mutableStateOf("") }

    LaunchedEffect(pendingAiImagePath) {
        if (!pendingAiImagePath.isNullOrBlank()) {
            attachedImagePath = pendingAiImagePath
            selectedTab = 1
            showProductDialog = true
            onClearPendingAiImage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Orders") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Catalog") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Earnings") })
        }

        when (selectedTab) {
            0 -> {
                val activeOrders = orders.filter { OrderStatus.valueOf(it.status).isActive }
                val pastOrders = orders.filterNot { OrderStatus.valueOf(it.status).isActive }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("My Stores (${stores.size})", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = { showCreateStoreDialog = true },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Store")
                            }
                        }
                    }

                    items(stores, key = { it.id }) { store ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(store.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${store.category} • ${store.address}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill(store.approvalStatus)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Store Open", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = store.isOpen,
                                        onCheckedChange = { viewModel.toggleStoreSettings(store, it, store.autoAcceptOrders) }
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto-Accept Orders", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = store.autoAcceptOrders,
                                        onCheckedChange = { viewModel.toggleStoreSettings(store, store.isOpen, it) }
                                    )
                                }
                            }
                        }
                    }

                    item { Text("Incoming & Active Orders (${activeOrders.size})", style = MaterialTheme.typography.titleMedium) }
                    if (activeOrders.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No incoming active orders",
                                subtitle = "New orders placed at your stores appear here in real time."
                            )
                        }
                    } else {
                        items(activeOrders, key = { it.id }) { order ->
                            SellerOrderCard(order = order, onAdvance = { viewModel.advanceOrderBySeller(order, it) })
                        }
                    }

                    if (pastOrders.isNotEmpty()) {
                        item { Text("Order History (${pastOrders.size})", style = MaterialTheme.typography.titleMedium) }
                        items(pastOrders.take(10), key = { it.id }) { order ->
                            SellerOrderCard(order = order, onAdvance = {})
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    editingProduct = null
                                    attachedImagePath = ""
                                    showProductDialog = true
                                },
                                enabled = stores.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_product_button")
                            ) {
                                Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Product")
                            }
                            OutlinedButton(
                                onClick = onOpenAiImageStudio,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_seller_ai_studio")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("4K AI Studio")
                            }
                        }
                    }

                    if (stores.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Create a store first",
                                subtitle = "Register your merchant store on the Orders tab before adding products."
                            )
                        }
                    } else if (products.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No products in catalog",
                                subtitle = "Add products with stock, discounts, and Gemini AI studio photography."
                            )
                        }
                    } else {
                        items(products, key = { it.id }) { prod ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "₹${prod.effectivePriceInr.toInt()} (${prod.discountPercent}% off) • Stock: ${prod.stockCount}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        StatusPill(if (prod.isAvailable) "AVAILABLE" else "OUT OF STOCK")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingProduct = prod
                                            attachedImagePath = prod.imageUrl
                                            showProductDialog = true
                                        },
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Edit") }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                val deliveredOrders = orders.filter { it.status == OrderStatus.DELIVERED.name }
                val totalSellerEarnings = deliveredOrders.sumOf { it.sellerEarningInr }
                val totalCommission = deliveredOrders.sumOf { it.platformCommissionInr }
                val existingAccount = payoutAccounts.firstOrNull { it.role == "SELLER" }
                var holder by remember(existingAccount?.accountHolderName) { mutableStateOf(existingAccount?.accountHolderName.orEmpty()) }
                var dest by remember(existingAccount?.maskedDestination) { mutableStateOf(existingAccount?.maskedDestination.orEmpty()) }
                var ifsc by remember(existingAccount?.ifscCode) { mutableStateOf(existingAccount?.ifscCode.orEmpty()) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                title = "Seller Net Earnings",
                                value = "₹${totalSellerEarnings.toInt()}",
                                subtitle = "${deliveredOrders.size} delivered",
                                icon = Icons.Filled.ReceiptLong,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                title = "Commission Paid",
                                value = "₹${totalCommission.toInt()}",
                                subtitle = "Platform share",
                                icon = Icons.Filled.AccountBalance,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Payout Account", style = MaterialTheme.typography.titleMedium)
                                    StatusPill(existingAccount?.kycStatus ?: "NOT SET")
                                }
                                OutlinedTextField(value = holder, onValueChange = { holder = it }, label = { Text("Account Holder Name") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = dest, onValueChange = { dest = it }, label = { Text("Masked Bank / UPI ID") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = ifsc, onValueChange = { ifsc = it }, label = { Text("IFSC Code") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = { viewModel.savePayoutAccount("SELLER", holder, dest, ifsc) },
                                    enabled = holder.length >= 2 && dest.length >= 3,
                                    modifier = Modifier.height(48.dp)
                                ) { Text("Save Payout Account") }
                            }
                        }
                    }

                    item { Text("Settlement History (${settlements.size})", style = MaterialTheme.typography.titleMedium) }
                    items(settlements, key = { it.id }) { stl ->
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Order #${stl.orderId.takeLast(6)}", style = MaterialTheme.typography.titleSmall)
                                    Text("Net: ₹${stl.netPayableInr} • ${stl.payoutMode}", style = MaterialTheme.typography.bodySmall)
                                    if (stl.paymentReference.isNotBlank()) {
                                        Text("Ref: ${stl.paymentReference}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                StatusPill(stl.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateStoreDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(StoreCategory.GROCERY) }
        var desc by remember { mutableStateOf("") }
        var addr by remember { mutableStateOf("") }
        var hours by remember { mutableStateOf("08:00 AM - 10:00 PM") }
        var autoAccept by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateStoreDialog = false },
            title = { Text("Register Seller Store") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoreCategory.entries.forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) })
                        }
                    }
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Shop Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Opening Hours") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-Accept Orders")
                        Switch(checked = autoAccept, onCheckedChange = { autoAccept = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createStore(name, category, desc, addr, hours, autoAccept)
                        showCreateStoreDialog = false
                    },
                    enabled = name.length >= 2 && addr.length >= 3
                ) { Text("Create Store") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateStoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showProductDialog && stores.isNotEmpty()) {
        val targetStore = stores.first()
        var name by remember(editingProduct) { mutableStateOf(editingProduct?.name.orEmpty()) }
        var cat by remember(editingProduct) { mutableStateOf(editingProduct?.category ?: "Essentials") }
        var price by remember(editingProduct) { mutableStateOf(editingProduct?.priceInr?.toString() ?: "99") }
        var discount by remember(editingProduct) { mutableStateOf(editingProduct?.discountPercent?.toString() ?: "0") }
        var stock by remember(editingProduct) { mutableStateOf(editingProduct?.stockCount?.toString() ?: "25") }
        var unit by remember(editingProduct) { mutableStateOf(editingProduct?.unitLabel ?: "1 pack") }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                attachedImagePath = uri.toString()
            }
        }

        AlertDialog(
            onDismissRequest = { showProductDialog = false },
            title = { Text(if (editingProduct == null) "New Product" else "Edit Product") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price ₹") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Disc %") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stock") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Photo Picker")
                        }
                        OutlinedButton(
                            onClick = {
                                showProductDialog = false
                                onOpenAiImageStudio()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Studio")
                        }
                    }
                    if (attachedImagePath.isNotBlank()) {
                        Text("Image attached: ${attachedImagePath.takeLast(32)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.upsertProduct(
                            productId = editingProduct?.id,
                            storeId = editingProduct?.storeId ?: targetStore.id,
                            name = name,
                            category = cat,
                            description = "",
                            priceInr = price.toDoubleOrNull() ?: 99.0,
                            discountPercent = discount.toLongOrNull() ?: 0L,
                            stockCount = stock.toLongOrNull() ?: 0L,
                            imageUrl = attachedImagePath,
                            unitLabel = unit
                        )
                        showProductDialog = false
                    },
                    enabled = name.length >= 2
                ) { Text("Save Product") }
            },
            dismissButton = {
                TextButton(onClick = { showProductDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SellerOrderCard(
    order: Order,
    onAdvance: (OrderStatus) -> Unit
) {
    val current = runCatching { OrderStatus.valueOf(order.status) }.getOrDefault(OrderStatus.PLACED)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${order.id.takeLast(6)} • ${order.customerName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(order.itemsSummary.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(order.status, orderStatusColor(order.status))
            }
            Text("Seller Net: ₹${order.sellerEarningInr} • Drop: ${order.deliveryAddress}", style = MaterialTheme.typography.labelSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (current) {
                    OrderStatus.PLACED -> {
                        Button(onClick = { onAdvance(OrderStatus.ACCEPTED) }, modifier = Modifier.height(48.dp)) { Text("Accept") }
                        OutlinedButton(onClick = { onAdvance(OrderStatus.REJECTED) }, modifier = Modifier.height(48.dp)) { Text("Reject") }
                    }
                    OrderStatus.ACCEPTED -> {
                        Button(onClick = { onAdvance(OrderStatus.PREPARING) }, modifier = Modifier.height(48.dp)) { Text("Start Preparing") }
                    }
                    OrderStatus.PREPARING -> {
                        Button(onClick = { onAdvance(OrderStatus.READY) }, modifier = Modifier.height(48.dp)) { Text("Mark READY") }
                    }
                    else -> {}
                }
            }
        }
    }
}
