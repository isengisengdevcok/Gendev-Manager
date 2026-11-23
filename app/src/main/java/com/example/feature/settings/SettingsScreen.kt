package com.example.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.core.common.AppContainer
import com.example.core.data.AiProviderMode
import com.example.core.data.FileDisplaySize
import com.example.core.data.GenDevSettings
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.KeyValueRow
import com.example.core.ui.theme.AppAccentOption
import com.example.core.ui.theme.AppThemeMode
import com.example.core.ui.theme.CyanSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(GenDevSettings())

    var geminiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var customEndpoint by remember { mutableStateOf(settings.customAiEndpoint) }
    var customModel by remember { mutableStateOf(settings.customAiModel) }
    var customKey by remember { mutableStateOf(settings.customAiApiKey) }
    var savedBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        settings.geminiApiKey,
        settings.geminiModel,
        settings.customAiEndpoint,
        settings.customAiModel,
        settings.customAiApiKey
    ) {
        geminiKey = settings.geminiApiKey
        geminiModel = settings.geminiModel
        customEndpoint = settings.customAiEndpoint
        customModel = settings.customAiModel
        customKey = settings.customAiApiKey
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("GENDEV Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "100% Free & Open Source • Zero Ads • Zero Tracking",
                style = MaterialTheme.typography.bodySmall,
                color = CyanSecondary
            )
        }

        item {
            GenDevGlassCard {
                Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                AppThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { container.settingsDataStore.updateTheme(mode) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { container.settingsDataStore.updateTheme(mode) } }
                        )
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Accent Palette", style = MaterialTheme.typography.titleSmall)
                AppAccentOption.entries.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { container.settingsDataStore.updateAccent(acc) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.accentOption == acc,
                            onClick = { scope.launch { container.settingsDataStore.updateAccent(acc) } }
                        )
                        Text(acc.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            GenDevGlassCard {
                Text("File Manager & Tampilan Daftar", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                ToggleRow(
                    title = "Dual-Pane View by Default",
                    subtitle = "Panel Kiri (Biasa) & Panel Kanan (Root) berdampingan",
                    checked = settings.dualPaneEnabled,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setDualPane(it) } }
                )
                ToggleRow(
                    title = "Show Hidden Dotfiles",
                    subtitle = "Tampilkan .git, .nomedia, dan file tersembunyi",
                    checked = settings.showHiddenFiles,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setShowHidden(it) } }
                )
                ToggleRow(
                    title = "Automatic Backup Before Edits",
                    subtitle = "Salin file asli ke Backups/ sebelum menyimpan atau menghapus",
                    checked = settings.autoBackupBeforeEdit,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setAutoBackup(it) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ukuran Tampilan File", style = MaterialTheme.typography.titleSmall)
                FileDisplaySize.entries.forEach { sz ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { container.settingsDataStore.setFileDisplaySize(sz) } }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.fileDisplaySize == sz,
                            onClick = { scope.launch { container.settingsDataStore.setFileDisplaySize(sz) } }
                        )
                        Text(sz.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            GenDevGlassCard {
                Text("GENDEV AI Engine (Firebase + Manual Key)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Gunakan Firebase AI Logic otomatis atau masukkan Gemini API Key pribadi Anda untuk APK standalone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AiProviderMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { container.settingsDataStore.updateAiProviderMode(mode) } }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = settings.aiProviderMode == mode,
                            onClick = { scope.launch { container.settingsDataStore.updateAiProviderMode(mode) } }
                        )
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (settings.aiProviderMode == AiProviderMode.GEMINI_API_KEY || settings.aiProviderMode == AiProviderMode.FIREBASE_HYBRID) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("Gemini API Key (AIza...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = geminiModel,
                        onValueChange = { geminiModel = it },
                        label = { Text("Gemini Model (contoh: gemini-2.5-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            container.settingsDataStore.updateGeminiKeyConfig(
                                apiKey = geminiKey,
                                model = geminiModel,
                                switchToKeyMode = geminiKey.isNotBlank()
                            )
                            savedBanner = "Gemini API Key disimpan lokal & siap digunakan."
                        }
                    }) {
                        Text("Simpan Gemini Key")
                    }
                }

                if (settings.aiProviderMode == AiProviderMode.CUSTOM_ENDPOINT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customEndpoint,
                        onValueChange = { customEndpoint = it },
                        label = { Text("OpenAI-Compatible Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text("Model Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customKey,
                        onValueChange = { customKey = it },
                        label = { Text("User API Key (stored locally)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            container.settingsDataStore.updateCustomAiConfig(customEndpoint, customModel, customKey)
                            savedBanner = "Saved custom AI endpoint settings locally."
                        }
                    }) {
                        Text("Save Provider Config")
                    }
                }

                savedBanner?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CyanSecondary)
                }
            }
        }

        item {
            GenDevGlassCard {
                Text("Project Identity & Build", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                KeyValueRow("Application", "GENDEV MANAGER")
                KeyValueRow("Application ID", BuildConfig.APPLICATION_ID)
                KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValueRow("License", "Free & Open Source (MIT / Apache-2.0 compatible)")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
