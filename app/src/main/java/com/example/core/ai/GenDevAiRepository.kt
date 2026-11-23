package com.example.core.ai

import android.util.Log
import com.example.core.data.AiProviderMode
import com.example.core.data.GenDevSettings
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.ConnectionPool
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

    private val standardFirebaseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_STANDARD,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 2048
            },
            systemInstruction = content {
                text(SYSTEM_PROMPT)
            }
        )
    }

    private val fastFirebaseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 1280
            },
            systemInstruction = content {
                text(SYSTEM_PROMPT_FAST)
            }
        )
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    fun resolveEffectiveGeminiModel(settings: GenDevSettings): String {
        val raw = settings.geminiModel.trim().removePrefix("models/")
        val normalized = when {
            raw.isBlank() || raw.startsWith("gemini-1.") || raw.startsWith("gemini-2.") -> MODEL_STANDARD
            else -> raw
        }
        return if (settings.aiFastMode && normalized == MODEL_STANDARD) MODEL_FAST else normalized
    }

    fun generateResponseStream(
        prompt: String,
        attachedContext: String?,
        settings: GenDevSettings,
        recentHistory: List<Pair<String, String>> = emptyList()
    ): Flow<AiStreamEvent> = flow {
        val trimmedContext = attachedContext?.trim()?.takeIf { it.isNotEmpty() }
        val contextLimit = if (settings.aiFastMode) 7_000 else 14_000
        val fullPrompt = buildString {
            append(prompt.trim())
            if (trimmedContext != null) {
                append("\n\n--- ATTACHED CONTEXT ---\n")
                append(trimmedContext.take(contextLimit))
            }
        }
        val compactHistory = recentHistory.takeLast(if (settings.aiFastMode) 4 else 8)

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
                            errorMessage = "Gemini API Key belum diisi. Ketuk 'API Key' di atas atau buka Instruksi Key.",
                            fallbackText = offline
                        )
                    )
                } else {
                    val modelId = resolveEffectiveGeminiModel(settings)
                    val label = if (settings.aiFastMode) "Gemini Fast ($modelId)" else "Gemini Key ($modelId)"
                    try {
                        val streamed = streamGeminiApiKeyRest(
                            apiKey = settings.geminiApiKey,
                            modelName = modelId,
                            prompt = fullPrompt,
                            history = compactHistory,
                            fastMode = settings.aiFastMode
                        ) { partial ->
                            emit(AiStreamEvent.Chunk(partial, label))
                        }
                        emit(AiStreamEvent.Completed(streamed, label))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini REST API key call failed", e)
                        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                        emit(
                            AiStreamEvent.Failed(
                                errorMessage = formatFriendlyError(e.localizedMessage),
                                fallbackText = offline
                            )
                        )
                    }
                }
            }

            AiProviderMode.CUSTOM_ENDPOINT -> {
                val allowKeylessLocal = settings.customAiEndpoint.contains("127.0.0.1") ||
                    settings.customAiEndpoint.contains("localhost") ||
                    settings.customAiEndpoint.contains("192.168.")
                if (settings.customAiEndpoint.isBlank() || (!allowKeylessLocal && settings.customAiApiKey.isBlank())) {
                    val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                    emit(
                        AiStreamEvent.Failed(
                            errorMessage = "Endpoint / API Key custom belum lengkap. Cek tombol 'API Key' atau Settings.",
                            fallbackText = offline
                        )
                    )
                } else {
                    val label = "Custom (${settings.customAiModel.ifBlank { "AI" }})"
                    try {
                        val reply = withTimeout(45_000L) {
                            streamOrCallCustomEndpoint(
                                endpoint = settings.customAiEndpoint,
                                modelName = settings.customAiModel,
                                apiKey = settings.customAiApiKey,
                                prompt = fullPrompt,
                                history = compactHistory,
                                fastMode = settings.aiFastMode
                            ) { partial ->
                                emit(AiStreamEvent.Chunk(partial, label))
                            }
                        }
                        emit(AiStreamEvent.Completed(reply, label))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Custom AI endpoint call failed", e)
                        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
                        emit(
                            AiStreamEvent.Failed(
                                errorMessage = formatFriendlyError(e.localizedMessage),
                                fallbackText = offline
                            )
                        )
                    }
                }
            }

            AiProviderMode.FIREBASE_HYBRID -> {
                val activeFirebaseModel = if (settings.aiFastMode) fastFirebaseModel else standardFirebaseModel
                val activeLabel = if (settings.aiFastMode) "Firebase Flash-Lite" else "Firebase Gemini 3.8"
                // If user already has a manual key, fail fast (7s) so it never hangs 60s before falling back
                val firebaseTimeout = if (settings.geminiApiKey.isNotBlank()) 7_000L else 45_000L
                val sb = StringBuilder()
                val firebaseInput = buildString {
                    if (compactHistory.isNotEmpty()) {
                        append("Riwayat singkat:\n")
                        compactHistory.forEach { (role, txt) ->
                            append(if (role == "user") "User: " else "AI: ")
                            append(txt.take(400)).append('\n')
                        }
                        append("\nPertanyaan terbaru:\n")
                    }
                    append(fullPrompt)
                }

                try {
                    withTimeout(firebaseTimeout) {
                        activeFirebaseModel.generateContentStream(firebaseInput).collect { chunk ->
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                sb.append(delta)
                                emit(AiStreamEvent.Chunk(sb.toString(), activeLabel))
                            }
                        }
                    }
                    if (sb.isNotBlank()) {
                        emit(AiStreamEvent.Completed(sb.toString(), activeLabel))
                    } else {
                        emit(
                            handleFirebaseFallback(
                                prompt = prompt,
                                attachedContext = attachedContext,
                                fullPrompt = fullPrompt,
                                history = compactHistory,
                                settings = settings,
                                firebaseError = "Firebase tidak mengembalikan teks."
                            ) { partial, fbLabel -> emit(AiStreamEvent.Chunk(partial, fbLabel)) }
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Firebase AI timed out after ${firebaseTimeout}ms", e)
                    emit(
                        handleFirebaseFallback(
                            prompt = prompt,
                            attachedContext = attachedContext,
                            fullPrompt = fullPrompt,
                            history = compactHistory,
                            settings = settings,
                            firebaseError = "Firebase AI timeout (${firebaseTimeout / 1000}d)."
                        ) { partial, fbLabel -> emit(AiStreamEvent.Chunk(partial, fbLabel)) }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI generation failed", e)
                    emit(
                        handleFirebaseFallback(
                            prompt = prompt,
                            attachedContext = attachedContext,
                            fullPrompt = fullPrompt,
                            history = compactHistory,
                            settings = settings,
                            firebaseError = e.localizedMessage ?: "Firebase AI tidak tersedia."
                        ) { partial, fbLabel -> emit(AiStreamEvent.Chunk(partial, fbLabel)) }
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testGeminiApiKey(apiKey: String, modelName: String): Result<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanKey = apiKey.trim()
                require(cleanKey.isNotEmpty()) { "API Key masih kosong." }
                val cleanModel = modelName.trim().removePrefix("models/").ifEmpty { MODEL_FAST }
                val t0 = System.currentTimeMillis()
                val reqBody = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK only.")))
                            }
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("maxOutputTokens", 12)
                            put("temperature", 0.0)
                        }
                    )
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent")
                    .addHeader("x-goog-api-key", cleanKey)
                    .addHeader("Content-Type", "application/json")
                    .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            JSONObject(raw).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        error(formatFriendlyError(msg ?: "HTTP ${resp.code}"))
                    }
                    Pair("Key valid ($cleanModel)", elapsed)
                }
            }
        }

    private suspend fun handleFirebaseFallback(
        prompt: String,
        attachedContext: String?,
        fullPrompt: String,
        history: List<Pair<String, String>>,
        settings: GenDevSettings,
        firebaseError: String,
        onChunk: suspend (String, String) -> Unit
    ): AiStreamEvent {
        if (settings.geminiApiKey.isNotBlank()) {
            val modelId = resolveEffectiveGeminiModel(settings)
            val label = "Gemini Key ($modelId)"
            try {
                val reply = streamGeminiApiKeyRest(
                    apiKey = settings.geminiApiKey,
                    modelName = modelId,
                    prompt = fullPrompt,
                    history = history,
                    fastMode = settings.aiFastMode
                ) { partial -> onChunk(partial, label) }
                return AiStreamEvent.Completed(reply, label)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini key fallback also failed", e)
            }
        }
        val offline = OfflineEngineeringAnalyzer.analyzeLocally(prompt, attachedContext)
        val hint = if (firebaseError.contains("403") || firebaseError.contains("App Check", ignoreCase = true)) {
            "Firebase App Check belum aktif di APK standalone. Ketuk 'API Key' atau 'Instruksi Key' untuk memakai Google AI Studio Key gratis (respon lebih cepat)."
        } else {
            "$firebaseError Menampilkan analisa statis offline."
        }
        return AiStreamEvent.Failed(errorMessage = hint, fallbackText = offline)
    }

    private suspend fun streamGeminiApiKeyRest(
        apiKey: String,
        modelName: String,
        prompt: String,
        history: List<Pair<String, String>>,
        fastMode: Boolean,
        onChunk: suspend (String) -> Unit
    ): String {
        val cleanModel = modelName.trim().removePrefix("models/").ifEmpty { MODEL_FAST }
        val streamUrl = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse"
        val contentsArr = JSONArray().apply {
            history.forEach { (role, msg) ->
                if (msg.isNotBlank()) {
                    put(
                        JSONObject().apply {
                            put("role", if (role == "user") "user" else "model")
                            put("parts", JSONArray().put(JSONObject().put("text", msg.take(1500))))
                        }
                    )
                }
            }
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }
            )
        }
        val bodyJson = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", if (fastMode) SYSTEM_PROMPT_FAST else SYSTEM_PROMPT))
                )
            )
            put("contents", contentsArr)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", if (fastMode) 0.3 else 0.45)
                    put("maxOutputTokens", if (fastMode) 1536 else 3072)
                }
            )
        }
        val req = Request.Builder()
            .url(streamUrl)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withTimeout(45_000L) {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    val apiErrMsg = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    error(apiErrMsg?.takeIf { it.isNotBlank() } ?: "Gemini HTTP ${resp.code}: ${raw.take(240)}")
                }
                val source = resp.body?.source() ?: error("Empty Gemini response stream.")
                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val delta = extractGeminiTextFromJson(payload)
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onChunk(accumulated.toString())
                    }
                }
                if (accumulated.isNotBlank()) return@use accumulated.toString()
                error("Gemini API tidak mengirim teks kandidat.")
            }
        }
    }

    private fun extractGeminiTextFromJson(rawJson: String): String {
        return runCatching {
            val parsed = JSONObject(rawJson)
            val candidates = parsed.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return ""
            val out = StringBuilder()
            for (i in 0 until parts.length()) {
                val t = parts.getJSONObject(i).optString("text")
                if (t.isNotEmpty()) out.append(t)
            }
            out.toString()
        }.getOrDefault("")
    }

    private suspend fun streamOrCallCustomEndpoint(
        endpoint: String,
        modelName: String,
        apiKey: String,
        prompt: String,
        history: List<Pair<String, String>>,
        fastMode: Boolean,
        onChunk: suspend (String) -> Unit
    ): String {
        val messagesArr = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", if (fastMode) SYSTEM_PROMPT_FAST else SYSTEM_PROMPT)
                }
            )
            history.forEach { (role, msg) ->
                if (msg.isNotBlank()) {
                    put(
                        JSONObject().apply {
                            put("role", if (role == "user") "user" else "assistant")
                            put("content", msg.take(1500))
                        }
                    )
                }
            }
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            )
        }
        val bodyJson = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put("max_tokens", if (fastMode) 1536 else 3072)
            put("temperature", if (fastMode) 0.3 else 0.45)
            put("messages", messagesArr)
        }
        val reqBuilder = Request.Builder()
            .url(endpoint.trim())
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                error("HTTP ${resp.code}: ${raw.take(240)}")
            }
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/event-stream", ignoreCase = true)) {
                val source = resp.body?.source() ?: error("Empty SSE stream")
                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isEmpty()) continue
                    val delta = runCatching {
                        val choices = JSONObject(payload).optJSONArray("choices")
                        choices?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onChunk(accumulated.toString())
                    }
                }
                if (accumulated.isNotBlank()) return accumulated.toString()
            } else {
                val raw = resp.body?.string().orEmpty()
                val parsed = JSONObject(raw)
                val choices = parsed.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    return choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
            error("Respon custom endpoint tidak sesuai format OpenAI chat completions.")
        }
    }

    private fun formatFriendlyError(raw: String?): String {
        val msg = raw.orEmpty()
        return when {
            msg.contains("API_KEY_INVALID", ignoreCase = true) || msg.contains("API key not valid", ignoreCase = true) ->
                "API Key tidak valid. Pastikan salin utuh (AIza...) tanpa spasi — cek menu Instruksi Key."
            msg.contains("429") || msg.contains("quota", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                "Kuota/batas kecepatan key tercapai (429). Coba aktifkan Mode Cepat (Flash-Lite) atau tunggu ±1 menit."
            msg.contains("403") ->
                "Akses ditolak (403). Aktifkan mode Gemini API Key dengan key pribadi Anda dari AI Studio."
            msg.contains("404") || msg.contains("not found", ignoreCase = true) ->
                "Nama model tidak ditemukan. Pilih preset gemini-3.5-flash-lite atau gemini-3.8-flash di dialog API Key."
            msg.isBlank() -> "Permintaan AI gagal. Periksa koneksi internet & API Key Anda."
            else -> msg
        }
    }

    companion object {
        const val MODEL_STANDARD = "gemini-3.8-flash"
        const val MODEL_FAST = "gemini-3.5-flash-lite"

        private const val SYSTEM_PROMPT =
            """You are GENDEV AI, an expert senior Android systems, APK reverse-engineering, DEX/Smali, Gradle, and security assistant inside GENDEV MANAGER.
Provide concise, accurate, actionable technical guidance (respond in Indonesian or English matching the user's language).
Safety rules: Never advise executing destructive root commands without explicit user review, never invent fake APK signatures, and clearly label any command before the user runs it in Terminal."""

        private const val SYSTEM_PROMPT_FAST =
            """You are GENDEV AI inside GENDEV MANAGER. Answer directly, concisely, and accurately in the user's language (Indonesian/English) using short bullet points and exact code/Smali/shell snippets without long intros."""
    }
}
