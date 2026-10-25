package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.EmbeddedArt
import com.example.data.artModel
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.model.Track
import com.example.service.EqualizerHelper
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

private fun PlaylistTrackEntity.art() = EmbeddedArt(uri, albumArtUri)

/**
 * A playlist's cover: a picked photo, one song's art, or (by default) the covers of its first
 * four albums in a 2×2 grid, falling back to one cover or an icon for short playlists.
 */
@Composable
fun PlaylistCover(playlist: PlaylistEntity, rows: List<PlaylistTrackEntity>, modifier: Modifier, corner: Dp = 8.dp) {
    val cover = playlist.coverUri
    val fixed = when {
        cover?.startsWith("file:") == true -> cover
        cover != null -> EmbeddedArt(cover, rows.firstOrNull { it.uri == cover }?.albumArtUri)
        else -> null
    }
    val albums = remember(rows) { rows.distinctBy { it.album.ifBlank { it.uri } }.take(4).map { it.art() } }
    CoverImage(fixed, albums, modifier, corner)
}

/** A read-only collection's cover (folder, album, artist...): the automatic grid of its songs' covers. */
@Composable
fun TracksCover(tracks: List<Track>, modifier: Modifier, corner: Dp = 8.dp) {
    val albums = remember(tracks) { tracks.distinctBy { it.album.ifBlank { it.contentUri } }.take(4).map { it.artModel } }
    CoverImage(null, albums, modifier, corner)
}

