package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.domain.model.ApprovalStatus
import com.example.domain.model.Complaint
import com.example.domain.model.ComplaintStatus
import com.example.domain.model.OrderStatus
import com.example.domain.model.PlatformSettings
import com.example.domain.model.SettlementMode
import com.example.domain.model.SettlementRecord
import com.example.domain.model.SettlementStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiTile
import com.example.ui.components.StatusPill
import com.example.ui.components.orderStatusColor
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.NearbyGoViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalScreen(
    viewModel: NearbyGoViewModel
) {
    if (!viewModel.isSuperAdmin) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyStateCard(
                title = "Super Admin Access Restricted",
                subtitle = "Only the verified Super Admin (shreyashsahu1500@gmail.com) can govern platform settings, payouts, seller/partner approvals, and private customer complaints.",
                icon = Icons.Filled.Lock
            )
        }
        return
    }

    val storesState by viewModel.adminAllStores.collectAsStateWithLifecycle()
    val partnersState by viewModel.adminAllPartners.collectAsStateWithLifecycle()
    val ordersState by viewModel.adminAllOrders.collectAsStateWithLifecycle()
    val paymentsState by viewModel.adminAllPayments.collectAsStateWithLifecycle()
    val settlementsState by viewModel.adminAllSettlements.collectAsStateWithLifecycle()
    val payoutAccountsState by viewModel.adminAllPayoutAccounts.collectAsStateWithLifecycle()
    val couponsState by viewModel.adminAllCoupons.collectAsStateWithLifecycle()
    val complaintsState by viewModel.adminAllComplaints.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.adminAuditLogs.collectAsStateWithLifecycle()
    val settings by viewModel.platformSettings.collectAsStateWithLifecycle()

    val stores = (storesState as? UiState.Success)?.data.orEmpty()
    val partners = (partnersState as? UiState.Success)?.data.orEmpty()
    val orders = (ordersState as? UiState.Success)?.data.orEmpty()
    val payments = (paymentsState as? UiState.Success)?.data.orEmpty()
    val settlements = (settlementsState as? UiState.Success)?.data.orEmpty()
    val payoutAccounts = (payoutAccountsState as? UiState.Success)?.data.orEmpty()
    val coupons = (couponsState as? UiState.Success)?.data.orEmpty()
    val complaints = (complaintsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var manualSettleTarget by remember { mutableStateOf<SettlementRecord?>(null) }
    var complaintTarget by remember { mutableStateOf<Pair<Complaint, ComplaintStatus>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Overview") },
                modifier = Modifier.testTag("admin_tab_overview")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Payouts") },
                modifier = Modifier.testTag("admin_tab_payouts")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Complaints") },
                modifier = Modifier.testTag("admin_tab_complaints")
            )
        }

        when (selectedTab) {
            0 -> {
                val activeCount = orders.count {
                    runCatching { OrderStatus.valueOf(it.status).isActive }.getOrDefault(false)
                }
                val deliveredCount = orders.count { it.status == OrderStatus.DELIVERED.name }
                val cancelledCount = orders.count {
                    it.status == OrderStatus.CANCELLED.name || it.status == OrderStatus.REJECTED.name
                }
                val grossRevenue = orders.sumOf { it.totalInr }
                val totalCommission = orders.sumOf { it.platformCommissionInr }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Super Admin Command", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Mode: ${settings.settlementMode} • Fee: ₹${settings.deliveryBaseFeeInr.toInt()} + ₹${settings.deliveryPer500mInr.toInt()}/500m",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.seedDemoMarketplace() },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("admin_seed_demo_button")
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Demo")
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                KpiTile(
                                    title = "Total Orders",
                                    value = "${orders.size}",
                                    subtitle = "$activeCount active • $deliveredCount delivered",
                                    icon = Icons.Filled.ReceiptLong,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    title = "Gross Revenue",
                                    value = "₹${grossRevenue.toInt()}",
                                    subtitle = "Comm: ₹${totalCommission.toInt()} • $cancelledCount cancelled",
                                    icon = Icons.Filled.AccountBalanceWallet,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                KpiTile(
                                    title = "Sellers / Stores",
                                    value = "${stores.size}",
                                    subtitle = "${stores.count { it.approvalStatus == ApprovalStatus.APPROVED.name }} approved",
                                    icon = Icons.Filled.Storefront,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    title = "Delivery Partners",
                                    value = "${partners.size}",
                                    subtitle = "${partners.count { it.isOnline }} online • ${partners.count { it.approvalStatus == ApprovalStatus.APPROVED.name }} approved",
                                    icon = Icons.Filled.DeliveryDining,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        Text("Seller Stores Approval (${stores.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(stores, key = { it.id }) { store ->
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(store.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${store.category} • ${store.address}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill(
                                        text = store.approvalStatus,
                                        color = if (store.approvalStatus == ApprovalStatus.APPROVED.name) StatusSuccess else StatusWarning
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.setStoreApproval(store.id, ApprovalStatus.APPROVED) },
                                        enabled = store.approvalStatus != ApprovalStatus.APPROVED.name,
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Approve") }
                                    OutlinedButton(
                                        onClick = { viewModel.setStoreApproval(store.id, ApprovalStatus.SUSPENDED) },
                                        enabled = store.approvalStatus != ApprovalStatus.SUSPENDED.name,
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Suspend") }
                                }
                            }
                        }
                    }

                    item {
                        Text("Delivery Partners Approval (${partners.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (partners.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No delivery partners registered yet",
                                subtitle = "Partners registering in the Partner portal appear here for verification."
                            )
                        }
                    } else {
                        items(partners, key = { it.id }) { partner ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(partner.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${partner.vehicleType} • ${partner.phone} • Trips: ${partner.completedDeliveries}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        StatusPill(partner.approvalStatus)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.setPartnerApproval(partner.id, ApprovalStatus.APPROVED) },
                                            enabled = partner.approvalStatus != ApprovalStatus.APPROVED.name,
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("Approve") }
                                        OutlinedButton(
                                            onClick = { viewModel.setPartnerApproval(partner.id, ApprovalStatus.SUSPENDED) },
                                            enabled = partner.approvalStatus != ApprovalStatus.SUSPENDED.name,
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("Suspend") }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Payout Account KYC Queue (${payoutAccounts.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(payoutAccounts, key = { it.id }) { acc ->
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${acc.accountHolderName} (${acc.role})", style = MaterialTheme.typography.titleSmall)
                                    Text("${acc.maskedDestination} • ${acc.ifscCode}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (acc.kycStatus == "VERIFIED") {
                                    StatusPill("KYC VERIFIED", StatusSuccess)
                                } else {
                                    Button(
                                        onClick = { viewModel.verifyPayoutAccountKyc(acc.id) },
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Verify KYC") }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                var onlineEnabled by remember(settings) { mutableStateOf(settings.onlinePaymentsEnabled) }
                var codEnabled by remember(settings) { mutableStateOf(settings.codEnabled) }
                var mode by remember(settings) {
                    mutableStateOf(
                        runCatching { SettlementMode.valueOf(settings.settlementMode) }.getOrDefault(SettlementMode.MANUAL)
                    )
                }
                var commPct by remember(settings) { mutableStateOf(settings.platformCommissionPercent.toString()) }
                var sellerPct by remember(settings) { mutableStateOf(settings.sellerPayoutPercent.toString()) }
                var baseFee by remember(settings) { mutableStateOf(settings.deliveryBaseFeeInr.toString()) }
                var slabFee by remember(settings) { mutableStateOf(settings.deliveryPer500mInr.toString()) }
                var minThreshold by remember(settings) { mutableStateOf(settings.minPayoutThresholdInr.toString()) }

                val refundQueue = orders.filter { it.status == OrderStatus.REFUND_PENDING.name }

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
                                Text("Payment, Pricing & Payout Mode Controls", style = MaterialTheme.typography.titleMedium)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Online Payments (UPI/Card/Razorpay)")
                                    Switch(checked = onlineEnabled, onCheckedChange = { onlineEnabled = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Cash on Delivery (COD)")
                                    Switch(checked = codEnabled, onCheckedChange = { codEnabled = it })
                                }

                                Text("Settlement Payout Mode", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = mode == SettlementMode.MANUAL,
                                        onClick = { mode = SettlementMode.MANUAL },
                                        label = { Text("Mode 1: Manual Payout (UTR)") },
                                        modifier = Modifier.testTag("mode_manual_chip")
                                    )
                                    FilterChip(
                                        selected = mode == SettlementMode.AUTOMATIC,
                                        onClick = { mode = SettlementMode.AUTOMATIC },
                                        label = { Text("Mode 2: Automatic Payout") },
                                        modifier = Modifier.testTag("mode_automatic_chip")
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = commPct,
                                        onValueChange = { commPct = it },
                                        label = { Text("Commission %") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = sellerPct,
                                        onValueChange = { sellerPct = it },
                                        label = { Text("Seller Payout %") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = baseFee,
                                        onValueChange = { baseFee = it },
                                        label = { Text("Base Fee (0-500m ₹)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = slabFee,
                                        onValueChange = { slabFee = it },
                                        label = { Text("+500m Slab ₹") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = minThreshold,
                                    onValueChange = { minThreshold = it },
                                    label = { Text("Min Auto-Payout Threshold ₹") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        viewModel.updatePlatformSettings(
                                            PlatformSettings(
                                                id = settings.id.ifBlank { "default" },
                                                onlinePaymentsEnabled = onlineEnabled,
                                                codEnabled = codEnabled,
                                                settlementMode = mode.name,
                                                platformCommissionPercent = commPct.toDoubleOrNull() ?: 10.0,
                                                sellerPayoutPercent = sellerPct.toDoubleOrNull() ?: 85.0,
                                                deliveryBaseFeeInr = baseFee.toDoubleOrNull() ?: 5.0,
                                                deliveryPer500mInr = slabFee.toDoubleOrNull() ?: 5.0,
                                                minPayoutThresholdInr = minThreshold.toDoubleOrNull() ?: 100.0
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("admin_save_settings_button")
                                ) {
                                    Text("Save Platform Settings")
                                }
                            }
                        }
                    }

                    if (refundQueue.isNotEmpty()) {
                        item {
                            Text("Pending Refunds (${refundQueue.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(refundQueue, key = { it.id }) { order ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("#${order.id.takeLast(6)} • ${order.customerName}", style = MaterialTheme.typography.titleSmall)
                                        Text("Amount: ₹${order.totalInr.toInt()} • ${order.paymentMethod}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = { viewModel.processRefund(order) },
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Refund") }
                                }
                            }
                        }
                    }

                    item {
                        Text("Settlement Payout Queue (${settlements.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (settlements.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No settlement entries yet",
                                subtitle = "Delivering an order generates Seller & Partner settlement entries automatically."
                            )
                        }
                    } else {
                        items(settlements, key = { it.id }) { stl ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${stl.recipientRole} • Order #${stl.orderId.takeLast(6)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Net: ₹${stl.netPayableInr} (Comm: ₹${stl.commissionInr})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (stl.paymentReference.isNotBlank()) {
                                                Text("Ref: ${stl.paymentReference}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        StatusPill(
                                            text = stl.status,
                                            color = if (stl.status == SettlementStatus.PAID.name) StatusSuccess else StatusWarning
                                        )
                                    }
                                    if (stl.status == SettlementStatus.PENDING.name) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { manualSettleTarget = stl },
                                                modifier = Modifier.height(48.dp)
                                            ) { Text("Manual Settle (UTR)") }
                                            Button(
                                                onClick = {
                                                    viewModel.triggerAutoPayout(stl, payoutAccounts, settlements)
                                                },
                                                modifier = Modifier.height(48.dp)
                                            ) { Text("Auto Payout") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Recent Payments Ledger (${payments.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(payments.take(15), key = { it.id }) { pay ->
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("#${pay.orderId.takeLast(6)} • ${pay.method}", style = MaterialTheme.typography.titleSmall)
                                    Text("₹${pay.amountInr.toInt()} • Ref: ${pay.providerReference.ifBlank { "COD/Pending" }}", style = MaterialTheme.typography.bodySmall)
                                }
                                StatusPill(pay.status, orderStatusColor(pay.status))
                            }
                        }
                    }
                }
            }

            2 -> {
                var couponCode by remember { mutableStateOf("") }
                var couponTitle by remember { mutableStateOf("") }
                var couponType by remember { mutableStateOf("FLAT") }
                var couponVal by remember { mutableStateOf("40") }
                var couponMin by remember { mutableStateOf("199") }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Private Customer Complaints (Strictly isolated to Super Admin — hidden from Sellers & Partners).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        Text("Customer Complaints (${complaints.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (complaints.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No open complaints",
                                subtitle = "Customer support tickets appear here privately for Super Admin resolution.",
                                icon = Icons.Filled.ReportProblem
                            )
                        }
                    } else {
                        items(complaints, key = { it.id }) { cmp ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cmp.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "By ${cmp.customerName} • Order: ${cmp.orderId.ifBlank { "General" }}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        StatusPill(
                                            text = cmp.status,
                                            color = when (cmp.status) {
                                                ComplaintStatus.RESOLVED.name -> StatusSuccess
                                                ComplaintStatus.IN_REVIEW.name -> StatusWarning
                                                else -> StatusDanger
                                            }
                                        )
                                    }
                                    Text(cmp.description, style = MaterialTheme.typography.bodySmall)
                                    if (cmp.adminResolutionNote.isNotBlank()) {
                                        Text(
                                            "Admin Resolution: ${cmp.adminResolutionNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { complaintTarget = cmp to ComplaintStatus.IN_REVIEW },
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("Mark In Review") }
                                        Button(
                                            onClick = { complaintTarget = cmp to ComplaintStatus.RESOLVED },
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("Resolve") }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Campaign, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Promo Coupon (${coupons.size} total)", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = couponCode,
                                    onValueChange = { couponCode = it.uppercase() },
                                    label = { Text("Coupon Code (e.g. SAVE50)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = couponTitle,
                                    onValueChange = { couponTitle = it },
                                    label = { Text("Campaign Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("FLAT", "PERCENT").forEach { t ->
                                        FilterChip(
                                            selected = couponType == t,
                                            onClick = { couponType = t },
                                            label = { Text(t) }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = couponVal,
                                        onValueChange = { couponVal = it },
                                        label = { Text("Value") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = couponMin,
                                        onValueChange = { couponMin = it },
                                        label = { Text("Min Order ₹") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.createCoupon(
                                            code = couponCode,
                                            title = couponTitle,
                                            type = couponType,
                                            value = couponVal.toDoubleOrNull() ?: 40.0,
                                            minOrder = couponMin.toDoubleOrNull() ?: 199.0
                                        )
                                        couponCode = ""
                                        couponTitle = ""
                                    },
                                    enabled = couponCode.trim().length >= 3,
                                    modifier = Modifier.height(48.dp)
                                ) { Text("Publish Coupon") }
                            }
                        }
                    }

                    item {
                        Text("Admin Audit Logs (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(auditLogs.take(20), key = { it.id }) { log ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(log.targetType, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                                Text("Actor: ${log.actorEmail}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    manualSettleTarget?.let { stl ->
        var utrRef by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manualSettleTarget = null },
            title = { Text("Manual Settlement UTR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Recipient: ${stl.recipientRole} • Net Payable: ₹${stl.netPayableInr}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = utrRef,
                        onValueChange = { utrRef = it },
                        label = { Text("Bank UTR / Payout Ref (min 4 chars)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val allSettlements = (settlementsState as? UiState.Success)?.data.orEmpty()
                Button(
                    onClick = {
                        viewModel.settleManualPayout(stl, utrRef, allSettlements)
                        manualSettleTarget = null
                    },
                    enabled = utrRef.trim().length >= 4
                ) { Text("Confirm Settled") }
            },
            dismissButton = {
                TextButton(onClick = { manualSettleTarget = null }) { Text("Cancel") }
            }
        )
    }

    complaintTarget?.let { (cmp, targetStatus) ->
        var note by remember { mutableStateOf(cmp.adminResolutionNote.ifBlank { "Reviewed by Super Admin support." }) }
        AlertDialog(
            onDismissRequest = { complaintTarget = null },
            title = { Text("Update Complaint -> ${targetStatus.name}") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Resolution Note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resolveComplaint(cmp, targetStatus, note)
                        complaintTarget = null
                    },
                    enabled = note.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { complaintTarget = null }) { Text("Cancel") }
            }
        )
    }
}
