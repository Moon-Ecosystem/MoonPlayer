package com.example.ui.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.LyricsLookup
import com.example.data.lyrics.currentLineIndex
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRaisedPanel
import kotlinx.coroutines.delay
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.MainViewModel.LyricsUiState
import com.example.viewmodel.Screen

/**
 * Lyrics drawn inside the player's cover square: synced lines follow the song and seek on
 * tap. Back returns to the cover.
 */
@Composable
fun LyricsPanel(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.lyricsState.collectAsState()
    val showChip = viewModel.settings.collectAsState().value.lyricsSourceChip
    val accent = LocalAccentColor.current
    // The player page stays composed under the queue, so only claim Back while it's showing.
    val onPlayer = viewModel.currentScreen.collectAsState().value == Screen.PLAYER
    BackHandler(enabled = onPlayer) { viewModel.closeLyrics() }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Lines, pills and buttons take their own taps first; a tap on empty space lands
            // here and brings the album art back.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Show album art"
            ) { viewModel.closeLyrics() }
            .testTag("lyrics_panel")
    ) {
        when (val s = state) {
            LyricsUiState.Loading -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
            is LyricsUiState.Done -> when (val lookup = s.lookup) {
                is LyricsLookup.Found -> {
                    // Pills are what you read: one per kind, only when there's more than one (or
                    // only a translation, so it's never mistaken for the real words).
                    val showSwitch = lookup.kinds.size > 1 || lookup.kind == LyricsKind.TRANSLATION
                    // Room for the pills only when they're there.
                    val top = if (showSwitch) 42.dp else 14.dp
                    var sheetOpen by remember { mutableStateOf(false) }
                    // Keyed by kind and source, so switching starts the new one at the sung line.
                    androidx.compose.runtime.key(lookup.kind, lookup.source) {
                        val guard = remember { LyricsScrollGuard() }
                        val label = sourceLine(lookup)
                        when (val lyrics = lookup.lyrics) {
                            is Lyrics.Synced -> SyncedLyrics(viewModel, lyrics.lines, guard, top) { SourceLabel(label) { sheetOpen = true } }
                            is Lyrics.Plain -> PlainLyrics(lyrics.text, guard, top) { SourceLabel(label) { sheetOpen = true } }
                        }
                        // Where it's from, over the lyrics only when there's a choice: for 3 s when
                        // they open, and again while the user scrolls. The label at the end stays,
                        // also when this line is turned off in Settings › Lyrics.
                        if (lookup.sources.size > 1 && showChip) {
                            var chipVisible by remember { mutableStateOf(true) }
                            LaunchedEffect(guard.scrollTick) {
                                chipVisible = true
                                delay(3_000)
                                chipVisible = false
                            }
                            AnimatedVisibility(
                                visible = chipVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp)
                            ) {
                                SourceChip(label) { sheetOpen = true }
                            }
                        }
                    }
                    if (showSwitch) {
                        LyricsKindSwitch(
                            available = lookup.kinds,
                            shown = lookup.kind,
                            onPick = { viewModel.showLyricsKind(it) },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp)
                        )
                    }
                    if (sheetOpen) LyricsSourceSheet(viewModel, lookup, canLookUp = s.lrclibAllowed) { sheetOpen = false }
                }
                LyricsLookup.NotFound -> LyricsMessage(
                    title = "No lyrics found",
                    detail = when {
                        s.onlineEnabled -> "Add a .lrc file with the same name next to the song to use your own."
                        s.lrclibAllowed -> "Look this song up on LRCLIB (sends its title, artist, album and length), or add a .lrc file " +
                            "with the same name next to it. Settings › Lyrics can look every song up automatically."
                        else -> "Add a .lrc file with the same name next to the song. LRCLIB is off in Settings › Lyrics."
                    },
                    // LRCLIB turned off: no button, nothing goes online.
                    actionLabel = when {
                        s.onlineEnabled -> "Try again"
                        s.lrclibAllowed -> "Look up on LRCLIB"
                        else -> null
                    },
                    // With automatic lookup off, this asks for this song only; the setting stays off.
                    onAction = { if (s.onlineEnabled) viewModel.retryLyrics() else viewModel.lookUpLrclib() },
                    modifier = Modifier.align(Alignment.Center)
                )
                is LyricsLookup.Unavailable -> LyricsMessage(
                    title = if (lookup.busy) "LRCLIB is busy" else "Couldn't reach LRCLIB",
                    detail = if (lookup.busy) {
                        "Its servers are overloaded right now. Try again in a minute."
                    } else {
                        "Check your connection and try again."
                    },
                    actionLabel = "Try again",
                    onAction = { if (s.onlineEnabled) viewModel.retryLyrics() else viewModel.lookUpLrclib() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Keeps drags inside the lyrics: a fling past the last line must not carry on into the
 * player/queue pager. Also notes when the user scrolled, to pause auto-scroll and to bring the
 * source line back ([scrollTick], read by Compose; only bumped every 0.5 s, so a drag doesn't
 * recompose the panel every frame).
 */
private class LyricsScrollGuard : NestedScrollConnection {
    var lastUserScroll = 0L
    var scrollTick by mutableLongStateOf(0L)
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            lastUserScroll = SystemClock.uptimeMillis()
            if (lastUserScroll - scrollTick > 500) scrollTick = lastUserScroll
        }
        return Offset.Zero
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
}

@OptIn(ExperimentalFoundationApi::class) // combinedClickable, for long press to copy a line
@Composable
private fun SyncedLyrics(
    viewModel: MainViewModel,
    lines: List<LyricLine>,
    guard: LyricsScrollGuard,
    top: Dp,
    footer: @Composable () -> Unit
) {
    val position = viewModel.currentPositionMs.collectAsState()
    val copy = rememberCopy()
    // Changes only when the sung line changes, not on every position tick.
    val current by remember(lines) { derivedStateOf { currentLineIndex(lines, position.value) } }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Keep the sung line a third of the way down the square.
    suspend fun showSungLine() =
        listState.animateScrollToItem(current.coerceAtLeast(0), scrollOffset = -listState.layoutInfo.viewportSize.height / 3)

    LaunchedEffect(current) {
        // After the user scrolls, leave the list where they put it for a few seconds.
        if (SystemClock.uptimeMillis() - guard.lastUserScroll < 3_000) return@LaunchedEffect
        showSungLine()
    }

    // Where the sung line is when it's scrolled out of view: -1 above, 1 below, 0 on screen.
    val away by remember(listState) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            when {
                current < 0 || visible.isEmpty() -> 0
                current < visible.first().index -> -1
                current > visible.last().index -> 1
                else -> 0
            }
        }
    }
    // Kept while the arrow fades out, so it doesn't flip direction as it goes.
    var arrowUp by remember { mutableStateOf(true) }
    LaunchedEffect(away) { if (away != 0) arrowUp = away < 0 }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        // [top] leaves room for the version pills when they show.
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top, bottom = 200.dp),
        modifier = Modifier.fillMaxSize().nestedScroll(guard)
    ) {
        itemsIndexed(lines) { index, line ->
            val color by animateColorAsState(
                // White at reduced strength reads on any accent tint; grey would turn muddy.
                targetValue = when {
                    index == current -> PrimaryText
                    index < current -> PrimaryText.copy(alpha = 0.42f)
                    else -> PrimaryText.copy(alpha = 0.55f)
                },
                animationSpec = tween(250),
                label = "lyric"
            )
            Text(
                text = line.text.ifBlank { "♪" },
                color = color,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                // Only as wide as its words: tap the words to jump the song there; the space
                // beside a short line counts as empty and returns to the art.
                // Long press copies the line.
                modifier = Modifier
                    .combinedClickable(
                        onLongClickLabel = "Copy line",
                        onLongClick = { if (line.text.isNotBlank()) copy(line.text) },
                        onClick = { viewModel.seekTo(line.timeMs) }
                    )
                    .padding(vertical = 7.dp)
            )
        }
        item { footer() }
    }
    // Back to the sung line; bottom-right, clear of the source line at bottom-left.
    AnimatedVisibility(
        visible = away != 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
    ) {
        Surface(
            onClick = {
                guard.lastUserScroll = 0L // follow the song again straight away
                scope.launch { showSungLine() }
            },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            Icon(
                imageVector = if (arrowUp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Back to the current line",
                tint = PrimaryText.copy(alpha = 0.85f),
                modifier = Modifier.padding(6.dp).size(22.dp)
            )
        }
    }
    }
}

