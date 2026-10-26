package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.LyricsLookup
import com.example.data.lyrics.prefersLatinLyrics
import com.example.data.lyrics.LyricsRepository
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.data.repository.folderNameFromTreeUri
import com.example.data.repository.folderPlaylistsOf
import com.example.data.repository.pickQuickPlaylists
import com.example.data.settings.AccentMode
import com.example.data.settings.PlayerSettings
import com.example.data.settings.SettingsManager
import com.example.data.InfoSection
import com.example.data.SongInfo
import com.example.data.readSongInfo
import com.example.data.stats.ListeningStats
import com.example.data.stats.StatsDetail
import com.example.data.stats.StatsKind
import com.example.data.stats.StatsRange
import com.example.data.stats.detailStats
import com.example.data.stats.listeningStats
import com.example.service.PlaybackService
import com.example.ui.theme.CelestialCyan
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.WarmIvory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    PLAYER,
    QUEUE,
    LIBRARY,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    PLAYLIST_DETAIL,
    FAVORITES,
    HISTORY,
    STATS,
    SETTINGS,
    ABOUT
}


/**
 * The song-list screens' search box and sort menu. Shared by All Songs and the screens that show
 * their own list (folders, folder playlists, albums...), so both controls work on every one.
 */
fun searchAndSort(tracks: List<Track>, query: String, sort: LibrarySortOrder): List<Track> {
    val found = if (query.isBlank()) tracks else tracks.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true) ||
            it.folderPath.contains(query, ignoreCase = true)
    }
    return when (sort) {
        LibrarySortOrder.TITLE_ASC -> found.sortedBy { it.title.lowercase() }
        LibrarySortOrder.ARTIST_ASC -> found.sortedBy { it.artist.lowercase() }
        // Within an album, in track order.
        LibrarySortOrder.ALBUM_ASC -> found.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
        LibrarySortOrder.DURATION_DESC -> found.sortedByDescending { it.durationMs }
        LibrarySortOrder.DATE_ADDED_DESC -> found.sortedByDescending { it.dateAdded }
    }
}

/**
 * Time a song actually plays, by [now] (a clock in ms). It runs only while playing, so pauses
 * and the unplayed rest of a skipped song don't count.
 */
class ListenTimer(private val now: () -> Long) {
    private var since: Long? = null
    private var stored = 0L

    fun setPlaying(playing: Boolean) {
        val start = since
        if (playing) {
            if (start == null) since = now()
        } else if (start != null) {
            stored += now() - start
            since = null
        }
    }

    /** The time played since the last take; counting starts again from zero. */
    fun take(): Long {
        val start = since
        var played = stored
        if (start != null) {
            val t = now()
            played += t - start
            since = t
        }
        stored = 0L
        return played
    }
}

