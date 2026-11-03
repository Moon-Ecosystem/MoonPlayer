package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.artModel
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
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Playlist & Queue Screen matching Image 2 (PlaylistQueue_73a31d54.jpeg).
 * Features rich header with album thumbnail, title, play/shuffle pills,
 * numbered tracks with equalizer bars on active song, under a pinned now-playing header.
 */
@Composable
fun PlaylistQueueScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffleMode.collectAsState()
    val playingFrom by viewModel.playingFrom.collectAsState()
    val accent = LocalAccentColor.current

    // Played songs stay in the queue, folded into a "Played" section by default. Search opens
    // from the Queue row only when asked, and filters the view, never the queue.
    var searching by remember { mutableStateOf(false) }
    val searchState = rememberTextFieldState()
    val search = searchState.text.toString()
    var showPlayed by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    fun closeSearch() {
        searchState.clearText()
        searching = false
    }
    BackHandler(enabled = searching) { closeSearch() }
    // The pager keeps this page composed under the player, so leaving the queue has to drop the
    // search and its keyboard itself.
    val screen by viewModel.currentScreen.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(screen) {
        if (screen != Screen.QUEUE && searching) {
            keyboard?.hide()
            closeSearch()
        }
    }
    val totalDurationMs = remember(queue) { queue.sumOf { it.durationMs } }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
    ) {
        // TOP BAR: Back button & Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("queue_back_button")
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back to player",
                    tint = PrimaryText
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (playingFrom != null) "PLAYING FROM" else "PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = playingFrom ?: "Queue",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Balances the back button so the title stays centred (queue search lives below).
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Rows in the order songs will play: the queue's own order, or the shuffled order with
        // shuffle on (shuffle never reorders the queue list). Each row keeps its queue index and
        // a stable key ("id#n" for the n-th copy of a song) so it keeps its key while dragged.
        val playOrder by viewModel.playOrder.collectAsState()
        var rows by remember(queue, playOrder, isShuffle) {
            val keyed = keyedEntries(queue)
            val positions = if (isShuffle && playOrder.size == queue.size) playOrder else queue.indices.toList()
            mutableStateOf(positions.map { IndexedValue(it, keyed[it]) })
        }
        val haptic = LocalHapticFeedback.current
        val listState = rememberLazyListState()
        // The local rows move instantly; each step goes to the player: a reorder of the play
        // order with shuffle on, of the queue itself otherwise.
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            val fromPos = rows.indexOfFirst { it.value.first == from.key }
            val toPos = rows.indexOfFirst { it.value.first == to.key }
            if (fromPos >= 0 && toPos >= 0) {
                rows = rows.toMutableList().apply { add(toPos, removeAt(fromPos)) }
                if (isShuffle) viewModel.movePlayOrder(fromPos, toPos) else viewModel.moveQueueItem(fromPos, toPos)
            }
        }
        // Without shuffle a row's queue index is its position, which a drag changes at once.
        val displayed = if (isShuffle) rows else rows.mapIndexed { pos, row -> IndexedValue(pos, row.value) }

        // NOW PLAYING: pinned above the list; tap returns to the full player.
        currentTrack?.let { track ->
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = TerminalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("queue_now_playing")
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QueueThumb(track, size = 52.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            onClick = { viewModel.togglePlayPause() },
                            shape = CircleShape,
                            color = accent,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("queue_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = com.example.ui.theme.buttonContentColorFor(accent),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    QueueProgressLine(viewModel, accent)
                }
            }
        }

        // While searching, the Queue row itself becomes the field, so nothing below it moves.
        if (searching) {
            // Same build as MoonKeyboard's search: BasicTextField on a TextFieldState in a rounded row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalRaisedPanel)
                    .border(1.dp, accent, RoundedCornerShape(8.dp))
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MutedText)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (search.isEmpty()) Text("Search the queue...", color = MutedText, fontSize = 15.sp)
                    BasicTextField(
                        state = searchState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus)
                            .testTag("queue_search_input"),
                        textStyle = TextStyle(color = PrimaryText, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        lineLimits = TextFieldLineLimits.SingleLine
                    )
                }
                IconButton(onClick = { closeSearch() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Close search", tint = MutedText)
                }
            }
            // Opens with the keyboard up, ready to type.
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // QUEUE heading, with the actions that apply to the whole queue
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "${queue.size} songs in queue · ${Track.formatDuration(totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            IconButton(
                onClick = { searching = true },
                enabled = queue.isNotEmpty(),
                modifier = Modifier.testTag("queue_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search the queue",
                    tint = PrimaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { viewModel.toggleShuffle() },
                modifier = Modifier.testTag("queue_shuffle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = if (isShuffle) "Shuffle on" else "Shuffle off",
                    tint = if (isShuffle) accent else MutedText,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = {
                    viewModel.saveQueueAsPlaylist()
                    Toast.makeText(context, "Queue saved as a playlist", Toast.LENGTH_SHORT).show()
                },
                enabled = queue.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Save queue as playlist",
                    tint = MutedText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            val query = search.trim()
            fun matches(t: Track) = query.isBlank() ||
                t.title.contains(query, ignoreCase = true) ||
                t.artist.contains(query, ignoreCase = true) ||
                t.album.contains(query, ignoreCase = true)

            // Played and Up next follow the play order around the current song, which sits in
            // the card above and is skipped in the list.
            val currentPos = displayed.indexOfFirst { it.index == currentIndex }
            val played = if (currentPos > 0) displayed.take(currentPos) else emptyList()
            val upcoming = displayed.drop(currentPos + 1)
            val playedIndices = played.mapTo(HashSet()) { it.index }
            val filtering = query.isNotBlank()

            // Play Next for any song but the current one and the one already next in play order.
            // With shuffle on, the move lands beside the current song and the service also makes
            // it next in the shuffled order.
            fun playNextAction(index: Int): (() -> Unit)? {
                if (currentIndex < 0 || index == currentIndex || upcoming.firstOrNull()?.index == index) return null
                val target = if (isShuffle) (if (index < currentIndex) currentIndex else currentIndex + 1)
                    else playNextTarget(index, currentIndex) ?: return null
                return { viewModel.playQueueItemNext(index, target) }
            }

            val visible = if (filtering) {
                // Search reaches the whole queue, played songs included (shown dimmed).
                displayed.filter { (i, e) -> i != currentIndex && matches(e.second) }.also { hits ->
                    item(key = "search_count") {
                        Text(
                            text = "${hits.size} ${if (hits.size == 1) "match" else "matches"} in the queue of ${queue.size} · played songs included",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            } else {
                // Played songs are a section of their own, so it's plain they're still in the queue.
                if (played.isNotEmpty()) {
                    item(key = "played_section") {
                        PlayedSectionRow(
                            tracks = played.map { it.value.second },
                            expanded = showPlayed,
                            onToggle = { showPlayed = !showPlayed }
                        )
                    }
                }
                upcoming
            }
            if (!filtering && showPlayed) {
                items(played, key = { it.value.first }) { (index, entry) ->
                    val (key, track) = entry
                    ReorderableItem(reorderState, key = key) { isDragging ->
                        NumberedQueueTrackRow(
                            track = track,
                            index = index + 1,
                            isCurrent = false,
                            isPlaying = false,
                            isPlayed = true,
                            isDragging = isDragging,
                            accentColor = accent,
                            dragHandle = Modifier.longPressDraggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            ),
                            onPlay = { viewModel.skipToQueueIndex(index) },
                            onPlayNext = playNextAction(index),
                            onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                            onRemove = { viewModel.removeFromQueue(index) }
                        )
                    }
                }
            }
            if (!filtering && upcoming.isNotEmpty()) {
                item(key = "up_next_label") {
                    Text(
                        text = "UP NEXT · ${upcoming.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                queue.isEmpty() -> "> QUEUE IS EMPTY"
                                query.isNotBlank() -> "> NO MATCHES IN QUEUE"
                                else -> "> NOTHING UP NEXT"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                    }
                }
            }
            items(visible, key = { it.value.first }) { (index, entry) ->
                val (key, track) = entry
                ReorderableItem(reorderState, key = key) { isDragging ->
                        NumberedQueueTrackRow(
                            track = track,
                            index = index + 1,
                            isCurrent = false,
                            isPlaying = false,
                            isPlayed = index in playedIndices,
                            isDragging = isDragging,
                            accentColor = accent,
                            // Reordering is off while searching: filtered rows don't map 1:1 to queue
                            // positions. Long-press first so a scroll starting on the handle stays a scroll.
                            dragHandle = if (query.isBlank()) Modifier.longPressDraggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            ) else null,
                            onPlay = { viewModel.skipToQueueIndex(index) },
                            onPlayNext = playNextAction(index),
                            onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                            onRemove = { viewModel.removeFromQueue(index) }
                        )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Queue position that puts the song at [from] right after the current one, or null when it is
 * the current song or already next. Moving an earlier song shifts the current one up by one.
 */
internal fun playNextTarget(from: Int, current: Int): Int? = when {
    current < 0 || from == current || from == current + 1 -> null
    from > current -> current + 1
    else -> current
}

/**
 * The played songs as one row: their covers stacked (latest first), how many, and that they're
 * still in the queue. Tapping it opens or closes the list of them below.
 */
@Composable
private fun PlayedSectionRow(tracks: List<Track>, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("queue_played_section")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                tracks.takeLast(3).reversed().forEachIndexed { i, track ->
                    Box(modifier = Modifier.padding(start = (i * 18).dp)) { QueueThumb(track, size = 30.dp) }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Played · ${tracks.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "Still in the queue · tap to ${if (expanded) "hide" else "see or replay"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MutedText
            )
        }
    }
}

/** Rounded cover thumbnail with the app logo underneath for songs without art. */
@Composable
private fun QueueThumb(track: Track, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalRaisedPanel)
            .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.moon_player_logo_thumb),
            contentDescription = null,
            modifier = Modifier.size(size * 0.6f)
        )
        AsyncImage(
            model = track.artModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Reads position only inside the progress lambda, so ticks redraw the line, not the screen.
@Composable
private fun QueueProgressLine(viewModel: MainViewModel, accent: androidx.compose.ui.graphics.Color) {
    val position = viewModel.currentPositionMs.collectAsState()
    val duration = viewModel.durationMs.collectAsState()
    LinearProgressIndicator(
        progress = {
            val d = duration.value
            if (d > 0) (position.value.toFloat() / d).coerceIn(0f, 1f) else 0f
        },
        color = accent,
        trackColor = TerminalBorder,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(2.dp)
    )
}

@Composable
fun NumberedQueueTrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    isPlayed: Boolean = false,
    isDragging: Boolean = false,
    dragHandle: Modifier? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent || isDragging) TerminalSurfaceElevated else androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            // Already-played songs are dimmed so the upcoming ones read first.
            .alpha(if (isPlayed) 0.55f else 1f)
            .clickable { onPlay() }
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Cover thumbnail, or equalizer bars on the playing song
                if (isCurrent && isPlaying) {
                    EqualizerWaveIndicator(accentColor = accentColor, modifier = Modifier.width(44.dp))
                } else {
                    QueueThumb(track, size = 44.dp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) accentColor else PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} · ${track.formattedDuration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            dragHandle?.let { handle ->
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MutedText,
                    modifier = handle.size(40.dp).padding(8.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp).minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = MutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(TerminalRaisedPanel)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play", color = PrimaryText) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    onPlayNext?.let { playNext ->
                        DropdownMenuItem(
                            text = { Text("Play Next", color = PrimaryText) },
                            onClick = {
                                showMenu = false
                                playNext()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = PrimaryText) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Queue", color = PrimaryText) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Animated equalizer bar indicator for currently playing track.
 */
@Composable
fun EqualizerWaveIndicator(
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val h1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxSize(h1)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxSize(h2)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxSize(h3)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Pairs each queue entry with a key that is unique even when a song is queued twice:
 * "id#0" for its first copy, "id#1" for the second, and so on.
 */
internal fun keyedEntries(queue: List<Track>): List<Pair<String, Track>> {
    val seen = HashMap<String, Int>()
    return queue.map { track ->
        val n = seen.merge(track.id, 1, Int::plus)!! - 1
        "${track.id}#$n" to track
    }
}
