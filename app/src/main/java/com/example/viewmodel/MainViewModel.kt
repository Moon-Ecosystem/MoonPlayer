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
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.data.repository.folderNameFromTreeUri
import com.example.data.repository.isTrackInFolder
import com.example.data.settings.AccentMode
import com.example.data.settings.PlayerSettings
import com.example.data.settings.SettingsManager
import com.example.service.PlaybackService
import com.example.ui.theme.CelestialCyan
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.WarmIvory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    FOLDERS,
    PLAYLISTS,
    PLAYLIST_DETAIL,
    FAVORITES,
    HISTORY,
    STATS,
    SETTINGS,
    ABOUT
}


enum class LibrarySortOrder {
    TITLE_ASC,
    ARTIST_ASC,
    ALBUM_ASC,
    DURATION_DESC,
    DATE_ADDED_DESC
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainViewModel"
    }

    private val repository = MusicRepository(application)
    val settingsManager = SettingsManager(application)

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
        combine(_libraryFolders, allTracks) { folders, tracks ->
            // Nested folders (e.g. Music and Music/J-pop) each list every track inside them.
            folders.map { uri ->
                folderNameFromTreeUri(uri) to tracks.filter { isTrackInFolder(it.contentUri, uri) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
    val playHistory = repository.playHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val topPlayedTracks = repository.topPlayedTracks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val mostListenedTracks = repository.mostListenedTracks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val totalPlays = repository.totalPlays.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )
    val totalListenTimeMs = repository.totalListenTimeMs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0L
    )
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
        _sortOrder
    ) { tracks, query, sort ->
        var list = if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.folderPath.contains(query, ignoreCase = true)
            }
        }

        list = when (sort) {
            LibrarySortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
            LibrarySortOrder.ALBUM_ASC -> list.sortedBy { it.album.lowercase() }
            LibrarySortOrder.DURATION_DESC -> list.sortedByDescending { it.durationMs }
            LibrarySortOrder.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            observePlaybackService()
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
    }

    private fun bindPlaybackService() {
        val intent = Intent(getApplication(), PlaybackService::class.java)
        try {
            getApplication<Application>().startService(intent)
        } catch (_: Exception) {}
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observePlaybackService() {
        val service = playbackService ?: return
        viewModelScope.launch {
            service.currentTrack.collect { queued ->
                // Tracks rebuilt from playlists/history lack format, bitrate and sample rate;
                // the scanned library copy has them, so prefer it when the track is still there.
                val track = queued?.let { q -> allTracks.value.firstOrNull { it.id == q.id } ?: q }
                _currentTrack.value = track
                updateAccentColor(track)
                track?.let { repository.recordHistory(it) }
            }
        }
        viewModelScope.launch {
            service.isPlaying.collect { _isPlaying.value = it }
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
                }
            }
        }
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
    }

    fun openPlaylist(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        _currentScreen.value = Screen.PLAYLIST_DETAIL
        viewModelScope.launch {
            repository.getPlaylistTracks(playlist.id).collect {
                _playlistTracks.value = it
            }
        }
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
    fun playTrackList(tracks: List<Track>, startIndex: Int, source: String) {
        _playingFrom.value = source
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

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, name)
            _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
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

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
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

    fun setBassBoostStrength(strength: Int) {
        playbackService?.equalizerHelper?.setBassBoost(strength)
        viewModelScope.launch {
            settingsManager.setBassBoostStrength(strength)
        }
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
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
        super.onCleared()
    }
}
