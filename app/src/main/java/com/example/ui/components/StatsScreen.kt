package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TrackStatEntity
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

@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topTracks by viewModel.topPlayedTracks.collectAsState()
    val totalPlays by viewModel.totalPlays.collectAsState()
    val totalListenTimeMs by viewModel.totalListenTimeMs.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val accent = LocalAccentColor.current

    val totalTimeFormatted = remember(totalListenTimeMs) {
        val ms = totalListenTimeMs ?: 0L
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms / (1000 * 60)) % 60
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    val maxPlayCount = remember(topTracks) {
        topTracks.maxOfOrNull { it.playCount } ?: 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
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
                    text = "LISTENING TELEMETRY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "[ LOCAL AUDIT & INSIGHTS ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 STAT METRICS CARDS (2x2 GRID)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard(
                icon = Icons.Default.Timer,
                title = "TOTAL TIME",
                value = totalTimeFormatted,
                accentColor = accent,
                modifier = Modifier.weight(1f)
            )
            StatMetricCard(
                icon = Icons.Default.Headphones,
                title = "TOTAL PLAYS",
                value = "${totalPlays ?: 0}",
                accentColor = accent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard(
                icon = Icons.Default.MusicNote,
                title = "LIBRARY",
                value = "${allTracks.size} Tracks",
                accentColor = accent,
                modifier = Modifier.weight(1f)
            )
            StatMetricCard(
                icon = Icons.Default.Favorite,
                title = "FAVORITES",
                value = "${favoriteIds.size} Songs",
                accentColor = accent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: MOST PLAYED SONGS
        Text(
            text = "TOP PLAYED TRACKS",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (topTracks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TerminalRaisedPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "> NO PLAY HISTORY LOGGED YET",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TerminalRaisedPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    topTracks.forEachIndexed { index, stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stat.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${stat.playCount} plays",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${stat.artist} · ${Track.formatDuration(stat.totalTimeMs)} total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { (stat.playCount.toFloat() / maxPlayCount).coerceIn(0.05f, 1f) },
                                    color = accent,
                                    trackColor = TerminalBorder,
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }

                        if (index < topTracks.lastIndex) {
                            HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // HARDWARE / CODEC PROFILE
        Text(
            text = "AUDIO ENGINE PROFILE",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TerminalRaisedPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileTelemetryRow("Engine Architecture", "AndroidX Media3 ExoPlayer")
                ProfileTelemetryRow("Supported Formats", "MP3 · FLAC · M4A · AAC · OGG · Opus · WAV")
                ProfileTelemetryRow("Digital Signal Processor", "Android Hardware AudioFX Equalizer")
                ProfileTelemetryRow("Privacy Architecture", "100% Local / Zero Cloud Leakage")
                ProfileTelemetryRow("Sample Rate", "44,100 Hz Hi-Fi Stereo")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
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

@Composable
private fun ProfileTelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        Text(text = value, style = MaterialTheme.typography.labelSmall, color = PrimaryText, fontFamily = FontFamily.Monospace)
    }
}
