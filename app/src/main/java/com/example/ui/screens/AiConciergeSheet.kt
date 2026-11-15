package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiImageAspect
import com.example.data.ai.AiImageResolution
import com.example.data.ai.GeminiChatTier
import com.example.domain.model.PortalRole
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.NearbyGoViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiConciergeSheet(
    viewModel: NearbyGoViewModel,
    initialTab: Int = 0,
    onAttachImageToSellerProduct: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "NearbyGo Gemini AI",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close AI sheet")
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Multi-Turn Chat") },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("1K / 2K / 4K Studio") },
                    icon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                GeminiMultiTurnChatPane(viewModel = viewModel, modifier = Modifier.weight(1f))
            } else {
                GeminiImageStudioPane(
                    viewModel = viewModel,
                    onAttachImageToSellerProduct = onAttachImageToSellerProduct,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiMultiTurnChatPane(
    viewModel: NearbyGoViewModel,
    modifier: Modifier = Modifier
) {
    val activeRole by viewModel.activePortal.collectAsStateWithLifecycle()
    val chatTier by viewModel.chatTier.collectAsStateWithLifecycle()
    val turns by viewModel.chatTurns.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, streamingReply) {
        val total = turns.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem((total - 1).coerceAtLeast(0))
        }
    }

    val starterPrompts = remember(activeRole) {
        when (activeRole) {
            PortalRole.CUSTOMER -> listOf(
                "Compare delivery fee for 1.2 km vs 2.4 km",
                "Suggest a healthy quick dinner + grocery basket under ₹500"
            )
            PortalRole.SELLER -> listOf(
                "Write a high-converting title for organic cold-pressed oil",
                "Suggest peak-hour prep workflow to reduce cancellations"
            )
            PortalRole.DELIVERY -> listOf(
                "Checklist for fast store pickup and doorstep verification",
                "How are partner earnings calculated per trip?"
            )
            PortalRole.ADMIN -> listOf(
                "Audit unit economics for ₹500 order with 10% commission",
                "When should I switch from Manual to Automatic payouts?"
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Role Persona: ${activeRole.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear conversation history")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GeminiChatTier.entries.forEach { tier ->
                FilterChip(
                    selected = chatTier == tier,
                    onClick = { viewModel.selectChatTier(tier) },
                    label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (turns.isEmpty() && streamingReply == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Ask anything (${chatTier.subtitle}). Conversation history stays active across turns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        starterPrompts.forEach { prompt ->
                            OutlinedButton(
                                onClick = { viewModel.sendChatMessage(prompt) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(prompt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            items(turns, key = { it.id }) { turn ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (turn.isUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!turn.isUser && turn.modelLabel.isNotBlank()) {
                                Text(
                                    text = "Gemini • ${turn.modelLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (turn.isUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            if (streamingReply != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Gemini streaming...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = streamingReply.orEmpty().ifEmpty { "Thinking..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        if (!chatError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chatError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask NearbyGo Gemini...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("gemini_chat_input"),
                singleLine = true
            )
            Button(
                onClick = {
                    val msg = inputText
                    inputText = ""
                    viewModel.sendChatMessage(msg)
                },
                enabled = inputText.isNotBlank() && streamingReply == null,
                modifier = Modifier
                    .height(52.dp)
                    .testTag("gemini_chat_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiImageStudioPane(
    viewModel: NearbyGoViewModel,
    onAttachImageToSellerProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.imageGenState.collectAsStateWithLifecycle()
    val selectedRes by viewModel.selectedImageResolution.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedImageAspect.collectAsStateWithLifecycle()
    var prompt by remember {
        mutableStateOf("Studio product photo of fresh organic Hass avocados in a woven eco basket on clean alabaster marble, soft daylight")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Generate studio product photography & banners with Gemini Pro Image. Choose resolution (1K, 2K, or 4K) and aspect ratio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Image Prompt") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_image_prompt_input")
        )

        Text("Output Resolution", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiImageResolution.entries.forEach { res ->
                FilterChip(
                    selected = selectedRes == res,
                    onClick = { viewModel.selectImageResolution(res) },
                    label = { Text(res.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("res_chip_${res.label}")
                )
            }
        }

        Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiImageAspect.entries.forEach { asp ->
                FilterChip(
                    selected = selectedAspect == asp,
                    onClick = { viewModel.selectImageAspect(asp) },
                    label = { Text(asp.label) }
                )
            }
        }

        Button(
            onClick = { viewModel.generateProductImage(prompt) },
            enabled = state !is ImageGenUiState.Loading && prompt.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_ai_image_button")
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Generate ${selectedRes.label} Image")
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val current = state) {
                    is ImageGenUiState.Idle -> {
                        Text(
                            text = "Preview will appear here after generation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ImageGenUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Synthesizing ${selectedRes.label} image (may take 30–60s)...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is ImageGenUiState.Success -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = current.bitmap.asImageBitmap(),
                                contentDescription = "Generated ${current.resolutionLabel} product image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "Generated at ${current.resolutionLabel} resolution",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { onAttachImageToSellerProduct(current.savedPath) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Attach to Seller Product")
                            }
                        }
                    }
                    is ImageGenUiState.Error -> {
                        Text(
                            text = "Error: ${current.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
