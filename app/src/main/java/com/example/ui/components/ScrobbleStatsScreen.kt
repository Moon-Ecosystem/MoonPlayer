package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.scrobble.Scrobble
import com.example.data.scrobble.ScrobbleException.Reason
import com.example.data.scrobble.ScrobbleService
import com.example.data.scrobble.ScrobbleStats
import com.example.data.scrobble.TopItem
import com.example.data.stats.StatsRange
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.MainViewModel.ScrobbleUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SCROBBLE_TABS = listOf("Overview", "Top", "Recent")

/** The stats page's scrobble side: connect, then Overview / Top / Recent from the service. */
@Composable
internal fun ColumnScope.ScrobbleStatsContent(
    state: ScrobbleUiState,
    service: ScrobbleService,
    user: String,
    range: StatsRange,
    accent: Color,
    viewModel: MainViewModel
) {
    if (state is ScrobbleUiState.NotConnected) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
            ConnectForm(service, accent, viewModel::connectScrobbles)
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    StatsRangeChips(range, accent, viewModel::setStatsRange)
    StatsTabs(SCROBBLE_TABS, tab, accent) { tab = it }

    key(tab) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
            when (state) {
                ScrobbleUiState.Loading, ScrobbleUiState.NotConnected -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = accent) }

                is ScrobbleUiState.Failed -> ScrobbleProblem(
                    state.reason, state.message, service, user, accent,
                    onChangeUser = viewModel::disconnectScrobbles,
                    onRetry = viewModel::refreshScrobbles
                )

                is ScrobbleUiState.Loaded -> {
                    val s = state.stats
                    if (s.offline) OfflineNote(s.fetchedAt, viewModel::refreshScrobbles)
                    when (tab) {
                        0 -> ScrobbleOverview(s, range, accent)
                        1 -> ScrobbleTop(s, range, accent)
                        else -> ScrobbleRecent(s.recent, accent)
                    }
                    Text(
                        text = "${service.label} counts plays, not listening time · " +
                            if (state.refreshing) "updating…" else "updated ${ago(s.fetchedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectForm(current: ScrobbleService, accent: Color, onConnect: (ScrobbleService, String) -> Unit) {
    var service by rememberSaveable { mutableIntStateOf(current.ordinal) }
    val userState = rememberTextFieldState()
    val user = userState.text.toString()

    StatsPanel {
        Text("> NOT CONNECTED", style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(10.dp))
        Text("See the stats Pano Scrobbler builds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Pano Scrobbler sends what you play to Last.fm or Libre.fm. Moon Player reads it back from there with just " +
                "your username. No password, and only what your profile already shows publicly.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText
        )
        Spacer(modifier = Modifier.height(16.dp))
        FieldLabel("SERVICE")
        StatsChipRow(ScrobbleService.entries.map { it.label.uppercase() }, service, accent) { service = it }
        Spacer(modifier = Modifier.height(8.dp))
        FieldLabel("USERNAME")
        // Same build as the queue search and MoonKeyboard: BasicTextField on a TextFieldState.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalSurfaceElevated)
                .border(1.dp, accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (user.isEmpty()) Text("Your ${ScrobbleService.entries[service].label} username", color = MutedText, fontSize = 15.sp)
                BasicTextField(
                    state = userState,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = PrimaryText, fontSize = 15.sp),
                    cursorBrush = SolidColor(accent),
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { onConnect(ScrobbleService.entries[service], user) },
            enabled = user.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("CONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        "Use the same account you signed in to in Pano Scrobbler. Nothing is sent to the service. Moon Player only reads.",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ScrobbleOverview(s: ScrobbleStats, range: StatsRange, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.Headphones, "SCROBBLES", "%,d".format(s.scrobbles), accent, Modifier.weight(1f))
        StatMetricCard(Icons.Default.PieChart, "ARTISTS", "%,d".format(s.artists), accent, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatMetricCard(Icons.Default.History, "ALL TIME", "%,d".format(s.totalScrobbles), accent, Modifier.weight(1f))
        StatMetricCard(
            Icons.Default.Event, "SCROBBLING SINCE",
            s.since?.let { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "–",
            accent, Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    if (s.scrobbles == 0) {
        NoScrobbles(range)
    } else {
        StatsSection("TOP ARTISTS") { TopItemsCard(s.topArtists.take(5), accent) }
    }
}

@Composable
private fun ScrobbleTop(s: ScrobbleStats, range: StatsRange, accent: Color) {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    StatsChipRow(listOf("ARTISTS", "ALBUMS", "TRACKS"), kind, accent) { kind = it }
    val items = when (kind) {
        0 -> s.topArtists
        1 -> s.topAlbums
        else -> s.topTracks
    }
    if (items.isEmpty()) NoScrobbles(range) else TopItemsCard(items, accent)
    Spacer(modifier = Modifier.height(16.dp))
}

/** Ranked by plays; a row opens its page on the service's website. */
@Composable
private fun TopItemsCard(items: List<TopItem>, accent: Color) {
    val uriHandler = LocalUriHandler.current
    val maxPlays = items.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    StatsPanel {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (item.url.isNotBlank()) Modifier.clickable { runCatching { uriHandler.openUri(item.url) } } else Modifier)
                    .padding(vertical = 8.dp)
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
                            text = if (item.plays == 1) "1 play" else "%,d plays".format(item.plays),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (item.detail.isNotBlank()) {
                        Text(item.detail, style = MaterialTheme.typography.labelSmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (item.plays.toFloat() / maxPlays).coerceIn(0.03f, 1f) },
                        color = accent,
                        trackColor = TerminalBorder,
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
                if (item.url.isNotBlank()) Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            }
            if (index < items.lastIndex) {
                HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** What's being scrobbled now, then the latest scrobbles by day. Not limited by the range. */
@Composable
private fun ScrobbleRecent(recent: List<Scrobble>, accent: Color) {
    if (recent.isEmpty()) {
        StatsPanel { CenteredNote("> NO SCROBBLES YET") }
        return
    }
    recent.firstOrNull { it.at == null }?.let { now ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalSurfaceElevated)
                .border(1.dp, accent, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Cover(now.imageUrl, 44)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("SCROBBLING NOW", style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
                Text(now.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now.artist, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    recent.filter { it.at != null }.groupBy { dayLabel(it.at!!) }.forEach { (day, plays) ->
        StatsSection(day) {
            StatsPanel {
                plays.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Cover(p.imageUrl, 36)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, color = PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.artist, style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(timeLabel(p.at!!), style = MaterialTheme.typography.labelSmall, color = MutedText, fontFamily = FontFamily.Monospace)
                    }
                    if (i < plays.lastIndex) HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String?, sizeDp: Int) {
    val modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(6.dp)).background(TerminalBorder)
    if (url == null) Box(modifier) else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
private fun ScrobbleProblem(
    reason: Reason,
    message: String?,
    service: ScrobbleService,
    user: String,
    accent: Color,
    onChangeUser: () -> Unit,
    onRetry: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val name = service.label.uppercase()
    val (title, body) = when (reason) {
        Reason.NO_USER -> "> NO SUCH USER ON $name" to "Check the spelling, or that it's the account Pano Scrobbler is signed in to."
        Reason.PRIVATE -> "> THIS PROFILE IS PRIVATE" to "${service.label} only shares listening that the profile makes public. Turn off \"Hide recent listening\" in its privacy settings, then refresh."
        Reason.OFFLINE -> "> OFFLINE" to "Connect to the internet to load your scrobbles."
        Reason.NO_KEY -> "> NO ${name} API KEY" to "This build of Moon Player has no ${service.label} API key, so it can't read ${service.label}."
        Reason.FAILED -> "> COULDN'T LOAD SCROBBLES" to (message?.takeIf { it.isNotBlank() } ?: "${service.label} didn't answer as expected.")
    }
    StatsPanel {
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (reason == Reason.OFFLINE) MutedText else MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (reason) {
                Reason.NO_USER -> AccentButton("CHANGE USERNAME", accent, onChangeUser)
                Reason.PRIVATE -> {
                    AccentButton("REFRESH", accent, onRetry)
                    QuietButton("OPEN $name") { runCatching { uriHandler.openUri(service.profileUrl(user)) } }
                }
                else -> QuietButton("TRY AGAIN", onRetry)
            }
        }
    }
}

@Composable
private fun OfflineNote(fetchedAt: Long, onRetry: () -> Unit) {
    StatsPanel {
        Text("> OFFLINE · SHOWING SAVED STATS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MutedText, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))
        Text("From ${ago(fetchedAt)}. They update once you're back online.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        Spacer(modifier = Modifier.height(10.dp))
        QuietButton("TRY AGAIN", onRetry)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun NoScrobbles(range: StatsRange) {
    StatsPanel {
        CenteredNote(if (range == StatsRange.ALL) "> NO SCROBBLES YET" else "> NO SCROBBLES IN THIS RANGE")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Is Pano Scrobbler running? It needs notification access to see what Moon Player plays.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CenteredNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(4.dp))
}

@Composable
private fun AccentButton(label: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent)),
        shape = RoundedCornerShape(8.dp)
    ) { Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = TerminalRaisedPanel, contentColor = SecondaryText),
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
    ) { Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
}

private fun ago(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

private fun dayLabel(at: Long): String = when {
    DateUtils.isToday(at) -> "TODAY"
    DateUtils.isToday(at + DateUtils.DAY_IN_MILLIS) -> "YESTERDAY"
    else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(at)).uppercase()
}

private fun timeLabel(at: Long): String =
    if (DateUtils.isToday(at)) {
        DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    } else {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    }
