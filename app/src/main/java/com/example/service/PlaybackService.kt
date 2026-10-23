package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

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
                updateCustomNotification()
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
                updateCustomNotification()
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

                // Reset A-B loop on song change
                _abLoopStartMs.value = null
                _abLoopEndMs.value = null
                updateCustomNotification()
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
            while (isActive) {
                if (player.isPlaying) {
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
                }
                delay(200)
            }
        }
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        _queue.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, tracks.lastIndex)
        _currentTrack.value = tracks[_currentIndex.value]

        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, startPositionMs)
        player.prepare()
        player.play()
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

        val mediaItem = MediaItem.Builder()
            .setUri(track.contentUri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
        player.addMediaItem(insertIdx, mediaItem)
    }

    fun addToQueue(track: Track) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ

        val mediaItem = MediaItem.Builder()
            .setUri(track.contentUri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()
        player.addMediaItem(mediaItem)
    }

    fun removeFromQueue(index: Int) {
        val currentQ = _queue.value.toMutableList()
        if (index in currentQ.indices) {
            currentQ.removeAt(index)
            _queue.value = currentQ
            player.removeMediaItem(index)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val currentQ = _queue.value.toMutableList()
        if (from in currentQ.indices && to in currentQ.indices) {
            val item = currentQ.removeAt(from)
            currentQ.add(to, item)
            _queue.value = currentQ
            player.moveMediaItem(from, to)
        }
    }

    fun clearQueue() {
        player.clearMediaItems()
        _queue.value = emptyList()
        _currentTrack.value = null
        _currentIndex.value = -1
        _currentPositionMs.value = 0L
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
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
            player.play()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> togglePlayPause()
            ACTION_SKIP_NEXT -> skipToNext()
            ACTION_SKIP_PREV -> skipToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateCustomNotification() {
        val track = _currentTrack.value ?: return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Moon Player Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification bar music controls"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val playIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rv = RemoteViews(packageName, R.layout.notification_media_custom)
        rv.setTextViewText(R.id.notif_title, track.title.uppercase())
        rv.setTextViewText(R.id.notif_subtitle, "${track.artist} · ${track.album}".uppercase())
        rv.setImageViewResource(
            R.id.notif_btn_play,
            if (player.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )
        rv.setOnClickPendingIntent(R.id.notif_btn_play, playIntent)
        rv.setOnClickPendingIntent(R.id.notif_btn_next, nextIntent)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.moon_player_logo)
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setContentIntent(contentIntent)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
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
        const val CHANNEL_ID = "moon_player_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE_PLAY = "com.example.moonplayer.ACTION_TOGGLE_PLAY"
        const val ACTION_SKIP_NEXT = "com.example.moonplayer.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.example.moonplayer.ACTION_SKIP_PREV"

        var instance: PlaybackService? = null
            private set
    }
}
