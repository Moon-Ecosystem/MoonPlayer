package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.artModel
import com.example.data.model.Track
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen

/** Now-playing bar shown under every screen except the player and queue; tap opens the player. */
@Composable
fun MiniPlayerBar(viewModel: MainViewModel, track: Track, modifier: Modifier = Modifier) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    // Read only inside the progress lambda, so position ticks redraw the line, not the bar.
    val position = viewModel.currentPositionMs.collectAsState()
    val duration = viewModel.durationMs.collectAsState()
    val accent = LocalAccentColor.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { viewModel.navigateTo(Screen.PLAYER) }
    ) {
        Column {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.moon_player_logo_thumb),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    AsyncImage(
                        model = track.artModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = PrimaryText)
                }
                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.togglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = buttonContentColorFor(accent),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PrimaryText)
                }
            }
        }
    }
}
