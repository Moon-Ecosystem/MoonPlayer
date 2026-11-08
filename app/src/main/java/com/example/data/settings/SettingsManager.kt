package com.example.data.settings

import android.content.Context
import com.example.data.model.ArtistSplitRules
import com.example.data.scrobble.ScrobbleService
import com.example.data.lyrics.DEFAULT_LYRICS_SOURCE_ORDER
import com.example.data.lyrics.LyricsSource
import com.example.data.lyrics.parseSourceOrder
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
    // The queue opens with its Played section unfolded.
    val queueShowPlayed: Boolean = false,
    // Turning shuffle on reshuffles played songs too, not just what's still to come.
    val shuffleAll: Boolean = false,
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
    // Whether Moon Player may contact LRCLIB at all (Look up on LRCLIB); off keeps lyrics offline.
    val lrclibEnabled: Boolean = true,
    // Look every song up on LRCLIB automatically (needs lrclibEnabled); null = never chosen, counts as off.
    val onlineLyrics: Boolean? = null,
    // null = follow the phone's language (romaji unless it reads the song's own script)
    val preferRomaji: Boolean? = null,
    // Where lyrics come from, most wanted first; a song's own pick in the source sheet wins.
    val lyricsSourceOrder: List<LyricsSource> = DEFAULT_LYRICS_SOURCE_ORDER,
    // A synced version beats the order above; off, the order wins even for unsynced lyrics.
    val preferSyncedLyrics: Boolean = true,
    // The source line that fades in over the lyrics; the one at their end always stays.
    val lyricsSourceChip: Boolean = true,
    // The scrobble account whose stats show on the stats page; a blank user is not connected.
    val scrobbleService: ScrobbleService = ScrobbleService.LASTFM,
    val scrobbleUser: String = "",
    // Off hides the scrobble side of Listening stats (and its switch) altogether.
    val showScrobbles: Boolean = true,
    // "A, B" counts as two artists; the user's per-tag choices from an artist page win over it.
    val splitArtistCommas: Boolean = true,
    val artistSplits: Map<String, List<Boolean>> = emptyMap()
) {
    val artistRules: ArtistSplitRules get() = ArtistSplitRules(splitArtistCommas, artistSplits)
}

// One saved artist fix: the tag, this mark, then 1 (split) or 0 (joined) per separator.
private const val FIX_MARK = '\u001F'

private fun parseArtistSplits(entries: Set<String>?): Map<String, List<Boolean>> =
    entries.orEmpty().mapNotNull { entry ->
        val at = entry.lastIndexOf(FIX_MARK).takeIf { it > 0 } ?: return@mapNotNull null
        entry.substring(0, at) to entry.substring(at + 1).map { it == '1' }
    }.toMap()

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
        val KEY_LRCLIB_ENABLED = booleanPreferencesKey("lrclib_enabled")
        val KEY_PREFER_ROMAJI = booleanPreferencesKey("prefer_romaji")
        val KEY_LYRICS_SOURCE_ORDER = stringPreferencesKey("lyrics_source_order")
        val KEY_PREFER_SYNCED_LYRICS = booleanPreferencesKey("prefer_synced_lyrics")
        val KEY_LYRICS_SOURCE_CHIP = booleanPreferencesKey("lyrics_source_chip")
        val KEY_QUEUE_SHOW_PLAYED = booleanPreferencesKey("queue_show_played")
        val KEY_SHUFFLE_ALL = booleanPreferencesKey("shuffle_all")
        val KEY_SCROBBLE_SERVICE = stringPreferencesKey("scrobble_service")
        val KEY_SCROBBLE_USER = stringPreferencesKey("scrobble_user")
        val KEY_SHOW_SCROBBLES = booleanPreferencesKey("show_scrobbles")
        val KEY_SPLIT_ARTIST_COMMAS = booleanPreferencesKey("split_artist_commas")
        val KEY_ARTIST_SPLITS = stringSetPreferencesKey("artist_splits")
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
            lrclibEnabled = prefs[KEY_LRCLIB_ENABLED] ?: true,
            onlineLyrics = prefs[KEY_ONLINE_LYRICS],
            preferRomaji = prefs[KEY_PREFER_ROMAJI],
            lyricsSourceOrder = parseSourceOrder(prefs[KEY_LYRICS_SOURCE_ORDER]),
            preferSyncedLyrics = prefs[KEY_PREFER_SYNCED_LYRICS] ?: true,
            lyricsSourceChip = prefs[KEY_LYRICS_SOURCE_CHIP] ?: true,
            queueShowPlayed = prefs[KEY_QUEUE_SHOW_PLAYED] ?: false,
            shuffleAll = prefs[KEY_SHUFFLE_ALL] ?: false,
            scrobbleService = ScrobbleService.entries.firstOrNull { it.name == prefs[KEY_SCROBBLE_SERVICE] }
                ?: ScrobbleService.LASTFM,
            scrobbleUser = prefs[KEY_SCROBBLE_USER] ?: "",
            showScrobbles = prefs[KEY_SHOW_SCROBBLES] ?: true,
            splitArtistCommas = prefs[KEY_SPLIT_ARTIST_COMMAS] ?: true,
            artistSplits = parseArtistSplits(prefs[KEY_ARTIST_SPLITS])
        )
    }

    suspend fun setSplitArtistCommas(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPLIT_ARTIST_COMMAS] = enabled }
    }

    /** Saves the user's splits for [tag] (one per separator), or forgets them with null. */
    suspend fun setArtistSplit(tag: String, splits: List<Boolean>?) {
        context.dataStore.edit { prefs ->
            val others = prefs[KEY_ARTIST_SPLITS].orEmpty().filterNot { it.substringBeforeLast(FIX_MARK) == tag }
            prefs[KEY_ARTIST_SPLITS] = others.toSet() +
                listOfNotNull(splits?.let { s -> tag + FIX_MARK + s.joinToString("") { if (it) "1" else "0" } })
        }
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

    /** A blank [user] disconnects. */
    suspend fun setScrobbleAccount(service: ScrobbleService, user: String) {
        context.dataStore.edit {
            it[KEY_SCROBBLE_SERVICE] = service.name
            it[KEY_SCROBBLE_USER] = user.trim()
        }
    }

    suspend fun setPreferRomaji(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREFER_ROMAJI] = enabled }
    }

    suspend fun setShowScrobbles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_SCROBBLES] = enabled }
    }

    suspend fun setQueueShowPlayed(enabled: Boolean) {
        context.dataStore.edit { it[KEY_QUEUE_SHOW_PLAYED] = enabled }
    }

    suspend fun setShuffleAll(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHUFFLE_ALL] = enabled }
    }

    suspend fun setLyricsSourceOrder(order: List<LyricsSource>) {
        context.dataStore.edit { it[KEY_LYRICS_SOURCE_ORDER] = order.joinToString(",") { s -> s.name } }
    }

    suspend fun setLyricsSourceChip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICS_SOURCE_CHIP] = enabled }
    }

    suspend fun setPreferSyncedLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREFER_SYNCED_LYRICS] = enabled }
    }

    suspend fun setLrclibEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LRCLIB_ENABLED] = enabled }
    }

    /** The first-start lyrics question: LRCLIB off, only when asked, or automatically. */
    suspend fun setLrclibMode(enabled: Boolean, automatic: Boolean) {
        context.dataStore.edit {
            it[KEY_LRCLIB_ENABLED] = enabled
            it[KEY_ONLINE_LYRICS] = enabled && automatic
        }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    }
}
