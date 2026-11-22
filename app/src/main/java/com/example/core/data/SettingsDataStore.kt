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
        "Hybrid (Firebase Gemini + Offline Fallback)",
        "Uses Gemini 3.8 Flash via Firebase AI Logic when online, and static engineering analysis offline."
    ),
    OFFLINE_ONLY(
        "Offline Engineering Analyzer Only",
        "Zero network calls. Uses deterministic static analysis for Manifest, Smali, Crash Logs, and Templates."
    ),
    CUSTOM_ENDPOINT(
        "Custom External Provider (User Key)",
        "Uses your configured OpenAI-compatible API endpoint and personal key, with offline fallback."
    )
}

data class GenDevSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK_CYBER,
    val accentOption: AppAccentOption = AppAccentOption.VIOLET_CYAN,
    val dualPaneEnabled: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val autoBackupBeforeEdit: Boolean = true,
    val aiProviderMode: AiProviderMode = AiProviderMode.FIREBASE_HYBRID,
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
        val AI_MODE = stringPreferencesKey("ai_mode")
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
            aiProviderMode = prefs[Keys.AI_MODE]?.let { runCatching { AiProviderMode.valueOf(it) }.getOrNull() }
                ?: AiProviderMode.FIREBASE_HYBRID,
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

    suspend fun updateAiProviderMode(mode: AiProviderMode) {
        context.settingsDataStore.edit { it[Keys.AI_MODE] = mode.name }
    }

    suspend fun updateCustomAiConfig(endpoint: String, model: String, apiKey: String) {
        context.settingsDataStore.edit {
            it[Keys.CUSTOM_AI_ENDPOINT] = endpoint.trim()
            it[Keys.CUSTOM_AI_MODEL] = model.trim()
            it[Keys.CUSTOM_AI_KEY] = apiKey.trim()
        }
    }
}
