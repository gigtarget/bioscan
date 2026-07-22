package com.example.bioscan.core.common

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bioscan.core.recognition.IdentityMatcher
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bioscan_kiosk_settings")

data class KioskSettings(
    val terminalMode: TerminalDirectionMode = TerminalDirectionMode.SMART_AUTO,
    val cooldownSeconds: Int = 15,
    val livenessMode: LivenessMode = LivenessMode.OFF,
    val deviceId: String = "DOOR_FRONT_KIOSK_01",
    val timeZoneId: String = TimeZone.getDefault().id,
    val soundAlertsEnabled: Boolean = true,
    val imageRetentionDays: Int = 30,
    val recognitionThreshold: Float = IdentityMatcher.DEFAULT_THRESHOLD,
    val recognitionMargin: Float = IdentityMatcher.DEFAULT_MIN_MARGIN
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
        val RECOGNITION_THRESHOLD = floatPreferencesKey("recognition_threshold")
        val RECOGNITION_MARGIN = floatPreferencesKey("recognition_margin")
    }

    val settingsFlow: Flow<KioskSettings> = context.dataStore.data.map { preferences ->
        KioskSettings(
            terminalMode = TerminalDirectionMode.SMART_AUTO,
            cooldownSeconds = (preferences[Keys.COOLDOWN_SECONDS] ?: 15).coerceIn(5, 600),
            // Active blink/head-turn challenges are intentionally disabled. Identity assurance
            // comes from image quality, FaceNet similarity, template support, and frame consensus.
            livenessMode = LivenessMode.OFF,
            deviceId = preferences[Keys.DEVICE_ID] ?: "DOOR_FRONT_KIOSK_01",
            timeZoneId = preferences[Keys.TIMEZONE_ID] ?: TimeZone.getDefault().id,
            soundAlertsEnabled = preferences[Keys.SOUND_ALERTS] ?: true,
            imageRetentionDays = preferences[Keys.IMAGE_RETENTION_DAYS] ?: 30,
            recognitionThreshold = (preferences[Keys.RECOGNITION_THRESHOLD]
                ?: IdentityMatcher.DEFAULT_THRESHOLD).coerceIn(
                IdentityMatcher.DEFAULT_THRESHOLD,
                0.95f
            ),
            recognitionMargin = (preferences[Keys.RECOGNITION_MARGIN]
                ?: IdentityMatcher.DEFAULT_MIN_MARGIN).coerceIn(
                IdentityMatcher.DEFAULT_MIN_MARGIN,
                0.35f
            )
        )
    }

    suspend fun updateTerminalMode(mode: TerminalDirectionMode) {
        context.dataStore.edit { it[Keys.TERMINAL_MODE] = TerminalDirectionMode.SMART_AUTO.name }
    }

    suspend fun updateCooldown(seconds: Int) {
        context.dataStore.edit { it[Keys.COOLDOWN_SECONDS] = seconds.coerceIn(5, 600) }
    }

    suspend fun updateLivenessMode(mode: LivenessMode) {
        context.dataStore.edit { it[Keys.LIVENESS_MODE] = LivenessMode.OFF.name }
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

    suspend fun updateRecognitionThreshold(value: Float) {
        context.dataStore.edit {
            it[Keys.RECOGNITION_THRESHOLD] = value.coerceIn(
                IdentityMatcher.DEFAULT_THRESHOLD,
                0.95f
            )
        }
    }

    suspend fun updateRecognitionMargin(value: Float) {
        context.dataStore.edit {
            it[Keys.RECOGNITION_MARGIN] = value.coerceIn(
                IdentityMatcher.DEFAULT_MIN_MARGIN,
                0.35f
            )
        }
    }
}
