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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moon_player_settings")

enum class AccentMode {
    DYNAMIC_COVER,
    WARM_IVORY,
    TERMINAL_GREEN,
    AMBER_GLOW,
    CYAN_PULSE,
    MOON_WHITE,
    SYSTEM // Android 12+ wallpaper (Material You) accent
}

data class PlayerSettings(
    val accentMode: AccentMode = AccentMode.DYNAMIC_COVER,
    val playbackSpeed: Float = 1.0f,
    val resumeOnLaunch: Boolean = true,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val showRemainingTime: Boolean = false,
    // Tapping the player's format badge opens the song info sheet.
    val badgeOpensSongInfo: Boolean = true,
    val lastTrackUri: String = "",
    val lastPositionMs: Long = 0L,
    val repeatMode: Int = 0, // 0 = off, 1 = one, 2 = all
    val shuffleMode: Boolean = false,
    val equalizerEnabled: Boolean = true,
    val equalizerPreset: String = "Flat",
    val equalizerBands: String = "0,0,0,0,0",
    val bassBoostStrength: Int = 300,
    val libraryFolderUris: Set<String> = emptySet(),
    val onboardingComplete: Boolean = false,
    // null until the user is asked the first time lyrics open
    val onlineLyrics: Boolean? = null,
    // null = follow the phone's language (romaji unless it reads the song's own script)
    val preferRomaji: Boolean? = null
)

class SettingsManager(private val context: Context) {
    companion object {
        val KEY_ACCENT_MODE = stringPreferencesKey("accent_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_RESUME_ON_LAUNCH = booleanPreferencesKey("resume_on_launch")
        val KEY_PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val KEY_SHOW_REMAINING_TIME = booleanPreferencesKey("show_remaining_time")
        val KEY_BADGE_SONG_INFO = booleanPreferencesKey("badge_song_info")
        val KEY_LAST_TRACK_URI = stringPreferencesKey("last_track_uri")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        val KEY_EQ_BANDS = stringPreferencesKey("eq_bands")
        val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
        val KEY_LIBRARY_FOLDER_URIS = stringSetPreferencesKey("library_folder_uris")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_ONLINE_LYRICS = booleanPreferencesKey("online_lyrics")
        val KEY_PREFER_ROMAJI = booleanPreferencesKey("prefer_romaji")
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
            badgeOpensSongInfo = prefs[KEY_BADGE_SONG_INFO] ?: true,
            lastTrackUri = prefs[KEY_LAST_TRACK_URI] ?: "",
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0,
            shuffleMode = prefs[KEY_SHUFFLE_MODE] ?: false,
            equalizerEnabled = prefs[KEY_EQ_ENABLED] ?: true,
            equalizerPreset = prefs[KEY_EQ_PRESET] ?: "Flat",
            equalizerBands = prefs[KEY_EQ_BANDS] ?: "0,0,0,0,0",
            bassBoostStrength = prefs[KEY_BASS_BOOST] ?: 300,
            libraryFolderUris = prefs[KEY_LIBRARY_FOLDER_URIS] ?: emptySet(),
            onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] ?: false,
            onlineLyrics = prefs[KEY_ONLINE_LYRICS],
            preferRomaji = prefs[KEY_PREFER_ROMAJI]
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

    suspend fun setBadgeOpensSongInfo(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BADGE_SONG_INFO] = enabled }
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

    /** A song list page's chosen sort (a LibrarySortOrder name), one key per page; null: none chosen. */
    fun listSort(page: String): Flow<String?> =
        context.dataStore.data.map { it[stringPreferencesKey("list_sort_$page")] }

    /** null goes back to the page's own order. */
    suspend fun setListSort(page: String, sort: String?) {
        val key = stringPreferencesKey("list_sort_$page")
        context.dataStore.edit { if (sort == null) it.remove(key) else it[key] = sort }
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

    suspend fun setLibraryFolders(uris: Set<String>) {
        context.dataStore.edit { it[KEY_LIBRARY_FOLDER_URIS] = uris }
    }

    suspend fun setOnlineLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ONLINE_LYRICS] = enabled }
    }

    suspend fun setPreferRomaji(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREFER_ROMAJI] = enabled }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    }
}
