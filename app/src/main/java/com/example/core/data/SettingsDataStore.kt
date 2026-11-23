package com.example.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.ui.theme.AppAccentOption
import com.example.core.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "gendev_settings")

enum class AiProviderMode(val label: String, val description: String) {
    FIREBASE_HYBRID(
        "Hybrid (Firebase Gemini + Auto Fallback)",
        "Coba Firebase AI Logic cepat; jika App Check gagal/lambat, otomatis pindah ke Gemini Key / Offline."
    ),
    GEMINI_API_KEY(
        "Gemini API Key (Langsung & Tercepat)",
        "Pakai Gemini API Key Google AI Studio langsung via streaming HTTPS tanpa menunggu App Check."
    ),
    CUSTOM_ENDPOINT(
        "Custom Provider (Groq / OpenRouter / Ollama)",
        "Pakai endpoint OpenAI-compatible & API key pilihan Anda sendiri."
    ),
    OFFLINE_ONLY(
        "Offline Engineering Analyzer (Instan 0 ms)",
        "Tanpa internet & tanpa key. Analisa statis Manifest, Smali, Crash Log, & Template."
    )
}

enum class FileDisplaySize(val label: String, val scaleFactor: Float) {
    COMPACT("Kecil / Padat", 0.92f),
    STANDARD("Besar (Default)", 1.15f),
    LARGE("Ekstra Besar", 1.32f)
}

data class GenDevSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK_CYBER,
    val accentOption: AppAccentOption = AppAccentOption.VIOLET_CYAN,
    val dualPaneEnabled: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val autoBackupBeforeEdit: Boolean = true,
    val fileDisplaySize: FileDisplaySize = FileDisplaySize.STANDARD,
    val rootRememberedGranted: Boolean = false,
    val aiProviderMode: AiProviderMode = AiProviderMode.FIREBASE_HYBRID,
    val aiFastMode: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val customAiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val customAiModel: String = "gpt-4o-mini",
    val customAiApiKey: String = ""
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_OPTION = stringPreferencesKey("accent_option")
        val DUAL_PANE = booleanPreferencesKey("dual_pane")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val FILE_DISPLAY_SIZE = stringPreferencesKey("file_display_size")
        val ROOT_REMEMBERED_GRANTED = booleanPreferencesKey("root_remembered_granted")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val AI_FAST_MODE = booleanPreferencesKey("ai_fast_mode")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CUSTOM_AI_ENDPOINT = stringPreferencesKey("custom_ai_endpoint")
        val CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
        val CUSTOM_AI_KEY = stringPreferencesKey("custom_ai_key")
    }

    val settingsFlow: Flow<GenDevSettings> = context.settingsDataStore.data.map { prefs ->
        GenDevSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK_CYBER,
            accentOption = prefs[Keys.ACCENT_OPTION]?.let { runCatching { AppAccentOption.valueOf(it) }.getOrNull() }
                ?: AppAccentOption.VIOLET_CYAN,
            dualPaneEnabled = prefs[Keys.DUAL_PANE] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            autoBackupBeforeEdit = prefs[Keys.AUTO_BACKUP] ?: true,
            fileDisplaySize = prefs[Keys.FILE_DISPLAY_SIZE]?.let { runCatching { FileDisplaySize.valueOf(it) }.getOrNull() }
                ?: FileDisplaySize.STANDARD,
            rootRememberedGranted = prefs[Keys.ROOT_REMEMBERED_GRANTED] ?: false,
            aiProviderMode = prefs[Keys.AI_MODE]?.let { runCatching { AiProviderMode.valueOf(it) }.getOrNull() }
                ?: AiProviderMode.FIREBASE_HYBRID,
            aiFastMode = prefs[Keys.AI_FAST_MODE] ?: true,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            geminiModel = prefs[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: "gemini-3.8-flash",
            customAiEndpoint = prefs[Keys.CUSTOM_AI_ENDPOINT] ?: "https://api.openai.com/v1/chat/completions",
            customAiModel = prefs[Keys.CUSTOM_AI_MODEL] ?: "gpt-4o-mini",
            customAiApiKey = prefs[Keys.CUSTOM_AI_KEY] ?: ""
        )
    }

    suspend fun updateTheme(mode: AppThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateAccent(accent: AppAccentOption) {
        context.settingsDataStore.edit { it[Keys.ACCENT_OPTION] = accent.name }
    }

    suspend fun setDualPane(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DUAL_PANE] = enabled }
    }

    suspend fun setShowHidden(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setFileDisplaySize(size: FileDisplaySize) {
        context.settingsDataStore.edit { it[Keys.FILE_DISPLAY_SIZE] = size.name }
    }

    suspend fun setRootRememberedGranted(granted: Boolean) {
        context.settingsDataStore.edit { it[Keys.ROOT_REMEMBERED_GRANTED] = granted }
    }

    suspend fun updateAiProviderMode(mode: AiProviderMode) {
        context.settingsDataStore.edit { it[Keys.AI_MODE] = mode.name }
    }

    suspend fun setAiFastMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AI_FAST_MODE] = enabled }
    }

    suspend fun updateGeminiKeyConfig(apiKey: String, model: String = "gemini-3.8-flash", switchToKeyMode: Boolean = true) {
        context.settingsDataStore.edit {
            it[Keys.GEMINI_API_KEY] = apiKey.trim()
            it[Keys.GEMINI_MODEL] = model.trim().ifEmpty { "gemini-3.8-flash" }
            if (switchToKeyMode && apiKey.isNotBlank()) {
                it[Keys.AI_MODE] = AiProviderMode.GEMINI_API_KEY.name
            }
        }
    }

    suspend fun updateCustomAiConfig(endpoint: String, model: String, apiKey: String) {
        context.settingsDataStore.edit {
            it[Keys.CUSTOM_AI_ENDPOINT] = endpoint.trim()
            it[Keys.CUSTOM_AI_MODEL] = model.trim()
            it[Keys.CUSTOM_AI_KEY] = apiKey.trim()
        }
    }
}
