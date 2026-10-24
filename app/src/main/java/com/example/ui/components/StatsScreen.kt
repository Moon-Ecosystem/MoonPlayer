package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.formatBreakdown
import com.example.data.stats.Bar
import com.example.data.stats.ListeningStats
import com.example.data.stats.Ranked
import com.example.data.stats.StatsDetail
import com.example.data.stats.StatsKind
import com.example.data.stats.StatsRange
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel

private val STATS_TABS = listOf("Overview", "Top", "Time")

@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.listeningStats.collectAsState()
    val detail by viewModel.statsDetail.collectAsState()
    val range by viewModel.statsRange.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val accent = LocalAccentColor.current
    var showResetDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "LISTENING STATS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "[ YOUR LISTENING, ON THIS PHONE ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showResetDialog = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Reset stats", tint = MutedText)
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset stats?", color = PrimaryText) },
                text = {
                    Text(
                        "Clears listening time, play counts, top tracks, top playlists and Recently Played. " +
                            "Playlists, favourites and your library aren't touched. This can't be undone.",
                        color = SecondaryText
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetStats()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = buttonContentColorFor(accent)
                        )
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showResetDialog = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = TerminalSurfaceElevated
            )
        }

        // RANGE: applies to every number and chart below
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            StatsRange.entries.forEach { r ->
                FilterChip(
                    selected = r == range,
                    onClick = { viewModel.setStatsRange(r) },
                    // Colour set on the text: the theme's labelSmall carries its own grey.
                    label = {
                        Text(
                            r.label,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (r == range) buttonContentColorFor(accent) else MutedText
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = TerminalRaisedPanel,
                        labelColor = MutedText,
                        selectedContainerColor = accent,
                        selectedLabelColor = buttonContentColorFor(accent)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = r == range,
                        borderColor = TerminalBorder,
                        selectedBorderColor = accent
                    )
                )
            }
        }

        // An artist/album/song/playlist page replaces the tabs; Back returns to them.
        detail?.let { d ->
            BackHandler { viewModel.closeStatsDetail() }
            key(d.kind, d.key) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 24.dp)
                ) {
                    DetailPage(d, accent, viewModel::openStatsDetail, viewModel::closeStatsDetail)
                }
            }
            return@Column
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = TerminalBlack,
            contentColor = accent,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = accent)
            },
            divider = { HorizontalDivider(color = TerminalBorder) }
        ) {
            STATS_TABS.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            title.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (tab == i) accent else MutedText
                        )
                    }
                )
            }
        }

        // Each tab starts at its top.
        key(tab) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {
                val s = stats ?: return@Column
                when (tab) {
                    0 -> OverviewTab(s, allTracks.size, favoriteIds.size, formatBreakdown(allTracks), accent, viewModel::openStatsDetail)
                    1 -> if (s.plays == 0) NoListening() else TopTab(s, accent, viewModel::openStatsDetail)
                    else -> if (s.plays == 0) NoListening() else TimeTab(s, accent)
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    s: ListeningStats,
    libraryCount: Int,
    favoriteCount: Int,
    formats: List<Pair<String, Int>>,
    accent: Color,
    onOpen: (StatsKind, String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.Timer, "TOTAL TIME", formatListenTime(s.listenedMs), accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.Headphones, "TOTAL PLAYS", "${s.plays}", accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.MusicNote, "LIBRARY", "$libraryCount Tracks", accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.Favorite, "FAVORITES", "$favoriteCount Songs", accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(24.dp))

    if (s.plays == 0) {
        NoListening()
    } else {
        TrendChart(s, accent)
        StatsSection("TOP ARTISTS") {
            RankedCard(s.artists.take(5), s.listenedMs, accent) { onOpen(StatsKind.ARTIST, it.key) }
        }
        if (s.playlists.isNotEmpty()) {
            StatsSection("TOP PLAYLISTS") {
                RankedCard(s.playlists.take(5), s.listenedMs, accent) { onOpen(StatsKind.PLAYLIST, it.key) }
            }
        }
    }

    if (formats.isNotEmpty()) {
        StatsSection("LIBRARY BY FORMAT") {
            StatsPanel {
                formats.forEach { (format, count) ->
                    ProfileTelemetryRow(format, if (count == 1) "1 track" else "$count tracks")
                }
            }
        }
    }
}

/** Artists, albums, songs or playlists, picked from one row, ranked by time or by plays. */
@Composable
private fun TopTab(s: ListeningStats, accent: Color, onOpen: (StatsKind, String) -> Unit) {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var byPlays by rememberSaveable { mutableStateOf(false) }
    val kinds = listOf("ARTISTS", "ALBUMS", "TRACKS", "PLAYLISTS")
    StatsChipRow(kinds, kind, accent) { kind = it }
    StatsChipRow(listOf("BY TIME", "BY PLAYS"), if (byPlays) 1 else 0, accent) { byPlays = it == 1 }
    val items = when (kind) {
        0 -> s.artists
        1 -> s.albums
        2 -> s.tracks
        else -> s.playlists
    }
    val ranked = if (byPlays) {
        items.sortedWith(compareByDescending<Ranked> { it.plays }.thenByDescending { it.listenedMs })
    } else {
        items.sortedWith(compareByDescending<Ranked> { it.listenedMs }.thenByDescending { it.plays })
    }
    if (ranked.isEmpty()) {
        StatsPanel {
            Text(
                text = "> NO PLAYLIST PLAYS IN THIS RANGE",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
    } else {
        val kindOf = StatsKind.entries[kind]
        RankedCard(ranked.take(50), s.listenedMs, accent, showPlayThrough = kindOf == StatsKind.TRACK) {
            onOpen(kindOf, it.key)
        }
    }
}

/** One artist, album, song or playlist: totals, play-through, what's in it, and its own charts. */
@Composable
private fun DetailPage(
    d: StatsDetail,
    accent: Color,
    onOpen: (StatsKind, String) -> Unit,
    onBack: () -> Unit
) {
    val s = d.stats
    val name = if (d.kind == StatsKind.TRACK) s.tracks.firstOrNull()?.name ?: "" else d.key
    val subtitle = when (d.kind) {
        StatsKind.TRACK -> s.tracks.firstOrNull()?.detail
        StatsKind.ALBUM -> s.artists.firstOrNull()?.name
        else -> null
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to top lists", tint = MutedText)
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(d.kind.label, style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (s.plays == 0) {
        NoListening()
        return
    }

    // TOTALS
    val share = if (d.allListenedMs > 0) "${s.listenedMs * 100 / d.allListenedMs}%" else "–"
    val average = if (d.timedPlays > 0) formatListenTime(s.listenedMs / d.timedPlays) else "–"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.Timer, "LISTENED", formatListenTime(s.listenedMs), accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.Headphones, "PLAYS", "${s.plays}", accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.PieChart, "SHARE", share, accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.AvTimer, "PER PLAY", average, accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.History, "FIRST", formatPlayDate(d.firstPlayed), accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.Event, "LAST", formatPlayDate(d.lastPlayed), accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(24.dp))

    // FINISHED VS SKIPPED
    val finished = s.tracks.sumOf { it.finished }
    val skipped = s.tracks.sumOf { it.skipped }
    val partly = (d.timedPlays - finished - skipped).coerceAtLeast(0)
    if (finished + skipped > 0) {
        StatsSection("FINISHED VS SKIPPED") {
            StatsPanel {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .semantics { contentDescription = "Finished $finished, partly played $partly, skipped $skipped" }
                ) {
                    if (finished > 0) Box(Modifier.weight(finished.toFloat()).fillMaxHeight().background(accent))
                    if (partly > 0) Box(Modifier.weight(partly.toFloat()).fillMaxHeight().background(TerminalBorder))
                    if (skipped > 0) Box(Modifier.weight(skipped.toFloat()).fillMaxHeight().background(MutedText))
                }
                Spacer(modifier = Modifier.height(10.dp))
                ProfileTelemetryRow("Finished (80%+)", "$finished")
                ProfileTelemetryRow("Partly played", "$partly")
                ProfileTelemetryRow("Skipped (under 30 s or half)", "$skipped")
            }
        }
    }

    // WHAT'S INSIDE
    val openTrack: (Ranked) -> Unit = { onOpen(StatsKind.TRACK, it.key) }
    when (d.kind) {
        StatsKind.ARTIST -> {
            StatsSection("TOP SONGS") { RankedCard(s.tracks.take(10), s.listenedMs, accent, showPlayThrough = true, onClick = openTrack) }
            StatsSection("ALBUMS") { RankedCard(s.albums, s.listenedMs, accent) { onOpen(StatsKind.ALBUM, it.key) } }
        }
        StatsKind.ALBUM -> {
            StatsSection("SONGS") { RankedCard(s.tracks, s.listenedMs, accent, showPlayThrough = true, onClick = openTrack) }
        }
        StatsKind.PLAYLIST -> {
            StatsSection("TOP SONGS") { RankedCard(s.tracks.take(10), s.listenedMs, accent, showPlayThrough = true, onClick = openTrack) }
            StatsSection("TOP ARTISTS") { RankedCard(s.artists.take(10), s.listenedMs, accent) { onOpen(StatsKind.ARTIST, it.key) } }
        }
        StatsKind.TRACK -> if (s.playlists.isNotEmpty()) {
            StatsSection("PLAYED FROM") { RankedCard(s.playlists, s.listenedMs, accent) { onOpen(StatsKind.PLAYLIST, it.key) } }
        }
    }

    // ITS OWN CHARTS
    TimeTab(s, accent)
}

/** "24 Oct", with the year when it isn't this year; "–" when never played. */
private fun formatPlayDate(at: Long?): String {
    if (at == null) return "–"
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val year = java.util.Calendar.getInstance().apply { timeInMillis = at }.get(java.util.Calendar.YEAR)
    val pattern = if (year == thisYear) "d MMM" else "d MMM yyyy"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(at))
}

@Composable
private fun StatsChipRow(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = {
                    Text(
                        label,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected == i) accent else MutedText
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = TerminalRaisedPanel,
                    selectedContainerColor = TerminalSurfaceElevated
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == i,
                    borderColor = TerminalBorder,
                    selectedBorderColor = accent
                )
            )
        }
    }
}

