package app.scanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prismDataStore: DataStore<Preferences> by preferencesDataStore(name = "prism_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    OBSIDIAN_DARK("Obsidian Dark"),
    ALABASTER_LIGHT("Alabaster Light")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.OBSIDIAN_DARK,
    val saveHistory: Boolean = true,
    val hapticFeedback: Boolean = true,
    val soundBeep: Boolean = false,
    val offlineSafetyChecks: Boolean = true,
    val autoOpenSafeUrls: Boolean = false,
    val batchModeDefault: Boolean = false,
    val manualShutterDefault: Boolean = false,
    val screenQrAutoEncloseAll: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_BEEP = booleanPreferencesKey("sound_beep")
        val OFFLINE_SAFETY = booleanPreferencesKey("offline_safety")
        val AUTO_OPEN_SAFE_URLS = booleanPreferencesKey("auto_open_safe_urls")
        val BATCH_MODE_DEFAULT = booleanPreferencesKey("batch_mode_default")
        val MANUAL_SHUTTER_DEFAULT = booleanPreferencesKey("manual_shutter_default")
        val SCREEN_QR_ENCLOSE_ALL = booleanPreferencesKey("screen_qr_enclose_all")
    }

    val settings: Flow<AppSettings> = context.prismDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.OBSIDIAN_DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.OBSIDIAN_DARK)
        AppSettings(
            themeMode = themeMode,
            saveHistory = prefs[Keys.SAVE_HISTORY] ?: true,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            soundBeep = prefs[Keys.SOUND_BEEP] ?: false,
            offlineSafetyChecks = prefs[Keys.OFFLINE_SAFETY] ?: true,
            autoOpenSafeUrls = prefs[Keys.AUTO_OPEN_SAFE_URLS] ?: false,
            batchModeDefault = prefs[Keys.BATCH_MODE_DEFAULT] ?: false,
            manualShutterDefault = prefs[Keys.MANUAL_SHUTTER_DEFAULT] ?: false,
            screenQrAutoEncloseAll = prefs[Keys.SCREEN_QR_ENCLOSE_ALL] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prismDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSaveHistory(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.SAVE_HISTORY] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setSoundBeep(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.SOUND_BEEP] = enabled }
    }

    suspend fun setOfflineSafetyChecks(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.OFFLINE_SAFETY] = enabled }
    }

    suspend fun setAutoOpenSafeUrls(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.AUTO_OPEN_SAFE_URLS] = enabled }
    }

    suspend fun setBatchModeDefault(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.BATCH_MODE_DEFAULT] = enabled }
    }

    suspend fun setManualShutterDefault(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.MANUAL_SHUTTER_DEFAULT] = enabled }
    }

    suspend fun setScreenQrAutoEncloseAll(enabled: Boolean) {
        context.prismDataStore.edit { it[Keys.SCREEN_QR_ENCLOSE_ALL] = enabled }
    }
}