/** [fixed] when set, else a 2×2 grid of four [albums] covers, one cover, or an icon. */
@Composable
private fun CoverImage(fixed: Any?, albums: List<Any>, modifier: Modifier, corner: Dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(TerminalRaisedPanel),
        contentAlignment = Alignment.Center
    ) {
        when {
            fixed != null ->
                AsyncImage(fixed, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            albums.size == 4 -> Column(Modifier.fillMaxSize()) {
                albums.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f)) {
                        pair.forEach {
                            AsyncImage(it, null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            albums.isNotEmpty() ->
                AsyncImage(albums.first(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(Icons.Default.PlaylistPlay, null, tint = MutedText, modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

private enum class CoverMode(val label: String) { AUTO("Auto"), SONG("A song's art"), PHOTO("From phone") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    viewModel: MainViewModel,
    playlist: PlaylistEntity,
    rows: List<PlaylistTrackEntity>,
    onDismiss: () -> Unit
) {
    val accent = LocalAccentColor.current
    var name by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description) }
    var coverUri by remember { mutableStateOf(playlist.coverUri) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var eq by remember { mutableStateOf(playlist.eqPreset) }
    var eqMenu by remember { mutableStateOf(false) }
    var mode by remember {
        mutableStateOf(
            when {
                playlist.coverUri == null -> CoverMode.AUTO
                playlist.coverUri.startsWith("file:") -> CoverMode.PHOTO
                else -> CoverMode.SONG
            }
        )
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photo = uri
            mode = CoverMode.PHOTO
        }
    }
    // What the cover would be if saved now (a song not yet picked, or no photo, means Auto).
    val chosenCover = when (mode) {
        CoverMode.AUTO -> null
        CoverMode.SONG -> coverUri?.takeUnless { it.startsWith("file:") }
        CoverMode.PHOTO -> coverUri?.takeIf { it.startsWith("file:") }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PrimaryText,
        unfocusedTextColor = PrimaryText,
        focusedBorderColor = accent,
        unfocusedBorderColor = TerminalBorder,
        focusedLabelColor = accent,
        unfocusedLabelColor = MutedText
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TerminalSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("EDIT PLAYLIST", color = accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.2.sp)

            val previewModifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally)
            if (photo != null) {
                AsyncImage(photo, "Cover", previewModifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            } else {
                PlaylistCover(playlist.copy(coverUri = chosenCover), rows, previewModifier, corner = 12.dp)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                CoverMode.entries.forEach { m ->
                    val selected = m == mode
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) accent else TerminalRaisedPanel,
                        border = BorderStroke(1.dp, if (selected) accent else TerminalBorder),
                        modifier = Modifier.clickable {
                            when (m) {
                                CoverMode.PHOTO -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else -> {
                                    mode = m
                                    photo = null
                                }
                            }
                        }
                    ) {
                        Text(
                            m.label,
                            color = if (selected) buttonContentColorFor(accent) else PrimaryText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (mode == CoverMode.SONG) {
                val songs = remember(rows) { rows.distinctBy { it.uri } }
                if (songs.isEmpty()) {
                    Text("Add songs first to use their covers", color = MutedText, style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(songs, key = { it.uri }) { song ->
                            val picked = song.uri == coverUri
                            AsyncImage(
                                model = song.art(),
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TerminalRaisedPanel)
                                    .border(2.dp, if (picked) accent else TerminalBorder, RoundedCornerShape(6.dp))
                                    .clickable { coverUri = song.uri }
                            )
                        }
                    }
                }
            } else {
                Text(
                    if (mode == CoverMode.AUTO) "Auto uses the covers of the first songs" else "Tap From phone again to pick another photo",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                minLines = 2,
                maxLines = 4,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TerminalBlack,
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth().clickable { eqMenu = true }
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Equalizer, null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Equalizer", color = PrimaryText, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                eq?.let { "$it · only while playing this playlist" } ?: "My usual equalizer",
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("›", color = MutedText, fontSize = 18.sp)
                    }
                }
                DropdownMenu(expanded = eqMenu, onDismissRequest = { eqMenu = false }) {
                    (listOf<String?>(null) + EqualizerHelper.PRESETS).forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    preset ?: "My usual equalizer",
                                    color = if (preset == eq) accent else PrimaryText,
                                    fontWeight = if (preset == eq) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                eq = preset
                                eqMenu = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val updated = playlist.copy(name = name, description = description, coverUri = chosenCover, eqPreset = eq)
                        viewModel.updatePlaylistDetails(playlist, updated, photo)
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent)),
                    modifier = Modifier.weight(1f)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * The playlist's + button: every song with a tick box, searchable by title, artist or album.
 * Songs already in the playlist are shown ticked and greyed out.
 */
@Composable
fun AddSongsScreen(
    viewModel: MainViewModel,
    playlist: PlaylistEntity,
    inPlaylist: Set<String>,
    onDone: () -> Unit
) {
    val accent = LocalAccentColor.current
    val allTracks by viewModel.allTracks.collectAsState()
    val sorted = remember(allTracks) { allTracks.sortedBy { it.title.lowercase() } }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val shown = remember(sorted, query) {
        if (query.isBlank()) sorted
        else sorted.filter { t -> listOf(t.title, t.artist, t.album).any { it.contains(query.trim(), ignoreCase = true) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Close", tint = PrimaryText) }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "ADD TO ${playlist.name.uppercase()}",
                    color = accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${picked.size} selected", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by title, artist, album...", color = MutedText) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MutedText) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText,
                focusedBorderColor = accent,
                unfocusedBorderColor = TerminalBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(shown, key = { it.id }) { track ->
                val already = track.id in inPlaylist
                val checked = already || track.id in picked
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !already) { picked = if (track.id in picked) picked - track.id else picked + track.id }
                        .alpha(if (already) 0.4f else 1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    AsyncImage(
                        model = track.artModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(TerminalRaisedPanel)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (already) "${track.artist} · already in playlist" else track.artist,
                            color = MutedText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = !already,
                        colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = buttonContentColorFor(accent))
                    )
                }
            }
        }
        Button(
            onClick = {
                // In list order, not the order they were ticked.
                viewModel.addTracksToPlaylist(playlist.id, sorted.filter { it.id in picked })
                onDone()
            },
            enabled = picked.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp)
        ) {
            Text(
                when (picked.size) { 0 -> "Pick songs to add"; 1 -> "Add 1 song"; else -> "Add ${picked.size} songs" },
                fontWeight = FontWeight.Bold
            )
        }
    }
}