@Composable
private fun PlainLyrics(text: String, guard: LyricsScrollGuard, top: Dp, footer: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(guard)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = top, bottom = 16.dp)
    ) {
        // Long press selects words to copy, as in any text.
        SelectionContainer {
            Text(text = text, color = PrimaryText, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        footer()
    }
}

/** ".lrc file · synced · 3 sources": where the shown lyrics come from, and if there's a choice. */
private fun sourceLine(f: LyricsLookup.Found): String =
    "${f.source.label} · ${if (f.lyrics is Lyrics.Synced) "synced" else "not synced"}" +
        if (f.sources.size > 1) " · ${f.sources.size} sources" else ""

/** The end of the lyrics: where they come from; tap for the source sheet (also to look up LRCLIB). */
@Composable
private fun SourceLabel(line: String, onClick: () -> Unit) {
    Text(
        text = "Lyrics: $line ▾",
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .padding(top = 10.dp)
            .clickable(onClickLabel = "Choose where lyrics come from", onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

/** The fading source line over the lyrics. */
@Composable
private fun SourceChip(line: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        modifier = Modifier.semantics { contentDescription = "Lyrics from $line. Choose where lyrics come from" }
    ) {
        Text(
            text = "$line ▾",
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryText.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * Every source that has the shown kind, to pick one for this song; Automatic goes back to the
 * Settings order. While LRCLIB hasn't been asked for the song, it can be looked up from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSourceSheet(viewModel: MainViewModel, f: LyricsLookup.Found, canLookUp: Boolean, onDismiss: () -> Unit) {
    val accent = LocalAccentColor.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TerminalRaisedPanel
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Text(
                "LYRICS SOURCE · ${f.kind.label.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = TerminalBlack,
                border = BorderStroke(1.dp, TerminalBorder)
            ) {
                Column {
                    SourceRow("Automatic", "Now: ${f.autoSource.label} · your order in Settings › Lyrics", null, !f.picked, accent) {
                        viewModel.pickLyricsSource(f.kind, null)
                        onDismiss()
                    }
                    f.sources.forEach { option ->
                        HorizontalDivider(color = TerminalBorder)
                        SourceRow(
                            option.source.label,
                            option.source.long,
                            if (option.synced) "SYNCED" else "PLAIN",
                            f.picked && f.source == option.source,
                            accent
                        ) {
                            viewModel.pickLyricsSource(f.kind, option.source)
                            onDismiss()
                        }
                    }
                    // Not when LRCLIB is turned off in Settings: then nothing goes online.
                    if (!f.lrclibAsked && canLookUp) {
                        HorizontalDivider(color = TerminalBorder)
                        SourceRow(
                            "Look up on LRCLIB",
                            if (f.lrclibFailed) "Couldn't reach LRCLIB · tap to try again" else "Sends the title, artist, album and length",
                            null,
                            null,
                            accent
                        ) { viewModel.lookUpLrclib() }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "The pick is remembered for this song and this version.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

/** A sheet row: a radio when [selected] isn't null, else an action in the accent colour. */
@Composable
private fun SourceRow(title: String, sub: String, tag: String?, selected: Boolean?, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { if (selected != null) this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (selected != null) {
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = MutedText))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (selected == null) accent else PrimaryText)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
        if (tag != null) {
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (tag == "SYNCED") TerminalGreen else MutedText
            )
        }
    }
}


/** Small pills to switch between the kinds a song has (Original / Romaji / Translation); the shown one is filled. */
@Composable
private fun LyricsKindSwitch(
    available: List<LyricsKind>,
    shown: LyricsKind,
    onPick: (LyricsKind) -> Unit,
    modifier: Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        available.forEach { kind ->
            val selected = kind == shown
            Surface(
                onClick = { onPick(kind) },
                shape = CircleShape,
                color = if (selected) PrimaryText else Color.Black.copy(alpha = 0.25f),
                border = if (selected) null else BorderStroke(1.dp, PrimaryText.copy(alpha = 0.35f)),
                modifier = Modifier.semantics { this.selected = selected }
            ) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.Black else PrimaryText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, detail: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(24.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
        Spacer(modifier = Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MutedText, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel, color = PrimaryText) }
        }
    }
}
