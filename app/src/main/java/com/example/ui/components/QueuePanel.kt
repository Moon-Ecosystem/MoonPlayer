package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuePanel(
    viewModel: MainViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val accent = LocalAccentColor.current
    var showClearConfirm by remember { mutableStateOf(false) }

    val totalQueueDurationMs = remember(queue) {
        queue.sumOf { it.durationMs }
    }

    if (isOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onClose() }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = false) {}, // prevent click-through
                color = TerminalRaisedPanel,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PLAYBACK QUEUE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "[ ${queue.size} TRACKS · ${Track.formatDuration(totalQueueDurationMs)} ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (queue.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearConfirm = true },
                                    modifier = Modifier
                                        .testTag("clear_queue_button")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Queue",
                                        tint = MutedText
                                    )
                                }
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .testTag("close_queue_button")
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Queue",
                                    tint = PrimaryText
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = TerminalBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Track List in Queue
                    if (queue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "> QUEUE EMPTY",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MutedText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Add songs from Library or Playlists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(queue, key = { index, track -> "${track.id}_$index" }) { index, track ->
                                val isCurrent = index == currentIndex
                                QueueItemCard(
                                    track = track,
                                    index = index,
                                    isCurrent = isCurrent,
                                    accentColor = accent,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < queue.lastIndex,
                                    onPlay = { viewModel.skipToQueueIndex(index) },
                                    onRemove = { viewModel.removeFromQueue(index) },
                                    onMoveUp = { viewModel.moveQueueItem(index, index - 1) },
                                    onMoveDown = { viewModel.moveQueueItem(index, index + 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "Clear Playback Queue?",
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "This will remove all ${queue.size} songs from the queue. Audio files will not be deleted.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearQueue()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = TerminalBlack
                    )
                ) {
                    Text("Clear Queue")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirm = false }
                ) {
                    Text("Cancel", color = PrimaryText)
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }
}

@Composable
fun QueueItemCard(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) TerminalSurfaceElevated else TerminalBlack,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) accentColor else TerminalBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("queue_item_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index or Prompt
            Text(
                text = if (isCurrent) ">" else String.format("%02d", index + 1),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) accentColor else MutedText,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(22.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) accentColor else PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${track.artist} · ${track.formattedDuration}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick reordering / remove buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canMoveUp) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier
                            .size(28.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (canMoveDown) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier
                            .size(28.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(28.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove From Queue",
                        tint = MutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
