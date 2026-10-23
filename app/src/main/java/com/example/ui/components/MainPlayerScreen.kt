package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.data.artModel
import com.example.data.repository.audioFormatLabel
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.WarmIvory
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPlayerScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val playingFrom by viewModel.playingFrom.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffleMode.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerSecs by viewModel.sleepTimerRemaining.collectAsState()
    val stopAtEnd by viewModel.isStopAtEndActive.collectAsState()
    val abStart by viewModel.abLoopStartMs.collectAsState()
    val abEnd by viewModel.abLoopEndMs.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lyricsOpen by viewModel.lyricsOpen.collectAsState()

    val accent = LocalAccentColor.current
    val isFavorite = currentTrack?.let { favoriteIds.contains(it.id) } ?: false

    var showOverflowMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val contentWidthModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .align(Alignment.Center)

        // No scrolling: the cover shrinks to fit short screens, and weighted spacers share out
        // whatever height is left so the controls never pool at one end.
        Column(
            modifier = contentWidthModifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP APP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .testTag("hamburger_menu_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = PrimaryText
                    )
                }

                // Centered context title and track index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Where the queue came from (playlist, folder, album...); album if unknown.
                    Text(
                        text = when {
                            currentTrack == null -> "MOON PLAYER"
                            playingFrom != null -> "FROM: ${playingFrom!!.uppercase()}"
                            else -> currentTrack!!.album.uppercase()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val indexText = if (queue.isNotEmpty() && currentIndex >= 0) {
                        String.format("[%02d / %02d]", currentIndex + 1, queue.size)
                    } else {
                        "[ -- / -- ]"
                    }
                    Text(
                        text = indexText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Equalizer, sleep timer, A-B loop and speed live here; the dot says one is on.
                val sleepActive = sleepTimerSecs != null || stopAtEnd
                // The equalizer is on by default; only a non-flat curve counts as "changed".
                val eqActive = settings.equalizerEnabled && settings.equalizerPreset != "Flat"
                val anyToolActive = eqActive || sleepActive || abStart != null || speed != 1.0f
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .testTag("overflow_menu_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = if (anyToolActive) "Player options, some active" else "Player options",
                            tint = PrimaryText
                        )
                    }
                    if (anyToolActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(7.dp)
                                .background(accent, CircleShape)
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(TerminalRaisedPanel)
                    ) {
                        PlayerMenuItem(
                            label = "Equalizer",
                            status = if (settings.equalizerEnabled) settings.equalizerPreset else "Off",
                            active = eqActive
                        ) {
                            showOverflowMenu = false
                            viewModel.openEqualizer()
                        }
                        PlayerMenuItem(
                            label = "Sleep timer",
                            status = when {
                                sleepTimerSecs != null -> sleepTimerSecs!!.let { String.format("%02d:%02d", it / 60, it % 60) }
                                stopAtEnd -> "End of song"
                                else -> "Off"
                            },
                            active = sleepActive
                        ) {
                            showOverflowMenu = false
                            viewModel.openSleepTimer()
                        }
                        // A-B and speed step through their values in place, so the menu stays open.
                        PlayerMenuItem(
                            label = "A-B loop",
                            status = when {
                                abStart != null && abEnd != null -> "On · tap to clear"
                                abStart != null -> "Tap to set B"
                                else -> "Tap to set A"
                            },
                            active = abStart != null
                        ) { viewModel.setAbLoop() }
                        PlayerMenuItem(label = "Playback speed", status = "${speed}x", active = speed != 1.0f) {
                            viewModel.setPlaybackSpeed(
                                when (speed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.75f
                                    else -> 1.0f
                                }
                            )
                        }
                        HorizontalDivider(color = TerminalBorder)
                        DropdownMenuItem(
                            text = { Text("Browse Library", color = PrimaryText) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(Screen.LIBRARY)
                            }
                        )
                        currentTrack?.let { trk ->
                            DropdownMenuItem(
                                text = { Text("Add to Playlist", color = PrimaryText) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setTrackToAddToPlaylist(trk)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f).heightIn(min = 4.dp))

            // 2. SQUARE ALBUM COVER: full content width, unless the screen is too short for that
            // plus the ~400dp of title, seek bar, controls and Up next below it.
            val coverSize = (maxW.coerceAtMost(520.dp) - 40.dp)
                .coerceAtMost(maxH - 400.dp)
                .coerceAtLeast(140.dp)
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(TerminalRaisedPanel)
                    .border(
                        1.5.dp,
                        if (lyricsOpen) lerp(TerminalBorder, accent, 0.4f) else TerminalBorder,
                        RoundedCornerShape(16.dp)
                    )
                    // Tap the cover for lyrics; while they show, taps belong to the lyric lines.
                    .clickable(enabled = currentTrack != null && !lyricsOpen, onClickLabel = "Show lyrics") {
                        viewModel.openLyrics()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Lyrics replace the artwork entirely; the corner button brings the cover back.
                Crossfade(targetState = lyricsOpen && currentTrack != null, animationSpec = tween(220), label = "cover") { showLyrics ->
                    if (showLyrics) {
                        // A deep tint of the song's accent: its colour, with white text that stays readable.
                        Box(modifier = Modifier.fillMaxSize().background(lerp(TerminalRaisedPanel, accent, 0.32f))) {
                            LyricsPanel(viewModel)
                            IconButton(
                                onClick = { viewModel.closeLyrics() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .testTag("close_lyrics_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Show album art", tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        CoverArt(currentTrack, accent)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f).heightIn(min = 12.dp))

            // 3. TITLE, ARTIST · ALBUM AND FAVOURITE (or how to start, when nothing's loaded)
            if (currentTrack == null) NothingPlayingPrompt(viewModel) else Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            ),
                            color = PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = accent)) { append(currentTrack?.artist.orEmpty()) }
                                currentTrack?.album?.takeIf { it.isNotBlank() }?.let {
                                    withStyle(SpanStyle(color = MutedText)) { append(" · $it") }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { currentTrack?.let { viewModel.toggleFavorite(it.id) } },
                        modifier = Modifier
                            .testTag("favorite_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                            tint = if (isFavorite) accent else MutedText,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Technical Audio Badge
                // Only what the file actually reported; unknown values are left out instead of faked.
                val techText = currentTrack?.let { t ->
                    listOfNotNull(
                        t.mimeType.takeIf { it.isNotBlank() }?.let(::audioFormatLabel),
                        t.bitrateKbps.takeIf { it > 0 }?.let { "$it KBPS" },
                        t.sampleRateHz.takeIf { it > 0 }?.let { "%.1f KHZ".format(it / 1000f) }
                    ).joinToString(" · ")
                }.orEmpty().ifBlank { "--" }.let { "[ $it ]" }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = TerminalRaisedPanel,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TerminalBorder)
                ) {
                    Text(
                        text = techText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f).heightIn(min = 10.dp))

            // 4. SEEK BAR WITH ELAPSED AND TOTAL/REMAINING TIME
            SeekBarSection(
                viewModel = viewModel,
                fallbackDurationMs = currentTrack?.durationMs ?: 1L,
                showRemainingTime = settings.showRemainingTime
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 5. CONTROLS: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier
                        .testTag("shuffle_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (isShuffle) "Shuffle on" else "Shuffle off",
                        tint = if (isShuffle) accent else MutedText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier
                        .testTag("previous_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = PrimaryText,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Large Play/Pause Button
                Surface(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = accent,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = com.example.ui.theme.buttonContentColorFor(accent),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier
                        .testTag("next_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = PrimaryText,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat Mode
                IconButton(
                    onClick = { viewModel.toggleRepeatMode() },
                    modifier = Modifier
                        .testTag("repeat_button")
                        .minimumInteractiveComponentSize()
                ) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (isRepeatActive) accent else MutedText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f).heightIn(min = 12.dp))

            // 6. UP NEXT: the next song; tap (or swipe up) for the whole queue
            if (currentTrack != null) {
                UpNextCard(
                    nextTitle = when {
                        isShuffle -> "Shuffle on · ${queue.size} songs"
                        currentIndex + 1 < queue.size -> queue[currentIndex + 1].let { "${it.title} · ${it.artist}" }
                        repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty() -> queue.first().let { "${it.title} · ${it.artist}" }
                        else -> "End of queue"
                    },
                    onClick = { viewModel.navigateTo(Screen.QUEUE) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// Isolated so the 200ms position ticks recompose only the seek bar, not the whole player.
@Composable
private fun SeekBarSection(
    viewModel: MainViewModel,
    fallbackDurationMs: Long,
    showRemainingTime: Boolean
) {
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val accent = LocalAccentColor.current

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val activePositionMs = if (isDraggingSlider) dragPositionMs.toLong() else positionMs
    val totalDuration = if (durationMs > 0) durationMs else fallbackDurationMs

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val sliderValue = if (totalDuration > 0) {
            (activePositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = sliderValue,
            onValueChange = { frac ->
                isDraggingSlider = true
                dragPositionMs = frac * totalDuration
            },
            onValueChangeFinished = {
                viewModel.seekTo(dragPositionMs.toLong())
                isDraggingSlider = false
            },
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = TerminalBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_seek_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Track.formatDuration(activePositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace
            )

            val rightTimeText = if (showRemainingTime) {
                val remaining = (totalDuration - activePositionMs).coerceAtLeast(0L)
                "-${Track.formatDuration(remaining)}"
            } else {
                Track.formatDuration(totalDuration)
            }

            Text(
                text = rightTimeText,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    viewModel.setShowRemainingTime(!showRemainingTime)
                }
            )
        }
    }
}

/** The cover square's artwork, over a placeholder shown when a track has none. */
@Composable
private fun CoverArt(track: Track?, accent: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = TerminalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = (track?.album ?: "MOON PLAYER").take(24).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        track?.let {
            AsyncImage(
                model = it.artModel,
                contentDescription = "Album Art for ${it.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A player-menu row: the tool's name, its current state on the right (accent when on). */
@Composable
private fun PlayerMenuItem(label: String, status: String, active: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = PrimaryText, modifier = Modifier.padding(end = 24.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) LocalAccentColor.current else MutedText
                )
            }
        },
        onClick = onClick
    )
}

/** The next song at the foot of the player; opens the queue. */
@Composable
private fun UpNextCard(nextTitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("up_next_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MutedText, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = nextTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open queue", tint = MutedText)
        }
    }
}

/**
 * Shown in place of the track details when nothing is loaded: points to a playlist or the full
 * song list, or to adding a folder when the library is still empty.
 */
@Composable
private fun NothingPlayingPrompt(viewModel: MainViewModel) {
    val allTracks by viewModel.allTracks.collectAsState()
    val accent = LocalAccentColor.current
    val pickFolder = rememberFolderPicker { uri, flags -> viewModel.addFolder(uri, flags) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Nothing playing",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (allTracks.isEmpty()) "Add a music folder to get started" else "Pick something to play",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))

        val filled = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = com.example.ui.theme.buttonContentColorFor(accent)
        )
        if (allTracks.isEmpty()) {
            Button(onClick = pickFolder, colors = filled) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Music Folder", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.navigateTo(Screen.PLAYLISTS) }, colors = filled) {
                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Playlists", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.LIBRARY) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Songs", color = PrimaryText)
                }
            }
        }
    }
}
