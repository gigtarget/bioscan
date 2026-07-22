package com.example.bioscan.core.common

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bioscan_kiosk_settings")

data class KioskSettings(
    val terminalMode: TerminalDirectionMode = TerminalDirectionMode.SMART_AUTO,
    val cooldownSeconds: Int = 15,
    val livenessMode: LivenessMode = LivenessMode.STANDARD,
    val deviceId: String = "DOOR_FRONT_KIOSK_01",
    val timeZoneId: String = "UTC",
    val soundAlertsEnabled: Boolean = true,
    val imageRetentionDays: Int = 30,
    val recognitionThreshold: Float = 0.60f,
    val recognitionMargin: Float = 0.04f
)

class KioskSettingsRepository(private val context: Context) {

    private object Keys {
        val TERMINAL_MODE = stringPreferencesKey("terminal_mode")
        val COOLDOWN_SECONDS = intPreferencesKey("cooldown_seconds")
        val LIVENESS_MODE = stringPreferencesKey("liveness_mode")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val SOUND_ALERTS = booleanPreferencesKey("sound_alerts")
        val IMAGE_RETENTION_DAYS = intPreferencesKey("image_retention_days")
    }

    val settingsFlow: Flow<KioskSettings> = context.dataStore.data.map { prefs ->
        KioskSettings(
            terminalMode = TerminalDirectionMode.valueOf(prefs[Keys.TERMINAL_MODE] ?: TerminalDirectionMode.SMART_AUTO.name),
            cooldownSeconds = prefs[Keys.COOLDOWN_SECONDS] ?: 15,
            livenessMode = LivenessMode.valueOf(prefs[Keys.LIVENESS_MODE] ?: LivenessMode.STANDARD.name),
            deviceId = prefs[Keys.DEVICE_ID] ?: "DOOR_FRONT_KIOSK_01",
            timeZoneId = prefs[Keys.TIMEZONE_ID] ?: "UTC",
            soundAlertsEnabled = prefs[Keys.SOUND_ALERTS] ?: true,
            imageRetentionDays = prefs[Keys.IMAGE_RETENTION_DAYS] ?: 30
        )
    }

    suspend fun updateTerminalMode(mode: TerminalDirectionMode) {
        context.dataStore.edit { it[Keys.TERMINAL_MODE] = mode.name }
    }

    suspend fun updateCooldown(seconds: Int) {
        context.dataStore.edit { it[Keys.COOLDOWN_SECONDS] = seconds }
    }

    suspend fun updateLivenessMode(mode: LivenessMode) {
        context.dataStore.edit { it[Keys.LIVENESS_MODE] = mode.name }
    }

    suspend fun updateDeviceId(id: String) {
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun updateTimeZone(tzId: String) {
        context.dataStore.edit { it[Keys.TIMEZONE_ID] = tzId }
    }

    suspend fun updateSoundAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ALERTS] = enabled }
    }
}
