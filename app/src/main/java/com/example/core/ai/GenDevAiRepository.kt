package com.example.core.ai

import android.util.Log
import com.example.core.data.AiProviderMode
import com.example.core.data.GenDevSettings
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "GenDevAiRepo"

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String, val providerLabel: String) : AiStreamEvent
    data class Completed(val fullText: String, val providerLabel: String) : AiStreamEvent
    data class Failed(val errorMessage: String, val fallbackText: String) : AiStreamEvent
}

class GenDevAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """You are GENDEV AI, an expert senior Android systems, APK reverse-engineering, DEX/Smali, Gradle, and security assistant inside GENDEV MANAGER.
Provide concise, accurate, actionable technical guidance.
Safety rules: Never advise executing destructive root commands without explicit user review, never invent fake APK signatures, and clearly label any command before the user runs it in Terminal."""
                )
            }
        )
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(55, TimeUnit.SECONDS)
            .build()
    }

    fun generateResponseStream(
        prompt: String,
        attachedContext: String?,
        settings: GenDevSettings
    ): Flow<AiStreamEvent> = flow {
        val fullPrompt = buildString {
            append(prompt.trim())
            if (!attachedContext.isNullOrBlank()) {
                append("\n\n--- ATTACHED WORKSPACE CONTEXT ---\n")
                append(attachedContext.take(12_000))
            }
        }

        when (settings.aiProviderMode) {
            AiProviderMode.OFFLINE_ONLY -> {
                val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                emit(AiStreamEvent.Completed(offline, "Offline Static Analyzer"))
            }

            AiProviderMode.CUSTOM_ENDPOINT -> {
                if (settings.customAiApiKey.isBlank() || settings.customAiEndpoint.isBlank()) {
                    val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                    emit(
                        AiStreamEvent.Failed(
                            errorMessage = "Custom AI endpoint or API key is not configured in Settings. Showing offline static analysis instead.",
                            fallbackText = offline
                        )
                    )
                } else {
                    try {
                        val reply = withTimeout(60_000L) {
                            callCustomOpenAiEndpoint(
                                endpoint = settings.customAiEndpoint,
                                modelName = settings.customAiModel,
                                apiKey = settings.customAiApiKey,
                                prompt = fullPrompt
                            )
                        }
                        emit(AiStreamEvent.Completed(reply, "Custom (${settings.customAiModel})"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Custom AI endpoint call failed", e)
                        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                        emit(
                            AiStreamEvent.Failed(
                                errorMessage = e.localizedMessage ?: "Custom AI request failed.",
                                fallbackText = offline
                            )
                        )
                    }
                }
            }

            AiProviderMode.FIREBASE_HYBRID -> {
                val sb = StringBuilder()
                try {
                    withTimeout(60_000L) {
                        model.generateContentStream(fullPrompt).collect { chunk ->
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                sb.append(delta)
                                emit(AiStreamEvent.Chunk(sb.toString(), "Gemini 3.8 Flash (Firebase)"))
                            }
                        }
                    }
                    if (sb.isNotBlank()) {
                        emit(AiStreamEvent.Completed(sb.toString(), "Gemini 3.8 Flash (Firebase)"))
                    } else {
                        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                        emit(
                            AiStreamEvent.Failed(
                                errorMessage = "Gemini returned an empty response. Offline static analysis provided.",
                                fallbackText = offline
                            )
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Firebase AI generation timed out", e)
                    val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                    emit(
                        AiStreamEvent.Failed(
                            errorMessage = "Cloud AI request timed out after 60s. Offline static analysis provided.",
                            fallbackText = offline
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI generation failed", e)
                    val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                    emit(
                        AiStreamEvent.Failed(
                            errorMessage = e.localizedMessage ?: "Cloud AI unavailable. Offline static analysis provided.",
                            fallbackText = offline
                        )
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun callCustomOpenAiEndpoint(
        endpoint: String,
        modelName: String,
        apiKey: String,
        prompt: String
    ): String {
        val bodyJson = JSONObject().apply {
            put("model", modelName)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "You are GENDEV AI, an Android APK, DEX/Smali, and systems engineering assistant.")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    )
                }
            )
        }
        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${raw.take(240)}")
            }
            val parsed = JSONObject(raw)
            val choices = parsed.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
            error("Unexpected response payload from custom endpoint")
        }
    }
}