enum class LibrarySortOrder(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    ARTIST_ASC("Artist (A-Z)"),
    ALBUM_ASC("Album (A-Z)"),
    DURATION_DESC("Duration (Longest first)"),
    DATE_ADDED_DESC("Date Added (Newest)")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainViewModel"
    }

    private val repository = MusicRepository(application)
    val settingsManager = SettingsManager(application)
    private val lyricsRepository = LyricsRepository(
        application,
        com.example.data.db.MoonPlayerDatabase.getInstance(application).lyricsDao()
    )

    sealed interface LyricsUiState {
        data object Loading : LyricsUiState
        /** First open with no local lyrics: ask before anything goes online. */
        data object AskOnline : LyricsUiState
        data class Done(val lookup: LyricsLookup, val onlineEnabled: Boolean) : LyricsUiState
    }

    private val _lyricsOpen = MutableStateFlow(false)
    val lyricsOpen = _lyricsOpen.asStateFlow()
    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Loading)
    val lyricsState = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null

    private var playbackService: PlaybackService? = null
    private var isBound = false

    // App Navigation & UI Dialogs
    private val _currentScreen = MutableStateFlow(Screen.PLAYER)
    val currentScreen = _currentScreen.asStateFlow()

    private val _isQueuePanelOpen = MutableStateFlow(false)
    val isQueuePanelOpen = _isQueuePanelOpen.asStateFlow()

    private val _isEqualizerOpen = MutableStateFlow(false)
    val isEqualizerOpen = _isEqualizerOpen.asStateFlow()

    private val _isSleepTimerOpen = MutableStateFlow(false)
    val isSleepTimerOpen = _isSleepTimerOpen.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _playlistTracks = MutableStateFlow<List<PlaylistTrackEntity>>(emptyList())
    val playlistTracks = _playlistTracks.asStateFlow()

    private val _trackToAddToPlaylist = MutableStateFlow<Track?>(null)
    val trackToAddToPlaylist = _trackToAddToPlaylist.asStateFlow()

    // Persisted SAF folders (tree URIs) that bound all library scanning
    private val _libraryFolders = MutableStateFlow<Set<String>>(emptySet())
    val libraryFolders = _libraryFolders.asStateFlow()

    /**
     * One live playlist per library folder: (folder name, its tracks). Derived from the library,
     * so it always matches the folder's current contents and needs no stored copy.
     */
    val folderPlaylists: StateFlow<List<Pair<String, List<Track>>>> by lazy {
        // The real folders songs sit in, found inside the library folders: adding Music lists
        // Music/J-pop and Music/Random, and new subfolders appear after the next scan.
        allTracks.map(::folderPlaylistsOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Comma-separated folder names for status text; blank when no folder is chosen
    private val _selectedFolderName = MutableStateFlow("")
    val selectedFolderName = _selectedFolderName.asStateFlow()

    private val _folderAccessError = MutableStateFlow<String?>(null)
    val folderAccessError = _folderAccessError.asStateFlow()

    // Search and Sort
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.TITLE_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    // Data flows
    val allTracks = repository.allTracks
    val favoriteTracks = repository.favoriteTracks
    val favoriteTrackIds = repository.favoriteTrackIds.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val allPlaylists = repository.allPlaylists.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    /** Songs of each of your playlists in order, by playlist id (for covers on the list). */
    val playlistRows: StateFlow<Map<Long, List<PlaylistTrackEntity>>> = repository.allPlaylistTracks
        .map { rows -> rows.groupBy { it.playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val playHistory = repository.playHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    /** A playlist in the drawer: [playlist] for one of yours, null for a folder playlist. */
    data class QuickPlaylist(val name: String, val playlist: PlaylistEntity?, val songs: Int, val playing: Boolean)

    /** Up to 4 playlists for the drawer: the one playing, then the most recently played. */
    val quickPlaylists: StateFlow<List<QuickPlaylist>> by lazy {
        combine(
            allPlaylists, folderPlaylists, repository.recentPlaylistNames,
            combine(_playingFrom, _playingPlaylistId, _playingFolder) { from, id, folder -> Triple(from, id, folder) },
            repository.playlistSongCounts
        ) { mine, folders, recent, (playing, playingId, playingFolder), counts ->
            val byName = mine.associateBy { it.name }
            val songsById = counts.associate { it.playlistId to it.songs }
            val folderSizes = folders.toMap().mapValues { it.value.size }
            pickQuickPlaylists(mine.map { it.name } + folders.map { it.first }, recent, playing).map { name ->
                val playlist = byName[name]
                val songs = playlist?.let { songsById[it.id] ?: 0 } ?: folderSizes[name] ?: 0
                val isPlaying = if (playlist != null) playlist.id == playingId else name == playingFolder
                QuickPlaylist(name, playlist, songs, playing = isPlaying)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Folder playlist shown on the Playlists screen; kept here so the quick panel can open one.
    private val _openFolderPlaylist = MutableStateFlow<String?>(null)
    val openFolderPlaylist = _openFolderPlaylist.asStateFlow()

    fun openFolderPlaylist(name: String) {
        navigateTo(Screen.PLAYLISTS)
        _openFolderPlaylist.value = name
    }

    fun closeFolderPlaylist() {
        _openFolderPlaylist.value = null
    }

    private val _statsRange = MutableStateFlow(StatsRange.ALL)
    val statsRange = _statsRange.asStateFlow()

    fun setStatsRange(range: StatsRange) {
        _statsRange.value = range
    }

    // The plays in the chosen range, with that range, re-read whenever history changes.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val statsRows = _statsRange.flatMapLatest { range ->
        repository.historySince(range.since(System.currentTimeMillis())).map { range to it }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** The stats page for the chosen range. Null while loading. */
    val listeningStats: StateFlow<ListeningStats?> = statsRows
        .map { (range, rows) -> listeningStats(rows, range, System.currentTimeMillis()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // The artist, album, song or playlist whose detail page is open on the stats screen.
    private val _statsSelection = MutableStateFlow<Pair<StatsKind, String>?>(null)

    fun openStatsDetail(kind: StatsKind, key: String) {
        _statsSelection.value = kind to key
    }

    fun closeStatsDetail() {
        _statsSelection.value = null
    }

    /** The open detail page's stats, in the same range; null when none is open. */
    val statsDetail: StateFlow<StatsDetail?> = combine(statsRows, _statsSelection) { (range, rows), selected ->
        selected?.let { (kind, key) -> detailStats(rows, kind, key, range, System.currentTimeMillis()) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings = settingsManager.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlayerSettings()
    )

    // null until DataStore has loaded, so the UI can tell "not yet known" from "first run"
    val onboardingComplete: StateFlow<Boolean?> = settingsManager.settingsFlow
        .map<PlayerSettings, Boolean?> { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { settingsManager.setOnboardingComplete() }
    }


    // Filtered & Sorted Tracks
    val filteredTracks: StateFlow<List<Track>> = combine(
        allTracks,
        _searchQuery,
        _sortOrder,
        ::searchAndSort
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playback state forwarded from PlaybackService
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    // In-memory only; a restored or hand-built queue simply shows no source.
    private val _playingFrom = MutableStateFlow<String?>(null)
    val playingFrom = _playingFrom.asStateFlow()

    // Playlist (user or folder) the current queue came from, credited in playlist stats
    private var playingPlaylist: String? = null

    // Your playlist the queue came from, for its own equalizer preset and the "Playing" mark.
    private val _playingPlaylistId = MutableStateFlow<Long?>(null)
    val playingPlaylistId = _playingPlaylistId.asStateFlow()

    // The folder playlist the queue came from (folders have no id, so by name); null otherwise,
    // so a playlist of yours or an album with the same name isn't mistaken for it.
    private val _playingFolder = MutableStateFlow<String?>(null)
    val playingFolder = _playingFolder.asStateFlow()

    // Listening time goes to the song (and playlist) that was playing, saved on pause and on song change.
    private val listenTimer = ListenTimer { android.os.SystemClock.elapsedRealtime() }
    private var listenTrackId: String? = null
    private var listenPlaylist: String? = null

    private var playCounted = false

    /**
     * Adds the current song to history and play counts once it's set to play. Tapping a song
     * while paused announces it before play() is called, so this also runs when playback starts;
     * a session restored paused and never played isn't counted.
     */
    private fun countPlayOnceStarted(service: PlaybackService) {
        val track = _currentTrack.value ?: return
        if (playCounted || !service.player.playWhenReady) return
        playCounted = true
        viewModelScope.launch { repository.recordHistory(track, listenPlaylist) }
    }

    private fun saveListenTime(scope: CoroutineScope = viewModelScope) {
        val trackId = listenTrackId ?: return
        val ms = listenTimer.take()
        scope.launch { repository.addListenTime(trackId, ms) }
    }

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode = _isShuffleMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()

    private val _isStopAtEndActive = MutableStateFlow(false)
    val isStopAtEndActive = _isStopAtEndActive.asStateFlow()

    private val _currentFileMissing = MutableStateFlow(false)
    val currentFileMissing = _currentFileMissing.asStateFlow()

    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    val abLoopStartMs = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    val abLoopEndMs = _abLoopEndMs.asStateFlow()

    // Dynamic accent color
    private val _activeAccentColor = MutableStateFlow(WarmIvory)
    val activeAccentColor = _activeAccentColor.asStateFlow()

    private var accentColorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? PlaybackService.LocalBinder
            playbackService = localBinder?.getService()
            isBound = true
            viewModelScope.launch {
                // Before observing: the queue observer saves the (still empty) queue right away,
                // which would wipe the saved session before it could be restored.
                restoreLastSession()
                observePlaybackService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    init {
        bindPlaybackService()
        restoreLibraryFolder()
        observeSettingsForTheme()
        // Open lyrics follow the song.
        viewModelScope.launch {
            _currentTrack.map { it?.id }.distinctUntilChanged().collect { if (_lyricsOpen.value) loadLyrics() }
        }
        // A rescan that finishes after the song loaded (e.g. at launch) refreshes its details.
        viewModelScope.launch {
            allTracks.collect { library ->
                val current = _currentTrack.value ?: return@collect
                library.firstOrNull { it.id == current.id }?.takeIf { it != current }?.let { _currentTrack.value = it }
            }
        }
    }

    fun openLyrics() {
        _lyricsOpen.value = true
        loadLyrics()
    }

    fun closeLyrics() {
        _lyricsOpen.value = false
    }

    /** Answer to the one-time "look up lyrics online?" question. */
    fun answerOnlineLyrics(allow: Boolean) {
        viewModelScope.launch {
            settingsManager.setOnlineLyrics(allow)
            loadLyrics(online = allow)
        }
    }

    fun setOnlineLyrics(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setOnlineLyrics(enabled) }
    }

    /** Drops a saved "no lyrics" (or wrong) result and looks the song up again. */
    fun retryLyrics() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            lyricsRepository.forget(track.id)
            loadLyrics()
        }
    }

    /** Shows another version (original / romaji / translation) and remembers it for the song. */
    fun showLyricsKind(kind: LyricsKind) {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            lyricsRepository.choose(track.id, kind)
            loadLyrics(showSpinner = false)
        }
    }

    fun setPreferRomaji(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setPreferRomaji(enabled) }
    }

    /** The romaji preference in effect: the user's setting, else the phone's language. */
    fun effectivePreferRomaji(settings: PlayerSettings): Boolean =
        settings.preferRomaji ?: prefersLatinLyrics(java.util.Locale.getDefault().language)

    private fun loadLyrics(online: Boolean? = null, showSpinner: Boolean = true) {
        val track = _currentTrack.value ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            if (showSpinner) _lyricsState.value = LyricsUiState.Loading
            val saved = settingsManager.settingsFlow.first()
            val allowed = online ?: saved.onlineLyrics
            val lookup = lyricsRepository.find(track, online = allowed == true, preferRomaji = effectivePreferRomaji(saved))
            _lyricsState.value = if (allowed == null && lookup !is LyricsLookup.Found) {
                LyricsUiState.AskOnline
            } else {
                LyricsUiState.Done(lookup, onlineEnabled = allowed == true)
            }
        }
    }

    private fun bindPlaybackService() {
        val intent = Intent(getApplication(), PlaybackService::class.java)
        try {
            getApplication<Application>().startService(intent)
        } catch (_: Exception) {}
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * On a fresh start (empty player) with Resume on App Launch on, reloads the saved queue at the
     * last song and position, paused. Does nothing if the service is already playing something.
     */
    private suspend fun restoreLastSession() {
        val service = playbackService ?: return
        if (service.queue.value.isNotEmpty()) return
        val saved = settingsManager.settingsFlow.first()
        if (!saved.resumeOnLaunch) return
        val entries = repository.savedQueue.first()
        if (entries.isEmpty()) return

        val libraryById = allTracks.value.associateBy { it.id }
        val tracks = entries.map { e ->
            libraryById[e.trackId] ?: Track(
                id = e.trackId,
                contentUri = e.uri,
                title = e.title,
                artist = e.artist,
                album = e.album,
                durationMs = e.durationMs,
                albumArtUri = e.albumArtUri
            )
        }
        // By document id when the address changed (same file, reached through another folder).
        fun docId(uri: String) = runCatching { android.provider.DocumentsContract.getDocumentId(Uri.parse(uri)) }.getOrNull()
        val lastDoc = docId(saved.lastTrackUri)
        val index = tracks.indexOfFirst { it.contentUri == saved.lastTrackUri }.takeIf { it >= 0 }
            ?: tracks.indexOfFirst { lastDoc != null && docId(it.contentUri) == lastDoc }
        service.playTrackList(
            tracks,
            startIndex = index.coerceAtLeast(0),
            startPositionMs = if (index >= 0) saved.lastPositionMs else 0L,
            playWhenReady = false
        )
    }

    private fun observePlaybackService() {
        val service = playbackService ?: return
        viewModelScope.launch {
            // By id: the same song at a new address (relinkQueue) isn't a new play.
            service.currentTrack.distinctUntilChangedBy { it?.id }.collect { queued ->
                // Tracks rebuilt from playlists/history lack format, bitrate and sample rate;
                // the scanned library copy has them, so prefer it when the track is still there.
                val track = queued?.let { q -> allTracks.value.firstOrNull { it.id == q.id } ?: q }
                saveListenTime()
                listenTrackId = track?.id
                listenPlaylist = playingPlaylist
                _currentTrack.value = track
                updateAccentColor(track)
                playCounted = false
                countPlayOnceStarted(service)
                // Player position, not 0: right after a restore it's the restored position.
                track?.let { settingsManager.savePlaybackState(it.contentUri, service.player.currentPosition) }
            }
        }
        viewModelScope.launch {
            service.isPlaying.collect { playing ->
                _isPlaying.value = playing
                listenTimer.setPlaying(playing)
                if (playing) countPlayOnceStarted(service) else saveListenTime()
                // Remember where playback stopped so Resume on App Launch can continue there.
                if (!playing) _currentTrack.value?.let {
                    settingsManager.savePlaybackState(it.contentUri, service.player.currentPosition)
                }
            }
        }
        viewModelScope.launch {
            settingsManager.settingsFlow
                .map { it.pauseOnHeadphoneDisconnect }
                .distinctUntilChanged()
                .collect { service.player.setHandleAudioBecomingNoisy(it) }
        }
        viewModelScope.launch {
            service.currentPositionMs.collect { _currentPositionMs.value = it }
        }
        viewModelScope.launch {
            service.durationMs.collect { _durationMs.value = it }
        }
        viewModelScope.launch {
            service.queue.collect {
                _queue.value = it
                repository.saveQueue(it)
            }
        }
        viewModelScope.launch {
            allTracks.collect { library ->
                if (library.isNotEmpty()) service.relinkQueue(library.associateBy { it.id })
            }
        }
        viewModelScope.launch {
            service.currentIndex.collect { _currentIndex.value = it }
        }
        viewModelScope.launch {
            service.repeatMode.collect { _repeatMode.value = it }
        }
        viewModelScope.launch {
            service.isShuffleMode.collect { _isShuffleMode.value = it }
        }
        viewModelScope.launch {
            service.playbackSpeed.collect { _playbackSpeed.value = it }
        }
        viewModelScope.launch {
            service.sleepTimerSecondsRemaining.collect { _sleepTimerRemaining.value = it }
        }
        viewModelScope.launch {
            service.isStopAtEndActive.collect { _isStopAtEndActive.value = it }
        }
        viewModelScope.launch {
            service.abLoopStartMs.collect { _abLoopStartMs.value = it }
        }
        viewModelScope.launch {
            service.abLoopEndMs.collect { _abLoopEndMs.value = it }
        }
        viewModelScope.launch {
            service.currentFileMissing.collect { _currentFileMissing.value = it }
        }
        // The playing playlist's own preset, else the usual one; also restores it at launch.
        // Applied only when it changes, so hand-moved bands stay until then.
        viewModelScope.launch {
            combine(_playingPlaylistId, repository.allPlaylists, settingsManager.settingsFlow) { id, playlists, settings ->
                playlists.firstOrNull { it.id == id }?.eqPreset ?: settings.equalizerPreset
            }.distinctUntilChanged().collect { service.equalizerHelper.applyPreset(it) }
        }
    }

    private fun observeSettingsForTheme() {
        viewModelScope.launch {
            settings.collect { s ->
                when (s.accentMode) {
                    AccentMode.DYNAMIC_COVER -> {
                        updateAccentColor(_currentTrack.value)
                    }
                    AccentMode.WARM_IVORY -> _activeAccentColor.value = WarmIvory
                    AccentMode.TERMINAL_GREEN -> _activeAccentColor.value = TerminalGreen
                    AccentMode.AMBER_GLOW -> _activeAccentColor.value = TerminalAmber
                    AccentMode.CYAN_PULSE -> _activeAccentColor.value = CelestialCyan
                    AccentMode.MOON_WHITE -> _activeAccentColor.value = MoonWhite
                    AccentMode.SYSTEM -> _activeAccentColor.value = systemAccentColor()
                }
            }
        }
    }

    /**
     * The wallpaper-based accent (Material You). Tone 200 is the light shade Android uses for
     * accents on dark backgrounds. Before Android 12 there is none, so the default accent stands in.
     */
    fun systemAccentColor(): Color =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Color(getApplication<Application>().getColor(android.R.color.system_accent1_200))
        } else {
            WarmIvory
        }

    private fun updateAccentColor(track: Track?) {
        val currentMode = settings.value.accentMode
        if (currentMode != AccentMode.DYNAMIC_COVER) return

        accentColorJob?.cancel()
        accentColorJob = viewModelScope.launch {
            val color = repository.extractAccentColor(track)
            _activeAccentColor.value = color
        }
    }

    /**
     * Persists the picker's grant and adds that tree to the library alongside any folders
     * already chosen. On failure the existing library and folders are left untouched.
     */
    fun addFolder(
        treeUri: Uri,
        grantFlags: Int,
        onResult: ((folderName: String, trackCount: Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val readFlags = grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (readFlags == 0) {
                _folderAccessError.value = "The folder picker did not grant read access. Choose the folder again."
                return@launch
            }

            try {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(treeUri, readFlags)
            } catch (e: SecurityException) {
                Log.w(TAG, "Persistable folder grant rejected (${e.javaClass.simpleName})")
                _folderAccessError.value = "Android did not grant lasting access to that folder. Choose it again."
                return@launch
            }

            val result = repository.scanFolderUri(treeUri, allTracks.value.associateBy { it.id })
            if (result.error != null) {
                _folderAccessError.value = result.error
                return@launch
            }

            // distinctBy drops duplicates when the new folder overlaps one already in the library
            repository.setTracks((allTracks.value + result.tracks).distinctBy { it.id })
            setFolders(_libraryFolders.value + treeUri.toString())
            _folderAccessError.value = null
            onResult?.invoke(result.folderName, result.tracks.size)
        }
    }

    /** Forgets a folder, releases its grant and rebuilds the library from the remaining folders. */
    fun removeFolder(treeUri: String) {
        viewModelScope.launch {
            setFolders(_libraryFolders.value - treeUri)
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            scanAllFolders(allTracks.value)
        }
    }

    /** Rescans the chosen folders only; never falls back to device-wide storage. */
    fun rescanLibrary() {
        viewModelScope.launch {
            if (_libraryFolders.value.isEmpty()) {
                _folderAccessError.value = "No music folder selected yet."
                return@launch
            }
            scanAllFolders(allTracks.value)
        }
    }

    private fun restoreLibraryFolder() {
        viewModelScope.launch {
            _libraryFolders.value = settingsManager.settingsFlow.first().libraryFolderUris
            _selectedFolderName.value = folderNames()
            if (_libraryFolders.value.isEmpty()) return@launch

            // Show the last scan instantly; the rescan below only re-reads new or changed files.
            scanAllFolders(repository.loadCachedLibrary())
        }
    }

    /**
     * Rebuilds the library from every chosen folder, reusing [known] metadata for unchanged
     * files. A folder that can't be read is reported and skipped; the others still load.
     */
    private suspend fun scanAllFolders(known: List<Track>) {
        val knownById = known.associateBy { it.id }
        val tracks = mutableListOf<Track>()
        var error: String? = null
        for (folder in _libraryFolders.value) {
            val uri = Uri.parse(folder)
            if (!hasPersistedReadPermission(uri)) {
                error = "Access to \"${folderNameFromTreeUri(folder)}\" has expired. Remove it and add it again."
                continue
            }
            val result = repository.scanFolderUri(uri, knownById)
            if (result.error != null) error = result.error else tracks += result.tracks
        }
        val merged = tracks.distinctBy { it.id }
        if (merged != known) repository.setTracks(merged)
        _folderAccessError.value = error
    }

    private suspend fun setFolders(folders: Set<String>) {
        _libraryFolders.value = folders
        _selectedFolderName.value = folderNames()
        settingsManager.setLibraryFolders(folders)
    }

    private fun folderNames(): String = _libraryFolders.value.joinToString(", ") { folderNameFromTreeUri(it) }

    private fun hasPersistedReadPermission(uri: Uri): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }


    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        // Coming back to Playlists later shows the list, not the folder last opened.
        if (screen != Screen.PLAYLISTS) _openFolderPlaylist.value = null
        if (screen != Screen.STATS) _statsSelection.value = null
    }

    private var playlistTracksJob: Job? = null

    fun openPlaylist(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        _currentScreen.value = Screen.PLAYLIST_DETAIL
        // Stop the previous playlist's listener, or it keeps overwriting this one's tracks.
        playlistTracksJob?.cancel()
        playlistTracksJob = viewModelScope.launch {
            repository.getPlaylistTracks(playlist.id).collect {
                _playlistTracks.value = it
            }
        }
    }

    /** Saves a playlist's new order, given its playlist_tracks row ids top to bottom. */
    fun reorderPlaylist(rowIdsInOrder: List<Long>) {
        viewModelScope.launch { repository.reorderPlaylist(rowIdsInOrder) }
    }

    fun openQueuePanel() {
        _currentScreen.value = Screen.QUEUE
    }

    fun closeQueuePanel() {
        _isQueuePanelOpen.value = false
    }

    fun openEqualizer() {
        _isEqualizerOpen.value = true
    }

    fun closeEqualizer() {
        _isEqualizerOpen.value = false
    }

    fun openSleepTimer() {
        _isSleepTimerOpen.value = true
    }

    fun closeSleepTimer() {
        _isSleepTimerOpen.value = false
    }

    fun setTrackToAddToPlaylist(track: Track?) {
        _trackToAddToPlaylist.value = track
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    // Playback actions
    fun playTrack(track: Track) {
        playbackService?.playTrack(track)
    }

    /** [source] names where playback started (playlist, folder, album...) for the player header. */
    fun playTrackList(tracks: List<Track>, startIndex: Int, source: String, isPlaylist: Boolean = false, playlistId: Long? = null) {
        _playingFrom.value = source
        playingPlaylist = source.takeIf { isPlaylist }
        _playingPlaylistId.value = playlistId
        _playingFolder.value = source.takeIf { isPlaylist && playlistId == null }
        playbackService?.playTrackList(tracks, startIndex)
    }

    fun togglePlayPause() {
        playbackService?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackService?.seekTo(positionMs)
    }

    fun skipToNext() {
        playbackService?.skipToNext()
    }

    fun skipToPrevious() {
        playbackService?.skipToPrevious()
    }

    fun skipToQueueIndex(index: Int) {
        playbackService?.skipToQueueItem(index)
    }

    fun toggleRepeatMode() {
        playbackService?.cycleRepeatMode()
    }

    fun toggleShuffle() {
        playbackService?.toggleShuffle()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackService?.setPlaybackSpeed(speed)
        viewModelScope.launch {
            settingsManager.setPlaybackSpeed(speed)
        }
    }

    fun setAbLoop() {
        val service = playbackService ?: return
        if (service.abLoopStartMs.value == null) {
            service.setAbLoopA()
        } else if (service.abLoopEndMs.value == null) {
            service.setAbLoopB()
        } else {
            service.clearAbLoop()
        }
    }

    fun startSleepTimer(minutes: Int, stopAtEnd: Boolean) {
        playbackService?.startSleepTimer(minutes, stopAtEnd)
        closeSleepTimer()
    }

    fun cancelSleepTimer() {
        playbackService?.cancelSleepTimer()
    }

    fun removeFromQueue(index: Int) {
        playbackService?.removeFromQueue(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        playbackService?.moveQueueItem(from, to)
    }

    /** The queue's Play Next: moves an already-queued song to play after the current one, shuffle on or off. */
    fun playQueueItemNext(from: Int, to: Int) {
        playbackService?.moveQueueItem(from, to, playNext = true)
    }

    fun clearQueue() {
        playbackService?.clearQueue()
    }

    fun playNext(track: Track) {
        playbackService?.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackService?.addToQueue(track)
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    /** Saves the whole queue, in order, as a new playlist named after when it was saved. */
    fun saveQueueAsPlaylist() {
        val tracks = queue.value
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val stamp = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val id = repository.createPlaylist("Queue $stamp")
            tracks.forEach { repository.addTrackToPlaylist(id, it) }
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, name)
            _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = name)
        }
    }

    /** Saves name, description, cover and equalizer; [photo] is a newly picked cover image. */
    fun updatePlaylistDetails(old: PlaylistEntity, new: PlaylistEntity, photo: android.net.Uri?) {
        if (new.name.isBlank()) return
        viewModelScope.launch { repository.updatePlaylistDetails(old, new, photo) }
    }

    /** A song list page's remembered sort, keyed like "ALBUM:Milk"; null keeps its own order. */
    fun listSort(page: String): Flow<LibrarySortOrder?> =
        settingsManager.listSort(page).map { saved -> LibrarySortOrder.entries.firstOrNull { it.name == saved } }

    fun setListSort(page: String, sort: LibrarySortOrder?) {
        viewModelScope.launch { settingsManager.setListSort(page, sort?.name) }
    }

    /**
     * Copies a folder, album, artist... in its shown order into a new playlist of yours and opens
     * it; a song listed twice (Recently Played) goes in once. The name avoids every existing
     * playlist and folder name, which the drawer tells apart by name.
     */
    fun saveAsPlaylist(source: String, tracks: List<Track>) {
        viewModelScope.launch {
            val taken = allPlaylists.value.map { it.name }.toSet() + folderPlaylists.value.map { it.first }
            val name = generateSequence(1) { it + 1 }
                .map { n -> if (n == 1) "$source (copy)" else "$source (copy $n)" }
                .first { it !in taken }
            val id = repository.createPlaylist(name)
            tracks.distinctBy { it.id }.forEach { repository.addTrackToPlaylist(id, it) }
            _openFolderPlaylist.value = null
            openPlaylist(PlaylistEntity(id = id, name = name))
        }
    }

    /** Appends [tracks] in order (the + screen's picks). */
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch { tracks.forEach { repository.addTrackToPlaylist(playlistId, it) } }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylistPhoto(allPlaylists.value.firstOrNull { it.id == id }?.coverUri)
            repository.deletePlaylist(id)
            if (_selectedPlaylist.value?.id == id) {
                _currentScreen.value = Screen.PLAYLISTS
                _selectedPlaylist.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
            _trackToAddToPlaylist.value = null
        }
    }

    /** Removes one entry of a playlist, by its playlist_tracks row id (duplicates are allowed). */
    fun removePlaylistRow(rowId: Long) {
        viewModelScope.launch {
            repository.removePlaylistRow(rowId)
        }
    }

    // Equalizer controls
    fun setEqualizerEnabled(enabled: Boolean) {
        playbackService?.equalizerHelper?.setEnabled(enabled)
        viewModelScope.launch {
            settingsManager.setEqualizerEnabled(enabled)
        }
    }

    fun setEqualizerPreset(preset: String) {
        playbackService?.equalizerHelper?.applyPreset(preset)
        viewModelScope.launch {
            settingsManager.setEqualizerPreset(preset)
        }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMilliBels: Int) {
        playbackService?.equalizerHelper?.setBandLevel(bandIndex, levelMilliBels)
    }

    /** Current band gains in millibels as the hardware reports them (zeros when unsupported). */
    fun equalizerBandLevels(): IntArray =
        playbackService?.equalizerHelper?.getBandLevels() ?: IntArray(5)

    /** Whether the device's audio effects engine accepted the equalizer for this session. */
    fun isEqualizerSupported(): Boolean = playbackService?.equalizerHelper?.isSupported == true

    fun setBassBoostStrength(strength: Int) {
        playbackService?.equalizerHelper?.setBassBoost(strength)
        viewModelScope.launch {
            settingsManager.setBassBoostStrength(strength)
        }
    }

    fun resetStats() {
        // The song playing now counts from zero too, not with time from before the reset.
        listenTimer.take()
        viewModelScope.launch { repository.resetStats() }
    }

    fun setAccentMode(mode: AccentMode) {
        viewModelScope.launch {
            settingsManager.setAccentMode(mode)
        }
    }

    fun setShowRemainingTime(show: Boolean) {
        viewModelScope.launch {
            settingsManager.setShowRemainingTime(show)
        }
    }

    fun setBadgeOpensSongInfo(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBadgeOpensSongInfo(enabled)
        }
    }

    /** The song info sheet: the file's own details, then its plays in Moon Player. */
    suspend fun songInfo(track: Track): SongInfo {
        val info = readSongInfo(getApplication(), track)
        val history = repository.historySince(0L).first()
        val played = detailStats(history, StatsKind.TRACK, track.id, StatsRange.ALL, System.currentTimeMillis())
        val stats = played.stats.tracks.firstOrNull() ?: return info
        val minutes = stats.listenedMs / 60_000
        return info.copy(sections = info.sections + InfoSection(
            "IN MOON PLAYER",
            listOfNotNull(
                "Plays" to "${stats.plays} · ${if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"} listened",
                "Finished" to "${stats.finished} · skipped ${stats.skipped}",
                played.lastPlayed?.let {
                    "Last played" to java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
                }
            )
        ))
    }

    /** Drops the song now loaded from the queue, e.g. when its file is gone. */
    fun removeCurrentFromQueue() {
        val index = _currentIndex.value
        if (index >= 0) removeFromQueue(index)
    }

    fun setResumeOnLaunch(resume: Boolean) {
        viewModelScope.launch {
            settingsManager.setResumeOnLaunch(resume)
        }
    }

    fun setPauseOnHeadphoneDisconnect(pause: Boolean) {
        viewModelScope.launch {
            settingsManager.setPauseOnHeadphoneDisconnect(pause)
        }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so save on a scope of its own.
        saveListenTime(CoroutineScope(Dispatchers.IO))
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
        super.onCleared()
    }
}
