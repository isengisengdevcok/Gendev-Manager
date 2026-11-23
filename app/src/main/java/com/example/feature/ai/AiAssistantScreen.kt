package com.example.feature.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.AiStreamEvent
import com.example.core.common.AppContainer
import com.example.core.data.AiMessageEntity
import com.example.core.data.AiProviderMode
import com.example.core.data.GenDevSettings
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@Composable
fun AiAssistantScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(GenDevSettings())
    val messages by container.database.aiMessageDao().observeMessages().collectAsStateWithLifecycle(emptyList())
    val attachedContext by container.sharedBus.attachedAiContext.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var streamingProvider by remember { mutableStateOf("") }
    var errorNotice by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val quickPrompts = listOf(
        "Audit AndroidManifest.xml ini untuk risiko keamanan dan komponen exported" to "Audit Manifest",
        "Jelaskan kode Smali terlampir langkah demi langkah" to "Jelaskan Smali",
        "Analisa crash log / error Gradle ini dan berikan perbaikannya" to "Diagnosa Crash",
        "Buat template network_security_config.xml Android yang aman" to "XML Template",
        "Jelaskan perintah diagnostik root shell Magisk / KernelSU yang aman" to "Root Cmds"
    )

    fun sendPrompt(rawPrompt: String) {
        val clean = rawPrompt.trim()
        if (clean.isEmpty() || streamingText != null) return
        inputPrompt = ""
        errorNotice = null
        val ctxSnapshot = attachedContext

        scope.launch {
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
                AiProviderMode.FIREBASE_HYBRID -> "Gemini 3.8 Flash (Firebase)..."
                AiProviderMode.GEMINI_API_KEY -> "Gemini Key (${settings.geminiModel})..."
                AiProviderMode.OFFLINE_ONLY -> "Offline Static Analyzer..."
                AiProviderMode.CUSTOM_ENDPOINT -> "Custom Endpoint..."
            }

            container.aiRepository.generateResponseStream(
                prompt = clean,
                attachedContext = ctxSnapshot?.content,
                settings = settings
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
                            Text(
                                text = when (settings.aiProviderMode) {
                                    AiProviderMode.FIREBASE_HYBRID -> "Firebase Gemini 3.8 Flash + Fallback"
                                    AiProviderMode.GEMINI_API_KEY -> "Gemini API Key (${settings.geminiModel})"
                                    AiProviderMode.OFFLINE_ONLY -> "100% Offline Static Engineering Analyzer"
                                    AiProviderMode.CUSTOM_ENDPOINT -> "Custom Endpoint (${settings.customAiModel})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = settings.aiProviderMode == AiProviderMode.GEMINI_API_KEY && settings.geminiApiKey.isNotBlank(),
                            onClick = { showApiKeyDialog = true },
                            leadingIcon = {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(
                                    if (settings.geminiApiKey.isNotBlank()) "Key Aktif" else "API Key",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("ai_api_key_btn")
                        )
                        IconButton(onClick = { scope.launch { container.database.aiMessageDao().clearAll() } }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear AI History")
                        }
                    }
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
            EmptyStatePanel(
                icon = Icons.Filled.AutoAwesome,
                title = "GENDEV AI Siap Digunakan",
                message = "Terhubung otomatis ke Firebase Gemini, atau ketuk tombol 'API Key' di kanan atas untuk memakai Gemini API Key pribadi Anda secara langsung.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
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
                                text = live.ifEmpty { "Thinking..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Tanya soal APK, Smali, Manifest, Gradle, atau Shell...") },
                    maxLines = 3,
                    modifier = Modifier.weight(1f).testTag("ai_prompt_input")
                )
                IconButton(
                    onClick = { sendPrompt(inputPrompt) },
                    enabled = streamingText == null && inputPrompt.isNotBlank(),
                    modifier = Modifier.testTag("ai_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to GENDEV AI", tint = VioletPrimary)
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var selectedMode by remember(settings.aiProviderMode) { mutableStateOf(settings.aiProviderMode) }
        var keyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
        var modelInput by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Konfigurasi GENDEV AI & API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih mode AI. Anda bisa memakai Firebase otomatis atau memasukkan Gemini API Key manual dari Google AI Studio agar berfungsi di APK manapun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AiProviderMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = mode }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                            Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key (AIza...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                    )
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model Gemini (default: gemini-2.5-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val finalMode = if (keyInput.isNotBlank() && selectedMode == AiProviderMode.FIREBASE_HYBRID) {
                                AiProviderMode.GEMINI_API_KEY
                            } else {
                                selectedMode
                            }
                            container.settingsDataStore.updateAiProviderMode(finalMode)
                            container.settingsDataStore.updateGeminiKeyConfig(
                                apiKey = keyInput,
                                model = modelInput,
                                switchToKeyMode = finalMode == AiProviderMode.GEMINI_API_KEY
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
        )
    }
}
