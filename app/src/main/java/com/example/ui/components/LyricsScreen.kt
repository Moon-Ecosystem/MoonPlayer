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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.LyricsLookup
import com.example.data.lyrics.LyricsSource
import com.example.data.lyrics.LyricsVersion
import com.example.data.lyrics.currentLineIndex
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
            LyricsUiState.AskOnline -> AskOnline(viewModel, Modifier.align(Alignment.Center))
            is LyricsUiState.Done -> when (val lookup = s.lookup) {
                is LyricsLookup.Found -> {
                    // Every version, the file's (♫) first, when there's more than one (or only a
                    // translation, so it's never mistaken for the real words). Showing the file's
                    // lyrics that LRCLIB's hide is in the player's ⋮ menu.
                    val shown = lookup.shown
                    val showSwitch = shown != null && (lookup.available.size > 1 || shown.kind == LyricsKind.TRANSLATION)
                    // Room for the pills only when they're there.
                    val top = if (showSwitch) 42.dp else 14.dp
                    when (val lyrics = lookup.lyrics) {
                        // Keyed by version, so switching starts the new one at the sung line.
                        is Lyrics.Synced -> androidx.compose.runtime.key(lookup.shown) {
                            SyncedLyrics(viewModel, lyrics.lines, lookup.source, top)
                        }
                        is Lyrics.Plain -> androidx.compose.runtime.key(lookup.shown) {
                            PlainLyrics(lyrics.text, lookup.source, top)
                        }
                    }
                    if (showSwitch && shown != null) {
                        LyricsVersionSwitch(
                            available = lookup.available,
                            shown = shown,
                            onPick = { viewModel.showLyricsVersion(it) },
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp)
                        )
                    }
                }
                LyricsLookup.NotFound -> LyricsMessage(
                    title = "No lyrics found",
                    detail = if (s.onlineEnabled) {
                        "Add a .lrc file with the same name next to the song to use your own."
                    } else {
                        "Online lookup is off. Turn it on, or add a .lrc file with the same name next to the song."
                    },
                    actionLabel = if (s.onlineEnabled) "Try again" else "Look up online",
                    onAction = { if (s.onlineEnabled) viewModel.retryLyrics() else viewModel.answerOnlineLyrics(true) },
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
                    onAction = { viewModel.retryLyrics() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Keeps drags inside the lyrics: a fling past the last line must not carry on into the
 * player/queue pager. Also notes when the user scrolled, to pause auto-scroll.
 */
private class LyricsScrollGuard : NestedScrollConnection {
    var lastUserScroll = 0L
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) lastUserScroll = SystemClock.uptimeMillis()
        return Offset.Zero
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
}

@OptIn(ExperimentalFoundationApi::class) // combinedClickable, for long press to copy a line
@Composable
private fun SyncedLyrics(viewModel: MainViewModel, lines: List<LyricLine>, source: LyricsSource, top: Dp) {
    val position = viewModel.currentPositionMs.collectAsState()
    val copy = rememberCopy()
    // Changes only when the sung line changes, not on every position tick.
    val current by remember(lines) { derivedStateOf { currentLineIndex(lines, position.value) } }
    val listState = rememberLazyListState()
    val guard = remember { LyricsScrollGuard() }

    LaunchedEffect(current) {
        // After the user scrolls, leave the list where they put it for a few seconds.
        if (SystemClock.uptimeMillis() - guard.lastUserScroll < 3_000) return@LaunchedEffect
        // Keep the sung line a third of the way down the square.
        val third = listState.layoutInfo.viewportSize.height / 3
        listState.animateScrollToItem(current.coerceAtLeast(0), scrollOffset = -third)
    }

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
        item { SourceLabel(source, synced = true) }
    }
}

@Composable
private fun PlainLyrics(text: String, source: LyricsSource, top: Dp) {
    val guard = remember { LyricsScrollGuard() }
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
        SourceLabel(source, synced = false)
    }
}

@Composable
private fun SourceLabel(source: LyricsSource, synced: Boolean) {
    Text(
        text = "Lyrics: ${source.label}" + if (synced) "" else " · not synced",
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 18.dp)
    )
}

@Composable
private fun AskOnline(viewModel: MainViewModel, modifier: Modifier) {
    val accent = LocalAccentColor.current
    Column(modifier = modifier.padding(20.dp)) {
        Text("Look up lyrics online?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Lyrics come from LRCLIB, a free lyrics library. Each lookup sends only the song's " +
                "title, artist, album and length; results are saved on your phone. Change this any time in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.answerOnlineLyrics(true) },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent))
            ) { Text("Use online lyrics") }
            OutlinedButton(onClick = { viewModel.answerOnlineLyrics(false) }) { Text("Not now", color = PrimaryText) }
        }
    }
}

/**
 * Small pills to switch between the versions a song has; the shown one is filled. A ♫ marks the
 * ones stored in the song file. The row scrolls sideways when it's wider than the square.
 */
@Composable
private fun LyricsVersionSwitch(
    available: List<LyricsVersion>,
    shown: LyricsVersion,
    onPick: (LyricsVersion) -> Unit,
    modifier: Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        available.forEach { version ->
            val selected = version == shown
            val inFile = version.source == LyricsSource.EMBEDDED
            val color = if (selected) Color.Black else PrimaryText
            Surface(
                onClick = { onPick(version) },
                shape = CircleShape,
                color = if (selected) PrimaryText else Color.Black.copy(alpha = 0.25f),
                border = if (selected) null else BorderStroke(1.dp, PrimaryText.copy(alpha = 0.35f)),
                modifier = Modifier.semantics {
                    this.selected = selected
                    contentDescription = version.kind.label + if (inFile) ", from the song file" else ", from ${version.source.label}"
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (inFile) Icon(Icons.Default.MusicNote, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                    Text(
                        text = version.kind.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, detail: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(24.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
        Spacer(modifier = Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MutedText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(onClick = onAction) { Text(actionLabel, color = PrimaryText) }
    }
}