@Composable
private fun TimeTab(s: ListeningStats, accent: Color) {
    TrendChart(s, accent)
    StatsSection("BY HOUR OF DAY", peakOf(s.byHour)?.let { "busiest ${it.label.padStart(2, '0')}:00" }) {
        StatsPanel { BarChart(s.byHour, accent, labelEvery = 6, description = "Listening by hour of day") }
    }
    val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val busiestDay = s.byWeekday.indices.maxByOrNull { s.byWeekday[it].ms }?.takeIf { s.byWeekday[it].ms > 0 }
    StatsSection("BY DAY OF WEEK", busiestDay?.let { "busiest ${weekdayNames[it]}" }) {
        StatsPanel { BarChart(s.byWeekday, accent, labelEvery = 1, description = "Listening by day of week") }
    }
}

@Composable
private fun TrendChart(s: ListeningStats, accent: Color) {
    val title = if (s.trendPerMonth) "LISTENING PER MONTH" else "LISTENING PER DAY"
    StatsSection(title, peakOf(s.trend)?.let { "peak ${formatListenTime(it.ms)}" }) {
        StatsPanel {
            BarChart(
                s.trend,
                accent,
                labelEvery = if (s.trend.size > 14) 5 else 1,
                description = title.lowercase()
            )
        }
    }
}

