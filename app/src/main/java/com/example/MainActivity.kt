package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.UiState
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.domain.model.PortalRole
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutFromNearbyGo
import com.example.ui.screens.AdminPortalScreen
import com.example.ui.screens.AiConciergeSheet
import com.example.ui.screens.CustomerPortalScreen
import com.example.ui.screens.DeliveryPortalScreen
import com.example.ui.screens.SellerPortalScreen
import com.example.ui.theme.NearbyGoTheme
import com.example.ui.viewmodel.NearbyGoViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must install Firebase App Check before calling Firebase AI Logic or rendering Compose UI.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            NearbyGoTheme {
                NearbyGoAppRoot()
            }
        }
    }
}

@Composable
private fun NearbyGoAppRoot() {
    val authUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val currentUser = authUser
    if (currentUser == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(currentUser.uid) {
            AuthenticatedNearbyGoShell(user = currentUser)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedNearbyGoShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NearbyGoViewModel = viewModel(
        factory = NearbyGoViewModel.provideFactory(
            currentUserId = user.uid,
            currentUserName = user.displayName?.takeIf { it.isNotBlank() } ?: "NearbyGo User",
            currentUserEmail = user.email.orEmpty(),
            isEmailVerified = user.isEmailVerified
        )
    )

    val activePortal by viewModel.activePortal.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val cartState by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartCount = (cartState as? UiState.Success)?.data?.sumOf { it.quantity } ?: 0L

    val snackbarHostState = remember { SnackbarHostState() }
    var customerSubTab by remember { mutableIntStateOf(0) }
    var showAiSheet by remember { mutableStateOf(false) }
    var aiInitialTab by remember { mutableIntStateOf(0) }
    var pendingAiImagePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearBanner()
        }
    }

    BackHandler(
        enabled = showAiSheet || activePortal != PortalRole.CUSTOMER || customerSubTab != 0
    ) {
        when {
            showAiSheet -> showAiSheet = false
            activePortal != PortalRole.CUSTOMER -> viewModel.switchPortal(PortalRole.CUSTOMER)
            customerSubTab != 0 -> customerSubTab = 0
        }
    }

    val openAiChat: () -> Unit = {
        aiInitialTab = 0
        showAiSheet = true
    }
    val openAiStudio: () -> Unit = {
        aiInitialTab = 1
        showAiSheet = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "NearbyGo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activePortal.label} Portal • ${user.email ?: "Signed In"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = openAiChat,
                            modifier = Modifier.testTag("open_ai_concierge_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Open NearbyGo Gemini AI Concierge",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                signOutFromNearbyGo(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out of NearbyGo"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        PortalRole.entries.forEach { role ->
                            val selected = activePortal == role
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.switchPortal(role) },
                                icon = { PortalRoleIcon(role = role, cartCount = cartCount) },
                                label = { Text(role.label) },
                                modifier = Modifier.testTag("nav_portal_${role.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalRole.entries.forEach { role ->
                                NavigationRailItem(
                                    selected = activePortal == role,
                                    onClick = { viewModel.switchPortal(role) },
                                    icon = { PortalRoleIcon(role = role, cartCount = cartCount) },
                                    label = { Text(role.label) },
                                    modifier = Modifier.testTag("rail_portal_${role.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (activePortal) {
                        PortalRole.CUSTOMER -> CustomerPortalScreen(
                            viewModel = viewModel,
                            selectedSubTab = customerSubTab,
                            onSelectSubTab = { customerSubTab = it },
                            onOpenAiChat = openAiChat
                        )
                        PortalRole.SELLER -> SellerPortalScreen(
                            viewModel = viewModel,
                            pendingAiImagePath = pendingAiImagePath,
                            onClearPendingAiImage = { pendingAiImagePath = null },
                            onOpenAiImageStudio = openAiStudio
                        )
                        PortalRole.DELIVERY -> DeliveryPortalScreen(viewModel = viewModel)
                        PortalRole.ADMIN -> AdminPortalScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showAiSheet) {
        AiConciergeSheet(
            viewModel = viewModel,
            initialTab = aiInitialTab,
            onAttachImageToSellerProduct = { savedPath ->
                pendingAiImagePath = savedPath
                showAiSheet = false
                viewModel.switchPortal(PortalRole.SELLER)
            },
            onDismiss = { showAiSheet = false }
        )
    }
}

@Composable
private fun PortalRoleIcon(
    role: PortalRole,
    cartCount: Long
) {
    val icon = when (role) {
        PortalRole.CUSTOMER -> Icons.Filled.ShoppingBag
        PortalRole.SELLER -> Icons.Filled.Storefront
        PortalRole.DELIVERY -> Icons.Filled.DeliveryDining
        PortalRole.ADMIN -> Icons.Filled.AdminPanelSettings
    }
    if (role == PortalRole.CUSTOMER && cartCount > 0L) {
        BadgedBox(
            badge = {
                Badge { Text(cartCount.toString()) }
            }
        ) {
            Icon(icon, contentDescription = role.label)
        }
    } else {
        Icon(icon, contentDescription = role.label)
    }
}
