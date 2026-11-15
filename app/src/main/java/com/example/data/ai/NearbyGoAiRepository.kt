package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.PortalRole
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class GeminiChatTier(
    val modelName: String,
    val label: String,
    val subtitle: String
) {
    FAST("gemini-3.5-flash-lite", "Fast", "Ultra-fast responses (Flash-Lite)"),
    GENERAL("gemini-3.8-flash", "Balanced", "General shopping & ops (3.8 Flash)"),
    DEEP("gemini-4-argon", "Deep Reasoning", "Complex analysis & math (4 Argon)")
}

enum class AiImageResolution(
    val label: String,
    val sdkSize: ImageSize
) {
    SIZE_1K("1K", ImageSize.SIZE_1K),
    SIZE_2K("2K", ImageSize.SIZE_2K),
    SIZE_4K("4K", ImageSize.SIZE_4K)
}

enum class AiImageAspect(
    val label: String,
    val sdkRatio: AspectRatio
) {
    SQUARE_1_1("1:1", AspectRatio.SQUARE_1x1),
    CARD_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    BANNER_16_9("16:9", AspectRatio.LANDSCAPE_16x9)
}

data class ChatTurn(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val modelLabel: String = ""
)

class NearbyGoAiRepository(private val appContext: Context) {

    fun systemInstructionForRole(role: PortalRole): String = when (role) {
        PortalRole.CUSTOMER ->
            "You are NearbyGo Concierge, a helpful hyperlocal shopping and food delivery assistant. " +
                "Help customers pick fresh groceries, compare restaurant meals, explain delivery fee tiers " +
                "(₹5 per 500m), suggest coupons, and guide order tracking concisely."
        PortalRole.SELLER ->
            "You are NearbyGo Merchant Advisor. Help local shopkeepers and restaurateurs optimize " +
                "product titles, pricing, inventory alerts, prep-time workflows, and studio photography prompts."
        PortalRole.DELIVERY ->
            "You are NearbyGo Dispatch Copilot. Assist delivery partners with pickup verification steps, " +
                "route efficiency, busy-state management, and payout settlement tracking."
        PortalRole.ADMIN ->
            "You are NearbyGo Operations Analyst. Assist the Super Admin with marketplace unit economics, " +
                "commission vs. delivery fee splits, payout reconciliation checks, and complaint triage."
    }

    private fun createChatSession(
        tier: GeminiChatTier,
        role: PortalRole,
        priorHistory: List<ChatTurn>
    ): Chat {
        val model = Firebase.ai.generativeModel(
            modelName = tier.modelName,
            systemInstruction = content { text(systemInstructionForRole(role)) }
        )
        val sdkHistory = priorHistory.map { turn ->
            content(if (turn.isUser) "user" else "model") {
                text(turn.text)
            }
        }
        return model.startChat(history = sdkHistory)
    }

    fun streamChatResponse(
        userMessage: String,
        tier: GeminiChatTier,
        role: PortalRole,
        priorHistory: List<ChatTurn>
    ): Flow<String> = flow {
        val chat = createChatSession(tier, role, priorHistory)
        val buffer = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    buffer.append(part)
                    emit(buffer.toString())
                }
            }
        }
        if (buffer.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }
    }

    suspend fun generateProductImage(
        prompt: String,
        resolution: AiImageResolution,
        aspect: AiImageAspect,
        useProStudioModel: Boolean = true
    ): Result<Pair<Bitmap, String>> {
        val trimmed = prompt.trim()
        if (trimmed.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a descriptive product image prompt."))
        }
        return try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(aspect.sdkRatio)
                    .setImageSize(resolution.sdkSize)
                    .build()
            }
            val modelName = if (useProStudioModel) "gemini-3-pro-image" else "gemini-nano-banana-2.1"
            val model = Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = config
            )
            val response = withTimeout(60_000L) {
                model.generateContent(trimmed)
            }
            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: return Result.failure(IllegalStateException("No image was returned by Gemini."))

            val savedPath = saveBitmapToInternalFiles(bitmap)
            Result.success(bitmap to savedPath)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds. Please retry."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            Result.failure(IllegalStateException("Gemini image quota exceeded. Check Firebase Blaze plan or try later."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "AI image generation failed."))
        }
    }

    private suspend fun saveBitmapToInternalFiles(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "ai_product_images").apply { mkdirs() }
            val file = File(dir, "prod_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            file.absolutePath
        }

    private companion object {
        const val TAG = "NearbyGoAiRepo"
    }
}
