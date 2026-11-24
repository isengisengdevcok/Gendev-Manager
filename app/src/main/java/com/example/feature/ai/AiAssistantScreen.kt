package com.example.feature.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.GenDevAiRepository
import com.example.core.common.AppContainer
import com.example.core.common.AppLogLevel
import com.example.core.common.AppLogStore
import com.example.core.data.AiMessageEntity
import com.example.core.data.AiProviderMode
import com.example.core.data.GenDevSettings
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AiAssistantScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(GenDevSettings())
    val messages by container.database.aiMessageDao().observeMessages().collectAsStateWithLifecycle(emptyList())
    val attachedContext by container.sharedBus.attachedAiContext.collectAsStateWithLifecycle()
    val appLogs by AppLogStore.logs.collectAsStateWithLifecycle()
    val appErrorCount = remember(appLogs) {
        appLogs.count { it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH }
    }

    var inputPrompt by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var streamingProvider by remember { mutableStateOf("") }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var errorNotice by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showKeyGuideDialog by remember { mutableStateOf(false) }
    var showSourceLogDialog by remember { mutableStateOf(false) }
    var includeSourceLogs by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val quickPrompts = listOf(
        "Analisa Source Log error & Force Close aplikasi ini, identifikasi akar masalah per file/modul, dan buat daftar perbaikan pengembangan lanjutan" to "Analisa Error App",
        "Audit AndroidManifest.xml ini untuk risiko keamanan dan komponen exported" to "Audit Manifest",
        "Jelaskan kode Smali terlampir langkah demi langkah" to "Jelaskan Smali",
        "Analisa crash log / error Gradle ini dan berikan perbaikannya" to "Diagnosa Crash",
        "Buat template network_security_config.xml Android yang aman" to "XML Template",
        "Jelaskan perintah diagnostik root shell Magisk / KernelSU yang aman" to "Root Cmds"
    )

    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText != null) {
            val targetIdx = (messages.size + if (streamingText != null) 0 else -1).coerceAtLeast(0)
            runCatching { listState.animateScrollToItem(targetIdx) }
        }
    }

    fun openAiStudioKeyUrl() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
            context.startActivity(intent)
        }.onFailure {
            errorNotice = "Buka browser ke https://aistudio.google.com/apikey untuk membuat API key gratis."
        }
    }

    fun readClipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
    }

    fun sendPrompt(rawPrompt: String) {
        val clean = rawPrompt.trim()
        if (clean.isEmpty() || streamingText != null) return
        inputPrompt = ""
        errorNotice = null
        val ctxSnapshot = attachedContext
        val recentHistory = messages.takeLast(6).map { it.role to it.content }

        activeJob = scope.launch {
            container.database.aiMessageDao().insert(
                AiMessageEntity(
                    role = "user",
                    content = clean,
                    providerTag = "User",
                    attachedContextLabel = ctxSnapshot?.label
                )
            )
            streamingText = ""
            streamingProvider = when (settings.aiProviderMode) {
                AiProviderMode.FIREBASE_HYBRID ->
                    if (settings.aiFastMode) "Firebase Flash-Lite..." else "Firebase Gemini 3.8..."
                AiProviderMode.GEMINI_API_KEY ->
                    "Gemini (${container.aiRepository.resolveEffectiveGeminiModel(settings)})..."
                AiProviderMode.OFFLINE_ONLY -> "Offline Static Analyzer..."
                AiProviderMode.CUSTOM_ENDPOINT -> "Custom (${settings.customAiModel})..."
            }

            try {
                container.aiRepository.generateResponseStream(
                    prompt = clean,
                    attachedContext = ctxSnapshot?.content,
                    settings = settings,
                    recentHistory = recentHistory,
                    includeSourceLogs = includeSourceLogs
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            streamingText = event.accumulatedText
                            streamingProvider = event.providerLabel
                        }
                        is AiStreamEvent.Completed -> {
                            streamingText = null
                            container.database.aiMessageDao().insert(
                                AiMessageEntity(
                                    role = "assistant",
                                    content = event.fullText,
                                    providerTag = event.providerLabel
                                )
                            )
                        }
                        is AiStreamEvent.Failed -> {
                            streamingText = null
                            errorNotice = event.errorMessage
                            container.database.aiMessageDao().insert(
                                AiMessageEntity(
                                    role = "assistant",
                                    content = event.fallbackText,
                                    providerTag = "Offline Fallback"
                                )
                            )
                        }
                    }
                }
            } finally {
                streamingText = null
                activeJob = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VioletPrimary)
                        Column {
                            Text("GENDEV AI Studio", style = MaterialTheme.typography.titleLarge)
                            val speedTag = if (settings.aiFastMode) " • Cepat" else " • Mendalam"
                            Text(
                                text = when (settings.aiProviderMode) {
                                    AiProviderMode.FIREBASE_HYBRID -> "Hybrid Firebase$speedTag"
                                    AiProviderMode.GEMINI_API_KEY ->
                                        "${container.aiRepository.resolveEffectiveGeminiModel(settings)}$speedTag"
                                    AiProviderMode.OFFLINE_ONLY -> "Offline Analyzer (0 ms)"
                                    AiProviderMode.CUSTOM_ENDPOINT -> "Custom (${settings.customAiModel})$speedTag"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { scope.launch { container.database.aiMessageDao().clearAll() } }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear AI History")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = settings.aiFastMode,
                        onClick = { scope.launch { container.settingsDataStore.setAiFastMode(!settings.aiFastMode) } },
                        leadingIcon = {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (settings.aiFastMode) "Mode Cepat ON" else "Mode Cepat", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("ai_fast_mode_chip")
                    )
                    FilterChip(
                        selected = settings.aiProviderMode == AiProviderMode.GEMINI_API_KEY && settings.geminiApiKey.isNotBlank(),
                        onClick = { showApiKeyDialog = true },
                        leadingIcon = {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                if (settings.geminiApiKey.isNotBlank()) "Key Aktif" else "Pasang API Key",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("ai_api_key_btn")
                    )
                    AssistChip(
                        onClick = { showKeyGuideDialog = true },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Instruksi Key", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("ai_key_guide_btn")
                    )
                    AssistChip(
                        onClick = { showSourceLogDialog = true },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = if (appErrorCount > 0) StatusDanger else CyanSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            val badge = if (appErrorCount > 0) "$appErrorCount Err" else "${appLogs.size}"
                            Text("Source Log ($badge)", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.testTag("ai_source_log_btn")
                    )
                    FilterChip(
                        selected = includeSourceLogs,
                        onClick = { includeSourceLogs = !includeSourceLogs },
                        label = {
                            Text(
                                if (includeSourceLogs) "Auto-Log AI ON" else "Auto-Log OFF",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("ai_autolog_toggle_chip")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.forEach { (promptText, chipLabel) ->
                        AssistChip(
                            onClick = { sendPrompt(promptText) },
                            label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                attachedContext?.let { ctx ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanSecondary.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = CyanSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Attached: ${ctx.label} (${ctx.content.length} chars)",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Detach Context",
                            modifier = Modifier.size(16.dp).clickable { container.sharedBus.clearAiContext() }
                        )
                    }
                }

                errorNotice?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusWarning.copy(alpha = 0.14f))
                            .clickable { showApiKeyDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = err, style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                    }
                }

                if (streamingText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (messages.isEmpty() && streamingText == null) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GenDevGlassCard(accentBorder = VioletPrimary.copy(alpha = 0.5f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VioletPrimary)
                            Text("GENDEV AI Mode Cepat & Multi-Key", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sudah dioptimalkan dengan SSE streaming real-time & model Gemini 3.x Flash-Lite agar jawaban langsung mengalir cepat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showApiKeyDialog = true }) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Atur API Key")
                            }
                            OutlinedButton(onClick = { showKeyGuideDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Instruksi Key")
                            }
                        }
                    }
                }

                item {
                    GenDevGlassCard {
                        Text("Instruksi Singkat Pilihan Key", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        KeyGuideSnippet(
                            title = "1. Google AI Studio Key (Gratis & Tercepat)",
                            detail = "Buat key gratis di aistudio.google.com/apikey (awalan AIza...) → tempel lewat tombol 'Pasang API Key' → otomatis aktif streaming."
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        KeyGuideSnippet(
                            title = "2. Key Alternatif (Groq / OpenRouter / Ollama)",
                            detail = "Pilih mode Custom Provider di dialog API Key untuk memakai key gsk_... (Groq ultra cepat), sk-or-... (OpenRouter), atau LLM lokal Ollama tanpa key."
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        KeyGuideSnippet(
                            title = "3. Mode Offline Analyzer (0 ms, Tanpa Key)",
                            detail = "Untuk audit AndroidManifest, Smali, atau Logcat secara instan tanpa internet/key, pilih mode Offline Engineering Analyzer."
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    GenDevGlassCard(
                        accentBorder = if (isUser) CyanSecondary.copy(alpha = 0.5f) else VioletPrimary.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadgePill(
                                label = if (isUser) "You" else msg.providerTag,
                                color = if (isUser) CyanSecondary else EmeraldTertiary
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("GENDEV AI", msg.content))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Response", modifier = Modifier.size(16.dp))
                            }
                        }
                        msg.attachedContextLabel?.let { label ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Context: $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                streamingText?.let { live ->
                    item {
                        GenDevGlassCard(accentBorder = VioletPrimary) {
                            StatusBadgePill(label = streamingProvider, color = VioletPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = live.ifEmpty { "Menghubungkan & streaming respons..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Tanya soal APK, Smali, Manifest, Gradle, atau Shell...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && messages.isNotEmpty()) {
                                scope.launch {
                                    delay(160)
                                    runCatching { listState.animateScrollToItem(messages.lastIndex) }
                                }
                            }
                        }
                        .testTag("ai_prompt_input")
                )
                if (streamingText != null) {
                    IconButton(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = null
                            streamingText = null
                        },
                        modifier = Modifier.testTag("ai_stop_btn")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop AI", tint = StatusDanger)
                    }
                } else {
                    IconButton(
                        onClick = { sendPrompt(inputPrompt) },
                        enabled = inputPrompt.isNotBlank(),
                        modifier = Modifier.testTag("ai_send_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to GENDEV AI", tint = VioletPrimary)
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var selectedMode by remember(settings.aiProviderMode) { mutableStateOf(settings.aiProviderMode) }
        var fastModeInput by remember(settings.aiFastMode) { mutableStateOf(settings.aiFastMode) }
        var keyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
        var modelInput by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
        var customEndpointInput by remember(settings.customAiEndpoint) { mutableStateOf(settings.customAiEndpoint) }
        var customModelInput by remember(settings.customAiModel) { mutableStateOf(settings.customAiModel) }
        var customKeyInput by remember(settings.customAiApiKey) { mutableStateOf(settings.customAiApiKey) }
        var testStatus by remember { mutableStateOf<String?>(null) }
        var isTesting by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { showApiKeyDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Konfigurasi GENDEV AI & Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { showApiKeyDialog = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }

                        HorizontalDivider()

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiProviderMode.entries.forEach { mode ->
                                    val shortName = when (mode) {
                                        AiProviderMode.FIREBASE_HYBRID -> "Hybrid"
                                        AiProviderMode.GEMINI_API_KEY -> "Gemini Key"
                                        AiProviderMode.CUSTOM_ENDPOINT -> "Custom Key"
                                        AiProviderMode.OFFLINE_ONLY -> "Offline"
                                    }
                                    FilterChip(
                                        selected = selectedMode == mode,
                                        onClick = { selectedMode = mode },
                                        label = { Text(shortName, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Text(
                                text = selectedMode.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedMode == AiProviderMode.GEMINI_API_KEY || selectedMode == AiProviderMode.FIREBASE_HYBRID) {
                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { keyInput = it; testStatus = null },
                                    label = { Text("Gemini API Key (AIza...)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val clip = readClipboardText()
                                            if (clip.isNotEmpty()) {
                                                keyInput = clip
                                                testStatus = "Key ditempel dari clipboard."
                                            }
                                        }) {
                                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste Key")
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isTesting = true
                                            testStatus = "Menguji koneksi..."
                                            scope.launch {
                                                val testModel = if (fastModeInput && modelInput == GenDevAiRepository.MODEL_STANDARD) {
                                                    GenDevAiRepository.MODEL_FAST
                                                } else {
                                                    modelInput
                                                }
                                                container.aiRepository.testGeminiApiKey(keyInput, testModel)
                                                    .onSuccess { (msg, ms) ->
                                                        testStatus = "✓ $msg • ${ms} ms"
                                                    }
                                                    .onFailure { err ->
                                                        testStatus = "✗ ${err.localizedMessage}"
                                                    }
                                                isTesting = false
                                            }
                                        },
                                        enabled = !isTesting && keyInput.isNotBlank(),
                                        modifier = Modifier.testTag("test_ai_key_btn")
                                    ) {
                                        Text(if (isTesting) "Menguji..." else "Tes Key", style = MaterialTheme.typography.labelSmall)
                                    }
                                    AssistChip(
                                        onClick = { openAiStudioKeyUrl() },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        label = { Text("Ambil Key", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    AssistChip(
                                        onClick = { showKeyGuideDialog = true },
                                        label = { Text("Instruksi", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }

                                testStatus?.let { st ->
                                    Text(
                                        text = st,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (st.startsWith("✓")) StatusSuccess else StatusWarning
                                    )
                                }

                                Text("Preset Model Cepat:", style = MaterialTheme.typography.labelSmall, color = CyanSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        GenDevAiRepository.MODEL_FAST to "Flash-Lite (Tercepat)",
                                        GenDevAiRepository.MODEL_STANDARD to "3.8 Flash",
                                        "gemini-4-argon" to "4 Argon"
                                    ).forEach { (mId, mLabel) ->
                                        FilterChip(
                                            selected = modelInput == mId,
                                            onClick = { modelInput = mId },
                                            label = { Text(mLabel, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = modelInput,
                                    onValueChange = { modelInput = it },
                                    label = { Text("Model Gemini") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (selectedMode == AiProviderMode.CUSTOM_ENDPOINT) {
                                Text("Preset Provider Cepat:", style = MaterialTheme.typography.labelSmall, color = CyanSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            customEndpointInput = "https://api.groq.com/openai/v1/chat/completions"
                                            customModelInput = "llama-3.3-70b-versatile"
                                        },
                                        label = { Text("Groq (gsk_)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            customEndpointInput = "https://openrouter.ai/api/v1/chat/completions"
                                            customModelInput = "meta-llama/llama-3.3-70b-instruct:free"
                                        },
                                        label = { Text("OpenRouter", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            customEndpointInput = "http://127.0.0.1:11434/v1/chat/completions"
                                            customModelInput = "qwen2.5-coder:7b"
                                        },
                                        label = { Text("Ollama Lokal", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                OutlinedTextField(
                                    value = customKeyInput,
                                    onValueChange = { customKeyInput = it },
                                    label = { Text("API Key (kosongkan jika Ollama lokal)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = customEndpointInput,
                                    onValueChange = { customEndpointInput = it },
                                    label = { Text("Endpoint URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { customModelInput = it },
                                    label = { Text("Nama Model") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mode Cepat (Streaming & Flash-Lite)", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Respon kata-per-kata dengan latensi minimal.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = fastModeInput, onCheckedChange = { fastModeInput = it })
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showApiKeyDialog = false }) { Text("Batal") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val finalMode = if (keyInput.isNotBlank() && selectedMode == AiProviderMode.FIREBASE_HYBRID) {
                                            AiProviderMode.GEMINI_API_KEY
                                        } else {
                                            selectedMode
                                        }
                                        container.settingsDataStore.setAiFastMode(fastModeInput)
                                        container.settingsDataStore.updateAiProviderMode(finalMode)
                                        container.settingsDataStore.updateGeminiKeyConfig(
                                            apiKey = keyInput,
                                            model = modelInput,
                                            switchToKeyMode = finalMode == AiProviderMode.GEMINI_API_KEY
                                        )
                                        container.settingsDataStore.updateCustomAiConfig(
                                            endpoint = customEndpointInput,
                                            model = customModelInput,
                                            apiKey = customKeyInput
                                        )
                                        errorNotice = null
                                        showApiKeyDialog = false
                                    }
                                },
                                modifier = Modifier.testTag("save_ai_key_btn")
                            ) {
                                Text("Simpan & Aktifkan")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showKeyGuideDialog) {
        Dialog(
            onDismissRequest = { showKeyGuideDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Instruksi & Panduan API Key GENDEV AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showKeyGuideDialog = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KeyGuideSnippet(
                                title = "1. Google AI Studio Key (Paling Direkomendasikan)",
                                detail = "• Buka https://aistudio.google.com/apikey dan login akun Google.\n" +
                                    "• Ketuk 'Create API key' lalu salin key berawalan AIza...\n" +
                                    "• Masuk ke tombol 'Pasang API Key' → tempel key → pilih preset 'Flash-Lite (Tercepat)' → tekan 'Tes Key' lalu 'Simpan & Aktifkan'."
                            )
                            KeyGuideSnippet(
                                title = "2. Groq Key (Respon Super Cepat / Gratis)",
                                detail = "• Ambil key di console.groq.com/keys (awalan gsk_...).\n" +
                                    "• Pilih mode 'Custom Key' → ketuk preset 'Groq (gsk_)' → tempel key Anda."
                            )
                            KeyGuideSnippet(
                                title = "3. OpenRouter / LLM Lokal Ollama (Termux/PC)",
                                detail = "• OpenRouter: ambil key sk-or-... di openrouter.ai/keys, pilih preset 'OpenRouter'.\n" +
                                    "• Ollama Lokal: jalankan Ollama di HP/PC lalu pilih preset 'Ollama Lokal' (tanpa API key)."
                            )
                            KeyGuideSnippet(
                                title = "4. Tips Kecepatan & Mengatasi Error Key",
                                detail = "• Aktifkan 'Mode Cepat ON' agar AI memakai streaming SSE + gemini-3.5-flash-lite.\n" +
                                    "• Error 403 / App Check: pindahkan mode dari Hybrid ke 'Gemini Key'.\n" +
                                    "• Error 400 API_KEY_INVALID: pastikan tidak ada spasi di awal/akhir key.\n" +
                                    "• Error 429 Quota: ganti model ke Flash-Lite atau tunggu 1 menit."
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { openAiStudioKeyUrl() }) { Text("Buka AI Studio") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { showKeyGuideDialog = false }) { Text("Tutup") }
                        }
                    }
                }
            }
        }
    }

    if (showSourceLogDialog) {
        var logFilter by remember { mutableStateOf("ALL") }
        val filteredLogs = remember(appLogs, logFilter) {
            when (logFilter) {
                "ERR" -> appLogs.filter { it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH }
                "WARN" -> appLogs.filter { it.level == AppLogLevel.WARN || it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH }
                else -> appLogs
            }.reversed()
        }

        Dialog(
            onDismissRequest = { showSourceLogDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Source Log & Crash Recorder",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "$appErrorCount error/crash • ${appLogs.size} total catatan (tersimpan otomatis)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (appErrorCount > 0) StatusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showSourceLogDialog = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ALL" to "Semua", "ERR" to "Error/FC", "WARN" to "Warn+Err").forEach { (key, label) ->
                                FilterChip(
                                    selected = logFilter == key,
                                    onClick = { logFilter = key },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        HorizontalDivider()

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (filteredLogs.isEmpty()) {
                                item {
                                    Text(
                                        "Belum ada error atau log yang tercatat pada filter ini.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            } else {
                                items(filteredLogs, key = { it.id }) { entry ->
                                    val levelColor = when (entry.level) {
                                        AppLogLevel.CRASH, AppLogLevel.ERROR -> StatusDanger
                                        AppLogLevel.WARN -> StatusWarning
                                        AppLogLevel.INFO -> CyanSecondary
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StatusBadgePill(label = "${entry.level.tag} • ${entry.source}", color = levelColor)
                                            Text(
                                                entry.formattedTime,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            entry.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMonoFontFamily
                                        )
                                        entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                stack.take(500),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = StatusWarning
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { AppLogStore.clearLogs() }) {
                                Text("Hapus Log")
                            }
                            OutlinedButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("GENDEV Source Log", AppLogStore.buildAiDiagnosticSnapshot()))
                            }) {
                                Text("Salin")
                            }
                            OutlinedButton(onClick = {
                                container.sharedBus.attachContextToAi(
                                    "App Source Log (${appLogs.size})",
                                    AppLogStore.buildAiDiagnosticSnapshot()
                                )
                                showSourceLogDialog = false
                            }) {
                                Text("Lampirkan")
                            }
                            Button(
                                onClick = {
                                    container.sharedBus.attachContextToAi(
                                        "App Source Log (${appLogs.size})",
                                        AppLogStore.buildAiDiagnosticSnapshot()
                                    )
                                    showSourceLogDialog = false
                                    sendPrompt("Analisa Source Log & Force Close GENDEV MANAGER terlampir, jelaskan akar masalah dan rencana perbaikan kodenya.")
                                },
                                modifier = Modifier.testTag("analyze_source_log_ai_btn")
                            ) {
                                Text("Analisa AI")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyGuideSnippet(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = CyanSecondary)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
