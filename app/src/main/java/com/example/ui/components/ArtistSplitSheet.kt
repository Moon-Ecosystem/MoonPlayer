package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.artistTag
import com.example.data.model.artistsOf
import com.example.data.model.splitsFor
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel

/**
 * "How this name splits", from an artist page: one card per artist [tags] of the page, each
 * separator a toggle (filled = split there). A change is saved per exact tag at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtistSplitSheet(viewModel: MainViewModel, artist: String, tags: List<String>, onDismiss: () -> Unit) {
    val accent = LocalAccentColor.current
    val settings by viewModel.settings.collectAsState()
    val rules = settings.artistRules
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TerminalRaisedPanel
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("HOW THESE NAMES SPLIT", style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
            Text("Tags with $artist. Tap a separator to split there or join it back.", style = MaterialTheme.typography.bodySmall, color = MutedText)
            tags.forEach { tag ->
                val cut = artistTag(tag)
                val splits = rules.splitsFor(tag, cut)
                val mine = rules.fixes[tag.trim()]?.size == cut.separators.size
                Surface(shape = RoundedCornerShape(10.dp), color = TerminalBlack, border = BorderStroke(1.dp, TerminalBorder)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            cut.names.forEachIndexed { i, name ->
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                                if (i < cut.separators.size) {
                                    val on = splits[i]
                                    Surface(
                                        onClick = { viewModel.setArtistSplit(tag, splits.toMutableList().also { it[i] = !on }) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (on) accent else TerminalBlack,
                                        contentColor = if (on) buttonContentColorFor(accent) else PrimaryText,
                                        border = if (on) null else BorderStroke(1.dp, TerminalBorder),
                                        modifier = Modifier.semantics {
                                            contentDescription = "Separator ${cut.separators[i]}"
                                            stateDescription = if (on) "Splits here" else "Joined"
                                        }
                                    ) {
                                        Text(
                                            cut.separators[i],
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        val result = artistsOf(cut, splits)
                        Text(
                            "→ ${result.size} ${if (result.size == 1) "ARTIST" else "ARTISTS"}: ${result.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MutedText
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (mine) "YOUR CHOICE" else "AUTOMATIC",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (mine) accent else MutedText,
                                modifier = Modifier.weight(1f)
                            )
                            if (mine) TextButton(onClick = { viewModel.setArtistSplit(tag, null) }) {
                                Text("Back to automatic", color = PrimaryText)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "A choice applies wherever that exact tag appears: artists, artist pages and stats. Undo it here or in Settings › Library.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

/** The tags on an artist page that name more than one artist (a sheet card each). */
fun splittableTags(artistTags: List<String>): List<String> =
    artistTags.map { it.trim() }.distinct().filter { artistTag(it).separators.isNotEmpty() }
