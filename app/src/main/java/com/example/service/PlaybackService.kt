package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.data.fileExists
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shuffle play order (list indices) after inserting an item at list index [inserted]: later
 * indices shift up, and the new one plays right after [after] (an index before the insert), or
 * last when [after] is null.
 */
internal fun shuffleOrderWithInsert(order: List<Int>, inserted: Int, after: Int?): IntArray {
    val shifted = order.map { if (it >= inserted) it + 1 else it }.toMutableList()
    val at = if (after == null) shifted.size else shifted.indexOf(if (after >= inserted) after + 1 else after) + 1
    shifted.add(at, inserted)
    return shifted.toIntArray()
}

/** Shuffle play order after moving list item [from] to [to], with it playing right after [current]. */
internal fun shuffleOrderWithMove(order: List<Int>, from: Int, to: Int, current: Int): IntArray {
    // newIndex[old]: where each list item ends up after the move.
    val moved = order.indices.toMutableList().apply { add(to, removeAt(from)) }
    val newIndex = IntArray(order.size).also { arr -> moved.forEachIndexed { new, old -> arr[old] = new } }
    val result = order.filter { it != from }.map { newIndex[it] }.toMutableList()
    result.add(result.indexOf(newIndex[current]) + 1, to)
    return result.toIntArray()
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val equalizerHelper = EqualizerHelper()

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    // Reactive states for UI
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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode = _isShuffleMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _sleepTimerSecondsRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerSecondsRemaining = _sleepTimerSecondsRemaining.asStateFlow()

    private val _isStopAtEndActive = MutableStateFlow(false)
    val isStopAtEndActive = _isStopAtEndActive.asStateFlow()

    // A-B loop state
    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    val abLoopStartMs = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    val abLoopEndMs = _abLoopEndMs.asStateFlow()

    // The current song's file couldn't be opened because it's gone (moved, renamed or deleted).
    private val _currentFileMissing = MutableStateFlow(false)
    val currentFileMissing = _currentFileMissing.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Init Equalizer
        equalizerHelper.init(player.audioSessionId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Media3's default small icon is a generic note; use the app's mascot silhouette instead.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
            // The UI talks to the service via LocalBinder, so no controller ever calls
            // onGetSession; register explicitly so Media3 posts the foreground media notification.
            .also { addSession(it) }

        setupPlayerListener()
        startProgressTracker()

        instance = this
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    if (_isStopAtEndActive.value) {
                        player.pause()
                        _isStopAtEndActive.value = false
                        _sleepTimerSecondsRemaining.value = null
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                _currentIndex.value = index
                val currentQ = _queue.value
                if (index in currentQ.indices) {
                    _currentTrack.value = currentQ[index]
                    _durationMs.value = currentQ[index].durationMs
                }
                _currentPositionMs.value = 0L
                _currentFileMissing.value = false

                // Reset A-B loop on song change
                _abLoopStartMs.value = null
                _abLoopEndMs.value = null
            }

            // Media3 reports a gone file on a folder URI as a generic I/O error, so ask the provider.
            override fun onPlayerError(error: PlaybackException) {
                val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
                if (error.errorCode !in 2000..2999) return // ERROR_CODE_IO_*
                serviceScope.launch {
                    val missing = withContext(Dispatchers.IO) { !fileExists(this@PlaybackService, uri) }
                    if (player.currentMediaItem?.localConfiguration?.uri == uri) _currentFileMissing.value = missing
                }
            }

            override fun onRepeatModeChanged(newRepeatMode: Int) {
                _repeatMode.value = newRepeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleMode.value = shuffleModeEnabled
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            // Stops while paused instead of waking every 200ms; onIsPlayingChanged restarts it.
            while (isActive && player.isPlaying) {
                val pos = player.currentPosition
                _currentPositionMs.value = pos
                _durationMs.value = player.duration.coerceAtLeast(0L)

                // A-B loop check
                val loopEnd = _abLoopEndMs.value
                val loopStart = _abLoopStartMs.value
                if (loopStart != null && loopEnd != null && loopEnd > loopStart) {
                    if (pos >= loopEnd) {
                        player.seekTo(loopStart)
                    }
                }
                delay(200)
            }
        }
    }

    /** [playWhenReady] false loads the queue paused, e.g. when restoring the last session. */
    fun playTrackList(
        tracks: List<Track>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true
    ) {
        if (tracks.isEmpty()) return
        _queue.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, tracks.lastIndex)
        _currentTrack.value = tracks[_currentIndex.value]

        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, startPositionMs)
        player.prepare()
        if (playWhenReady) player.play()
        _currentPositionMs.value = startPositionMs
    }

    fun playTrack(track: Track) {
        val currentQ = _queue.value.toMutableList()
        val index = currentQ.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            skipToQueueItem(index)
        } else {
            // Append and play
            currentQ.add(track)
            playTrackList(currentQ, currentQ.lastIndex)
        }
    }

    fun playNext(track: Track) {
        val currentQ = _queue.value.toMutableList()
        val currIdx = player.currentMediaItemIndex
        val insertIdx = if (currIdx >= 0 && currIdx < currentQ.size) currIdx + 1 else currentQ.size
        currentQ.add(insertIdx, track)
        _queue.value = currentQ
        insertKeepingShuffle(insertIdx, track, after = currIdx.takeIf { it >= 0 })
    }

    fun addToQueue(track: Track) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ
        insertKeepingShuffle(currentQ.lastIndex, track, after = null)
    }

    /**
     * Adds the item at [index]. With shuffle on, the player would slot it into a random spot of
     * its play order, so Play Next could come much later; it goes right after [after] instead
     * (null: at the end of the order, for Add to Queue).
     */
    @OptIn(UnstableApi::class)
    private fun insertKeepingShuffle(index: Int, track: Track, after: Int?) {
        val order = if (player.shuffleModeEnabled) shuffledOrder() else null
        player.addMediaItem(index, track.toMediaItem())
        order?.let { player.setShuffleOrder(DefaultShuffleOrder(shuffleOrderWithInsert(it, index, after), System.nanoTime())) }
    }

    /** The list indices in the order shuffle plays them. */
    private fun shuffledOrder(): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val order = mutableListOf<Int>()
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        return order
    }

    // Embedded ID3 artwork is merged in by ExoPlayer at playback; artworkUri covers tracks with
    // a separate cover so the notification and lock screen show it before decoding starts.
    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(contentUri)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri?.let(Uri::parse))
                .build()
        )
        .build()

    fun removeFromQueue(index: Int) {
        val currentQ = _queue.value.toMutableList()
        if (index in currentQ.indices) {
            currentQ.removeAt(index)
            _queue.value = currentQ
            player.removeMediaItem(index)
            // Removing an earlier song shifts the playing one without a media-item transition.
            _currentIndex.value = player.currentMediaItemIndex
            // The last song gone: nothing is loaded, so show nothing rather than the removed song.
            if (currentQ.isEmpty()) clearQueue()
        }
    }

    /**
     * Swaps queued songs to [library]'s current address for the same song id, e.g. after their
     * folder was replaced by its subfolders and the old address lost its access.
     */
    fun relinkQueue(library: Map<String, Track>) {
        val q = _queue.value
        val fixed = q.map { t -> library[t.id]?.takeIf { it.contentUri != t.contentUri } ?: t }
        if (fixed == q) return
        _queue.value = fixed
        fixed.forEachIndexed { i, t ->
            if (t !== q[i] && i < player.mediaItemCount) player.replaceMediaItem(i, t.toMediaItem())
        }
    }

    /** [playNext]: the queue's Play Next, which with shuffle on also makes it next in play order. */
    @OptIn(UnstableApi::class)
    fun moveQueueItem(from: Int, to: Int, playNext: Boolean = false) {
        val currentQ = _queue.value.toMutableList()
        if (from in currentQ.indices && to in currentQ.indices) {
            val item = currentQ.removeAt(from)
            currentQ.add(to, item)
            _queue.value = currentQ
            val current = player.currentMediaItemIndex
            val order = if (playNext && player.shuffleModeEnabled && current >= 0) shuffledOrder() else null
            player.moveMediaItem(from, to)
            order?.let { player.setShuffleOrder(DefaultShuffleOrder(shuffleOrderWithMove(it, from, to, current), System.nanoTime())) }
            // The playing song keeps playing but may now sit at a different position.
            _currentIndex.value = player.currentMediaItemIndex
        }
    }

    fun clearQueue() {
        player.clearMediaItems()
        _queue.value = emptyList()
        _currentTrack.value = null
        _currentIndex.value = -1
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _currentFileMissing.value = false
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            play()
        }
    }

    /**
     * play() that also works on an idle player. Dismissing the paused notification makes Media3
     * stop the player (STATE_IDLE, queue kept), and play() alone does nothing until it's prepared
     * again; media buttons prepare by themselves, the app's own buttons need this.
     */
    private fun play() {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        if (player.currentPosition > 3000L || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun skipToQueueItem(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            play()
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun toggleShuffle() {
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _isShuffleMode.value = newShuffle
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setAbLoopA() {
        val current = player.currentPosition
        _abLoopStartMs.value = current
        _abLoopEndMs.value = null
    }

    fun setAbLoopB() {
        val current = player.currentPosition
        val start = _abLoopStartMs.value
        if (start != null && current > start) {
            _abLoopEndMs.value = current
        } else {
            // clear
            _abLoopStartMs.value = null
            _abLoopEndMs.value = null
        }
    }

    fun clearAbLoop() {
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
    }

    fun startSleepTimer(minutes: Int, stopAtEnd: Boolean = false) {
        sleepTimerJob?.cancel()
        _isStopAtEndActive.value = stopAtEnd

        if (stopAtEnd && minutes == 0) {
            _sleepTimerSecondsRemaining.value = null
            return
        }

        val totalSeconds = minutes * 60
        _sleepTimerSecondsRemaining.value = totalSeconds

        sleepTimerJob = serviceScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                _sleepTimerSecondsRemaining.value = remaining
            }
            if (isActive) {
                player.pause()
                _sleepTimerSecondsRemaining.value = null
                _isStopAtEndActive.value = false
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerSecondsRemaining.value = null
        _isStopAtEndActive.value = false
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // System UI, Bluetooth and scrobblers bind with the session actions and must get Media3's
    // binder; only the app's own plain bind gets the local binder.
    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent) ?: binder
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        equalizerHelper.release()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        if (instance == this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: PlaybackService? = null
            private set
    }
}
