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
                text(SYSTEM_PROMPT)
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

            AiProviderMode.GEMINI_API_KEY -> {
                if (settings.geminiApiKey.isBlank()) {
                    val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                    emit(
                        AiStreamEvent.Failed(
                            errorMessage = "Gemini API Key belum diisi. Ketuk tombol 'API Key' di atas atau buka Settings untuk memasukkan key Anda.",
                            fallbackText = offline
                        )
                    )
                } else {
                    try {
                        val modelId = settings.geminiModel.ifBlank { "gemini-2.5-flash" }
                        val reply = withTimeout(60_000L) {
                            callGeminiApiKeyRest(
                                apiKey = settings.geminiApiKey,
                                modelName = modelId,
                                prompt = fullPrompt
                            )
                        }
                        emit(AiStreamEvent.Completed(reply, "Gemini API Key ($modelId)"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini REST API key call failed", e)
                        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                        emit(
                            AiStreamEvent.Failed(
                                errorMessage = e.localizedMessage ?: "Gemini API Key request failed.",
                                fallbackText = offline
                            )
                        )
                    }
                }
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
                        handleFirebaseFallback(prompt, attachedContext, fullPrompt, settings, "Gemini returned an empty response.")
                            .also { emit(it) }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Firebase AI generation timed out", e)
                    emit(handleFirebaseFallback(prompt, attachedContext, fullPrompt, settings, "Firebase AI timed out after 60s."))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI generation failed", e)
                    val msg = e.localizedMessage ?: "Cloud AI unavailable."
                    emit(handleFirebaseFallback(prompt, attachedContext, fullPrompt, settings, msg))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun handleFirebaseFallback(
        prompt: String,
        attachedContext: String?,
        fullPrompt: String,
        settings: GenDevSettings,
        firebaseError: String
    ): AiStreamEvent {
        if (settings.geminiApiKey.isNotBlank()) {
            try {
                val modelId = settings.geminiModel.ifBlank { "gemini-2.5-flash" }
                val reply = withTimeout(60_000L) {
                    callGeminiApiKeyRest(
                        apiKey = settings.geminiApiKey,
                        modelName = modelId,
                        prompt = fullPrompt
                    )
                }
                return AiStreamEvent.Completed(reply, "Gemini Key Fallback ($modelId)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini key fallback also failed", e)
            }
        }
        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
        val hint = if (firebaseError.contains("403") || firebaseError.contains("App Check", ignoreCase = true)) {
            "Firebase App Check belum tervalidasi di APK standalone. Ketuk 'API Key' di atas untuk memakai Gemini API Key pribadi Anda (gratis dari Google AI Studio)."
        } else {
            "$firebaseError Offline static analysis provided."
        }
        return AiStreamEvent.Failed(errorMessage = hint, fallbackText = offline)
    }

    private fun callGeminiApiKeyRest(
        apiKey: String,
        modelName: String,
        prompt: String
    ): String {
        val cleanModel = modelName.trim().removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
        val bodyJson = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }
                )
            )
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val apiErrMsg = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull()
                error(apiErrMsg?.takeIf { it.isNotBlank() } ?: "Gemini HTTP ${resp.code}: ${raw.take(240)}")
            }
            val parsed = JSONObject(raw)
            val candidates = parsed.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val out = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val t = parts.getJSONObject(i).optString("text")
                        if (t.isNotEmpty()) out.append(t)
                    }
                    if (out.isNotBlank()) return out.toString()
                }
            }
            error("Gemini API returned no candidate text.")
        }
    }

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
                            put("content", SYSTEM_PROMPT)
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

    companion object {
        private const val SYSTEM_PROMPT =
            """You are GENDEV AI, an expert senior Android systems, APK reverse-engineering, DEX/Smali, Gradle, and security assistant inside GENDEV MANAGER.
Provide concise, accurate, actionable technical guidance (respond in Indonesian or English matching the user's language).
Safety rules: Never advise executing destructive root commands without explicit user review, never invent fake APK signatures, and clearly label any command before the user runs it in Terminal."""
    }
}
