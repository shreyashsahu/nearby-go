package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.domain.model.ApprovalStatus
import com.example.domain.model.DeliveryPartner
import com.example.domain.model.Order
import com.example.domain.model.OrderStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiTile
import com.example.ui.components.OrderTimelineBar
import com.example.ui.components.StatusPill
import com.example.ui.components.orderStatusColor
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.NearbyGoViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeliveryPortalScreen(
    viewModel: NearbyGoViewModel
) {
    val context = LocalContext.current
    val partnerState by viewModel.deliveryPartnerProfile.collectAsStateWithLifecycle()
    val readyOrdersState by viewModel.readyUnassignedOrders.collectAsStateWithLifecycle()
    val assignedOrdersState by viewModel.partnerAssignedOrders.collectAsStateWithLifecycle()
    val settlementsState by viewModel.mySettlements.collectAsStateWithLifecycle()
    val payoutAccountsState by viewModel.myPayoutAccounts.collectAsStateWithLifecycle()

    val partner = (partnerState as? UiState.Success)?.data
    val readyOrders = (readyOrdersState as? UiState.Success)?.data.orEmpty()
    val assignedOrders = (assignedOrdersState as? UiState.Success)?.data.orEmpty()
    val settlements = (settlementsState as? UiState.Success)?.data.orEmpty()
    val payoutAccounts = (payoutAccountsState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var rejectTargetOrder by remember { mutableStateOf<Order?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (partner != null) {
            if (!granted) {
                viewModel.showBanner("GPS permission denied; partner online with fallback coordinates.")
            }
            viewModel.togglePartnerOnline(partner, true)
        }
    }

    val openMapsNavigation: (String) -> Unit = { address ->
        val navUri = Uri.parse("google.navigation:q=${Uri.encode(address)}")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).setPackage("com.google.android.apps.maps")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
        when {
            navIntent.resolveActivity(context.packageManager) != null -> context.startActivity(navIntent)
            fallbackIntent.resolveActivity(context.packageManager) != null -> context.startActivity(fallbackIntent)
            else -> viewModel.showBanner("No Google Maps application found on this device.")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Missions") },
                modifier = Modifier.testTag("delivery_tab_missions")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Earnings") },
                modifier = Modifier.testTag("delivery_tab_earnings")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Profile") },
                modifier = Modifier.testTag("delivery_tab_profile")
            )
        }

        when (selectedTab) {
            0 -> {
                val activeAssigned = assignedOrders.filter {
                    runCatching { OrderStatus.valueOf(it.status).isActive }.getOrDefault(false)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (partner == null) {
                        item {
                            PartnerRegistrationCard(
                                defaultName = viewModel.currentUserName,
                                onRegister = { name, phone, vehicle ->
                                    viewModel.registerDeliveryPartner(name, phone, vehicle)
                                }
                            )
                        }
                    } else {
                        item {
                            PartnerStatusHeaderCard(
                                partner = partner,
                                onToggleOnline = { goOnline ->
                                    if (goOnline) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            viewModel.togglePartnerOnline(partner, true)
                                        } else {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    } else {
                                        viewModel.togglePartnerOnline(partner, false)
                                    }
                                }
                            )
                        }

                        if (partner.approvalStatus != ApprovalStatus.APPROVED.name) {
                            item {
                                Surface(
                                    color = StatusWarning.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Partner verification status: ${partner.approvalStatus}. Super Admin approval is required before claiming deliveries.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Active Assigned Deliveries (${activeAssigned.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (activeAssigned.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No active delivery mission",
                                    subtitle = "Accept a READY request below when online to start pickup & dropoff.",
                                    icon = Icons.Filled.DeliveryDining
                                )
                            }
                        } else {
                            items(activeAssigned, key = { it.id }) { order ->
                                ActiveDeliveryOrderCard(
                                    order = order,
                                    onNavigate = { openMapsNavigation(order.deliveryAddress) },
                                    onAdvance = { next -> viewModel.advanceDeliveryOrder(order, next, partner) },
                                    onRelease = { rejectTargetOrder = order }
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Available READY Requests (${readyOrders.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (readyOrders.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No unassigned READY orders",
                                    subtitle = "Orders marked READY by sellers appear here in real time.",
                                    icon = Icons.Filled.LocalShipping
                                )
                            }
                        } else {
                            items(readyOrders, key = { it.id }) { order ->
                                ReadyOrderOfferCard(
                                    order = order,
                                    partner = partner,
                                    onAccept = { viewModel.claimDeliveryOrder(order, partner) },
                                    onReject = { rejectTargetOrder = order },
                                    onPreviewRoute = { openMapsNavigation(order.deliveryAddress) }
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                val pastOrders = assignedOrders.filterNot {
                    runCatching { OrderStatus.valueOf(it.status).isActive }.getOrDefault(false)
                }
                val existingAccount = payoutAccounts.firstOrNull { it.role == "DELIVERY" }
                var holder by remember(existingAccount?.accountHolderName) {
                    mutableStateOf(existingAccount?.accountHolderName.orEmpty())
                }
                var dest by remember(existingAccount?.maskedDestination) {
                    mutableStateOf(existingAccount?.maskedDestination.orEmpty())
                }
                var ifsc by remember(existingAccount?.ifscCode) {
                    mutableStateOf(existingAccount?.ifscCode.orEmpty())
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile(
                                title = "Deliveries",
                                value = "${partner?.completedDeliveries ?: 0L}",
                                subtitle = "Completed trips",
                                icon = Icons.Filled.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                            KpiTile(
                                title = "Partner Earnings",
                                value = "₹${(partner?.totalEarningsInr ?: 0.0).toInt()}",
                                subtitle = if (partner?.isOnline == true) "Online" else "Offline",
                                icon = Icons.Filled.AccountBalance,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Delivery Payout Account", style = MaterialTheme.typography.titleMedium)
                                    StatusPill(existingAccount?.kycStatus ?: "NOT SET")
                                }
                                OutlinedTextField(
                                    value = holder,
                                    onValueChange = { holder = it },
                                    label = { Text("Account Holder Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = dest,
                                    onValueChange = { dest = it },
                                    label = { Text("Masked Bank / UPI ID") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = ifsc,
                                    onValueChange = { ifsc = it },
                                    label = { Text("IFSC Code") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { viewModel.savePayoutAccount("DELIVERY", holder, dest, ifsc) },
                                    enabled = holder.length >= 2 && dest.length >= 3,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("save_delivery_payout_button")
                                ) {
                                    Text("Save Payout Account")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Settlement History (${settlements.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (settlements.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No settlements yet",
                                subtitle = "Completed deliveries generate automatic settlement ledger entries.",
                                icon = Icons.Filled.ReceiptLong
                            )
                        }
                    } else {
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
                                        Text(
                                            "Net: ₹${stl.netPayableInr} • ${stl.payoutMode}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (stl.paymentReference.isNotBlank()) {
                                            Text("Ref: ${stl.paymentReference}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    StatusPill(stl.status)
                                }
                            }
                        }
                    }

                    if (pastOrders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Completed Delivery History (${pastOrders.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(pastOrders.take(15), key = { it.id }) { order ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "#${order.id.takeLast(6)} • ${order.storeName}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "Fee Earned: ₹${order.deliveryEarningInr} • ${order.distanceMeters}m",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    StatusPill(order.status, orderStatusColor(order.status))
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                var editName by remember(partner?.name) {
                    mutableStateOf(partner?.name ?: viewModel.currentUserName)
                }
                var editPhone by remember(partner?.phone) {
                    mutableStateOf(partner?.phone.orEmpty())
                }
                var editVehicle by remember(partner?.vehicleType) {
                    mutableStateOf(partner?.vehicleType ?: "Electric Scooter")
                }
                val vehicleOptions = listOf("Electric Scooter", "Motorcycle", "Bicycle", "EV Van")

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Person, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delivery Partner Profile", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Full Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPhone,
                                    onValueChange = { editPhone = it },
                                    label = { Text("Phone Number") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Vehicle Type", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    vehicleOptions.forEach { vehicle ->
                                        FilterChip(
                                            selected = editVehicle == vehicle,
                                            onClick = { editVehicle = vehicle },
                                            label = { Text(vehicle) }
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.registerDeliveryPartner(editName, editPhone, editVehicle)
                                    },
                                    enabled = editName.trim().length >= 2 && editPhone.trim().length >= 6,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_partner_profile_button")
                                ) {
                                    Text(if (partner == null) "Register Partner" else "Update Partner Profile")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejectTargetOrder?.let { target ->
        var reason by remember { mutableStateOf("Partner unavailable / busy route") }
        AlertDialog(
            onDismissRequest = { rejectTargetOrder = null },
            title = { Text("Reject / Release #${target.id.takeLast(6)}") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for reassignment") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectDeliveryOrder(target, reason)
                        rejectTargetOrder = null
                    },
                    enabled = reason.isNotBlank()
                ) { Text("Confirm Release") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetOrder = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnerRegistrationCard(
    defaultName: String,
    onRegister: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Electric Scooter") }
    val vehicles = listOf("Electric Scooter", "Motorcycle", "Bicycle", "EV Van")

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.DeliveryDining, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Become a NearbyGo Delivery Partner", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Register your profile to go online, accept READY store pickups, and earn distance-based delivery fees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Partner Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Number") },
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vehicles.forEach { v ->
                    FilterChip(
                        selected = vehicle == v,
                        onClick = { vehicle = v },
                        label = { Text(v) }
                    )
                }
            }
            Button(
                onClick = { onRegister(name, phone, vehicle) },
                enabled = name.trim().length >= 2 && phone.trim().length >= 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("register_partner_button")
            ) {
                Text("Register Partner Account")
            }
        }
    }
}

@Composable
private fun PartnerStatusHeaderCard(
    partner: DeliveryPartner,
    onToggleOnline: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(partner.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${partner.vehicleType} • ${partner.phone}", style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(
                    text = partner.approvalStatus,
                    color = if (partner.approvalStatus == ApprovalStatus.APPROVED.name) StatusSuccess else StatusWarning
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (partner.isOnline) "Online • Receiving Requests" else "Offline • Paused",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (partner.isBusy) {
                        Text(
                            text = "Busy on active order #${partner.activeOrderId.takeLast(6)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusDanger
                        )
                    }
                }
                Switch(
                    checked = partner.isOnline,
                    onCheckedChange = onToggleOnline,
                    modifier = Modifier.testTag("partner_online_switch")
                )
            }
        }
    }
}

@Composable
private fun ActiveDeliveryOrderCard(
    order: Order,
    onNavigate: () -> Unit,
    onAdvance: (OrderStatus) -> Unit,
    onRelease: () -> Unit
) {
    val current = runCatching { OrderStatus.valueOf(order.status) }.getOrDefault(OrderStatus.ASSIGNED)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${order.id.takeLast(6)} • ${order.storeName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Customer: ${order.customerName}", style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(order.status, orderStatusColor(order.status))
            }
            Text(
                "Dropoff: ${order.deliveryAddress}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Trip Distance: ${order.distanceMeters}m • Delivery Earning: ₹${order.deliveryEarningInr}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            OrderTimelineBar(order.timelineEvents)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("navigate_maps_button_${order.id}")
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
                when (current) {
                    OrderStatus.ASSIGNED -> {
                        Button(
                            onClick = { onAdvance(OrderStatus.PICKED_UP) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pickup_order_button_${order.id}")
                        ) { Text("Confirm Pickup") }
                    }
                    OrderStatus.PICKED_UP -> {
                        Button(
                            onClick = { onAdvance(OrderStatus.OUT_FOR_DELIVERY) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("out_for_delivery_button_${order.id}")
                        ) { Text("Out for Delivery") }
                    }
                    OrderStatus.OUT_FOR_DELIVERY -> {
                        Button(
                            onClick = { onAdvance(OrderStatus.DELIVERED) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("complete_delivery_button_${order.id}")
                        ) { Text("Mark Delivered") }
                    }
                    else -> {}
                }
            }
            if (current == OrderStatus.ASSIGNED) {
                TextButton(
                    onClick = onRelease,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Release Assignment", color = StatusDanger)
                }
            }
        }
    }
}

@Composable
private fun ReadyOrderOfferCard(
    order: Order,
    partner: DeliveryPartner,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onPreviewRoute: () -> Unit
) {
    val canClaim = partner.approvalStatus == ApprovalStatus.APPROVED.name &&
        partner.isOnline &&
        !partner.isBusy

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${order.id.takeLast(6)} • ${order.storeName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${order.itemsSummary.size} item(s) • Drop: ${order.deliveryAddress}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusPill("READY", StatusSuccess)
            }
            Text(
                "Distance: ${order.distanceMeters}m • Fee: ₹${order.deliveryEarningInr}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (!canClaim) {
                val note = when {
                    partner.approvalStatus != ApprovalStatus.APPROVED.name -> "Awaiting Super Admin approval to claim orders."
                    partner.isBusy -> "Busy lock active: finish current delivery first."
                    else -> "Switch Online to accept this delivery."
                }
                Text(note, style = MaterialTheme.typography.labelSmall, color = StatusWarning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    enabled = canClaim,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("claim_delivery_button_${order.id}")
                ) { Text("Accept Trip") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reject_delivery_button_${order.id}")
                ) { Text("Reject") }
                OutlinedButton(
                    onClick = onPreviewRoute,
                    modifier = Modifier.height(48.dp)
                ) { Text("Map") }
            }
        }
    }
}
