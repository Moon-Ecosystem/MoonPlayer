package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moon_player_settings")

enum class AccentMode {
    DYNAMIC_COVER,
    WARM_IVORY,
    TERMINAL_GREEN,
    AMBER_GLOW,
    CYAN_PULSE
}

data class PlayerSettings(
    val accentMode: AccentMode = AccentMode.DYNAMIC_COVER,
    val playbackSpeed: Float = 1.0f,
    val resumeOnLaunch: Boolean = true,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val showRemainingTime: Boolean = false,
    val lastTrackUri: String = "",
    val lastPositionMs: Long = 0L,
    val repeatMode: Int = 0, // 0 = off, 1 = one, 2 = all
    val shuffleMode: Boolean = false,
    val equalizerEnabled: Boolean = true,
    val equalizerPreset: String = "Flat",
    val equalizerBands: String = "0,0,0,0,0",
    val bassBoostStrength: Int = 300
)

class SettingsManager(private val context: Context) {
    companion object {
        val KEY_ACCENT_MODE = stringPreferencesKey("accent_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_RESUME_ON_LAUNCH = booleanPreferencesKey("resume_on_launch")
        val KEY_PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val KEY_SHOW_REMAINING_TIME = booleanPreferencesKey("show_remaining_time")
        val KEY_LAST_TRACK_URI = stringPreferencesKey("last_track_uri")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        val KEY_EQ_BANDS = stringPreferencesKey("eq_bands")
        val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_ACCENT_MODE] ?: AccentMode.DYNAMIC_COVER.name
        val mode = try {
            AccentMode.valueOf(modeStr)
        } catch (e: Exception) {
            AccentMode.DYNAMIC_COVER
        }

        PlayerSettings(
            accentMode = mode,
            playbackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
            resumeOnLaunch = prefs[KEY_RESUME_ON_LAUNCH] ?: true,
            pauseOnHeadphoneDisconnect = prefs[KEY_PAUSE_ON_DISCONNECT] ?: true,
            showRemainingTime = prefs[KEY_SHOW_REMAINING_TIME] ?: false,
            lastTrackUri = prefs[KEY_LAST_TRACK_URI] ?: "",
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0,
            shuffleMode = prefs[KEY_SHUFFLE_MODE] ?: false,
            equalizerEnabled = prefs[KEY_EQ_ENABLED] ?: true,
            equalizerPreset = prefs[KEY_EQ_PRESET] ?: "Flat",
            equalizerBands = prefs[KEY_EQ_BANDS] ?: "0,0,0,0,0",
            bassBoostStrength = prefs[KEY_BASS_BOOST] ?: 300
        )
    }

    suspend fun setAccentMode(mode: AccentMode) {
        context.dataStore.edit { it[KEY_ACCENT_MODE] = mode.name }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed }
    }

    suspend fun setResumeOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_ON_LAUNCH] = enabled }
    }

    suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAUSE_ON_DISCONNECT] = enabled }
    }

    suspend fun setShowRemainingTime(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_REMAINING_TIME] = show }
    }

    suspend fun savePlaybackState(uri: String, positionMs: Long) {
        context.dataStore.edit {
            it[KEY_LAST_TRACK_URI] = uri
            it[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { it[KEY_REPEAT_MODE] = mode }
    }

    suspend fun setShuffleMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHUFFLE_MODE] = enabled }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    suspend fun setEqualizerPreset(preset: String) {
        context.dataStore.edit { it[KEY_EQ_PRESET] = preset }
    }

    suspend fun setEqualizerBands(bands: String) {
        context.dataStore.edit { it[KEY_EQ_BANDS] = bands }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        context.dataStore.edit { it[KEY_BASS_BOOST] = strength }
    }
}
