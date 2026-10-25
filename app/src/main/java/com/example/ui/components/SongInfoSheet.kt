package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InfoSection
import com.example.data.SongInfo
import com.example.data.model.Track
import com.example.ui.theme.buttonContentColorFor
import com.example.data.songInfoText
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.viewmodel.MainViewModel

/** Everything known about [track]'s file, from the format badge or the player menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoSheet(viewModel: MainViewModel, track: Track, badge: String, onDismiss: () -> Unit) {
    val accent = LocalAccentColor.current
    val clipboard = LocalClipboardManager.current
    val info by produceState<SongInfo?>(null, track.id) { value = viewModel.songInfo(track) }
    val sections = info?.sections

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TerminalRaisedPanel,
        // Fully open, the sheet stops under the clock instead of sliding behind it.
        modifier = Modifier.statusBarsPadding().testTag("song_info_sheet")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SONG INFO", style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = MutedText, fontFamily = FontFamily.Monospace)
                    }
                    OutlinedButton(
                        onClick = { sections?.let { clipboard.setText(AnnotatedString(songInfoText(track.title, it))) } },
                        enabled = sections != null,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("COPY", color = PrimaryText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val loaded = sections
            if (loaded == null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
            } else {
                if (info?.missing == true) item {
                    MissingFileCard(
                        onRemove = {
                            viewModel.removeCurrentFromQueue()
                            onDismiss()
                        },
                        onRescan = {
                            viewModel.rescanLibrary()
                            onDismiss()
                        }
                    )
                }
                items(loaded, key = { it.title }) { section -> InfoSectionCard(section) }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/** The file was moved, renamed or deleted after it was queued: say so and offer the ways out. */
@Composable
private fun MissingFileCard(onRemove: () -> Unit, onRescan: () -> Unit) {
    val accent = LocalAccentColor.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalBlack,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent),
        modifier = Modifier.testTag("song_info_missing")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("This file doesn't exist any more", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryText)
            }
            Text(
                "It was moved, renamed or deleted after it was added to the queue, so it can't play. " +
                    "Remove it from the queue, or rescan the library to pick up a renamed or moved copy, then play that one.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent))
                ) { Text("Remove from queue") }
                OutlinedButton(
                    onClick = onRescan,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) { Text("Rescan library", color = PrimaryText) }
            }
        }
    }
}

@Composable
private fun InfoSectionCard(section: InfoSection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title, style = MaterialTheme.typography.labelSmall, color = MutedText, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TerminalBlack,
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                section.rows.forEachIndexed { i, (name, value) ->
                    if (i > 0) HorizontalDivider(color = TerminalBorder.copy(alpha = 0.6f))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, color = SecondaryText, modifier = Modifier.width(112.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
