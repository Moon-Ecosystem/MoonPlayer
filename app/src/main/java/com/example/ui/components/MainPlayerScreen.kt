package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
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
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val queue by viewModel.queue.collectAsState()
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

    val accent = LocalAccentColor.current
    val isFavorite = currentTrack?.let { favoriteIds.contains(it.id) } ?: false

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val activePositionMs = if (isDraggingSlider) dragPositionMs.toLong() else positionMs
    val totalDuration = if (durationMs > 0) durationMs else (currentTrack?.durationMs ?: 1L)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            // Inward swipe detector on the right side to open Queue panel
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount < -35f) {
                        viewModel.openQueuePanel()
                        change.consume()
                    }
                }
            }
    ) {
        val maxW = maxWidth
        val contentWidthModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .align(Alignment.Center)

        Column(
            modifier = contentWidthModifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
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
                    Text(
                        text = (currentTrack?.album?.uppercase() ?: "MOON PLAYER").take(24),
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

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .testTag("overflow_menu_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Player options",
                            tint = PrimaryText
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(TerminalRaisedPanel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Queue (${queue.size})", color = PrimaryText) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.openQueuePanel()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Equalizer", color = PrimaryText) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.openEqualizer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer", color = PrimaryText) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.openSleepTimer()
                            }
                        )
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

            Spacer(modifier = Modifier.height(8.dp))

            // 2. TURNTABLE / JEWEL CASE ALBUM COVER (Image 3 inspired)
            val coverSize = (maxW * 0.74f).coerceIn(250.dp, 330.dp)
            TurntableAlbumCover(
                track = currentTrack,
                isPlaying = isPlaying,
                accentColor = accent,
                size = coverSize,
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.QUEUE) }
            )


            Spacer(modifier = Modifier.height(18.dp))

            // 3. TRACK TITLE, ARTIST, AND ALBUM NAME CENTERED
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = currentTrack?.title ?: "No Track Selected",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = PrimaryText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentTrack?.artist ?: "Select a song to start playback",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = currentTrack?.album ?: "Moon Player Library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Technical Audio Badge
                val techText = "[ MP3 · ${currentTrack?.bitrateKbps ?: 320} KBPS · 44.1 KHZ ]"
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

            Spacer(modifier = Modifier.height(14.dp))

            // 4. SEEK BAR WITH ELAPSED AND TOTAL/REMAINING TIME
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

                    // Speed button indicator
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = TerminalRaisedPanel,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TerminalBorder),
                        modifier = Modifier.clickable {
                            val nextSpeed = when (speed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.75f
                                else -> 1.0f
                            }
                            viewModel.setPlaybackSpeed(nextSpeed)
                        }
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val rightTimeText = if (settings.showRemainingTime) {
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
                            viewModel.setShowRemainingTime(!settings.showRemainingTime)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. PRIMARY CONTROLS ROW
            // Bookmark/Queue marker, Previous, Large Play/Pause, Next, Favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Queue toggle / marker
                IconButton(
                    onClick = { viewModel.openQueuePanel() },
                    modifier = Modifier
                        .testTag("queue_panel_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Open Queue",
                        tint = if (queue.isNotEmpty()) accent else MutedText,
                        modifier = Modifier.size(26.dp)
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
                    shape = CircleShape,
                    color = accent,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("play_pause_button")
                        .clickable { viewModel.togglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = TerminalBlack,
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

                // Favorite
                IconButton(
                    onClick = {
                        currentTrack?.let { viewModel.toggleFavorite(it.id) }
                    },
                    modifier = Modifier
                        .testTag("favorite_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite Track",
                        tint = if (isFavorite) accent else MutedText,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. SECONDARY CONTROLS ROW
            // Equalizer, Shuffle, Sleep Timer, Repeat, and optional A-B loop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Equalizer
                IconButton(
                    onClick = { viewModel.openEqualizer() },
                    modifier = Modifier
                        .testTag("equalizer_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = if (settings.equalizerEnabled) accent else MutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shuffle
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier
                        .testTag("shuffle_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accent else MutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sleep Timer
                IconButton(
                    onClick = { viewModel.openSleepTimer() },
                    modifier = Modifier
                        .testTag("sleep_timer_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerSecs != null || stopAtEnd) accent else MutedText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                        contentDescription = "Repeat Mode",
                        tint = if (isRepeatActive) accent else MutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // A-B Loop Button
                val abText = when {
                    abStart != null && abEnd != null -> "A-B"
                    abStart != null -> "A-"
                    else -> "AB"
                }
                val isAbActive = abStart != null
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isAbActive) accent.copy(alpha = 0.2f) else TerminalRaisedPanel,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isAbActive) accent else TerminalBorder
                    ),
                    modifier = Modifier
                        .clickable { viewModel.setAbLoop() }
                        .testTag("ab_loop_button")
                ) {
                    Text(
                        text = abText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAbActive) accent else MutedText,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Sleep Timer countdown banner if active
            AnimatedVisibility(visible = sleepTimerSecs != null) {
                sleepTimerSecs?.let { sec ->
                    val m = sec / 60
                    val s = sec % 60
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TerminalRaisedPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { viewModel.openSleepTimer() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SLEEP TIMER ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