/** The bar with the most listening, or null when nothing was played. */
private fun peakOf(bars: List<Bar>): Bar? = bars.maxByOrNull { it.ms }?.takeIf { it.ms > 0 }

@Composable
private fun StatsSection(title: String, note: String? = null, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.labelSmall, color = MutedText, fontFamily = FontFamily.Monospace)
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    content()
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun StatsPanel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun NoListening() {
    StatsPanel {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(text = "> NO LISTENING IN THIS RANGE", style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
    }
}

/** Bars scaled to the largest; empty slots show as a thin line so the time axis stays readable. */
@Composable
private fun BarChart(bars: List<Bar>, accent: Color, labelEvery: Int, description: String) {
    val max = bars.maxOfOrNull { it.ms }?.takeIf { it > 0 } ?: 1L
    val gap = if (bars.size > 14) 2.dp else 4.dp
    val peak = peakOf(bars)
    Column(modifier = Modifier.semantics {
        contentDescription = if (peak == null) "$description: nothing played" else "$description, peak ${peak.label}: ${formatListenTime(peak.ms)}"
    }) {
        Row(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(if (bar.ms > 0) (bar.ms.toFloat() / max).coerceAtLeast(0.04f) else 0.02f)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (bar.ms > 0) accent else TerminalBorder)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEachIndexed { i, bar ->
                Text(
                    text = if (i % labelEvery == 0) bar.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Ranked artists, albums, playlists or songs: time listened, plays and share of all listening. */
@Composable
private fun RankedCard(
    items: List<Ranked>,
    totalMs: Long,
    accent: Color,
    showPlayThrough: Boolean = false,
    // Opens the row's detail page; rows aren't tappable without it.
    onClick: ((Ranked) -> Unit)? = null
) {
    val maxMs = items.maxOfOrNull { it.listenedMs } ?: 0L
    val maxPlays = items.maxOfOrNull { it.plays } ?: 1
    StatsPanel {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable { onClick(item) } else Modifier)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("#%02d", index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 0) accent else MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            text = formatListenTime(item.listenedMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val share = if (totalMs > 0) " · ${item.listenedMs * 100 / totalMs}%" else ""
                    val plays = if (item.plays == 1) "1 play" else "${item.plays} plays"
                    Text(
                        text = listOf(item.detail, plays + share).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showPlayThrough && item.finished + item.skipped > 0) {
                        Text(
                            text = "finished ${item.finished} · skipped ${item.skipped}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Old plays have no recorded time; fall back to plays so the bar still means something.
                    val fraction = if (maxMs > 0) item.listenedMs.toFloat() / maxMs else item.plays.toFloat() / maxPlays
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0.03f, 1f) },
                        color = accent,
                        trackColor = TerminalBorder,
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** "1h 20m", "12m", or "45s" under a minute. */
internal fun formatListenTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "${ms / 1000}s"
    }
}

@Composable
private fun StatMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }
    }
}

/** Label on the left, value right-aligned; a long value wraps instead of running into the label. */
@Composable
internal fun ProfileTelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
