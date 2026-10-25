package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.PlaylistEntity
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
import com.example.viewmodel.LibrarySortOrder
import com.example.viewmodel.Screen
import com.example.viewmodel.searchAndSort
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val folderPlaylists by viewModel.folderPlaylists.collectAsState()
    val playlistRows by viewModel.playlistRows.collectAsState()
    val accent = LocalAccentColor.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renamePlaylistName by remember { mutableStateOf("") }
    val openFolderName by viewModel.openFolderPlaylist.collectAsState()

    val pickFolder = rememberFolderPicker { uri, flags ->
        viewModel.addFolder(uri, flags)
    }

    // A folder playlist opens as the same playlist page as yours, read-only.
    folderPlaylists.firstOrNull { it.first == openFolderName }?.let { (name, tracks) ->
        BackHandler { viewModel.closeFolderPlaylist() }
        CollectionScreen(viewModel, CollectionKind.FOLDER, name, tracks, onBack = { viewModel.closeFolderPlaylist() }, isPlaylist = true)
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newPlaylistName = ""
                    showCreateDialog = true
                },
                containerColor = accent,
                contentColor = com.example.ui.theme.buttonContentColorFor(accent),
                modifier = Modifier.testTag("create_playlist_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Playlist")
            }
        },
        containerColor = TerminalBlack
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "PLAYLISTS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "[ ${folderPlaylists.size} FOLDERS · ${playlists.size} CUSTOM ]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = pickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalRaisedPanel, contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("import_folder_playlist_button")
                ) {
                    Icon(imageVector = Icons.Default.DriveFolderUpload, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Folder", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty() && folderPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "> NO PLAYLISTS CREATED",
                            style = MaterialTheme.typography.labelLarge,
                            color = MutedText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add a music folder, or tap + to create a custom playlist",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (folderPlaylists.isNotEmpty()) {
                        item { PlaylistSectionLabel("FOLDERS") }
                    }
                    itemsIndexed(folderPlaylists) { _, (name, tracks) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalRaisedPanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openFolderPlaylist(name) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TracksCover(tracks, Modifier.size(48.dp), corner = 6.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryText
                                    )
                                    Text(
                                        text = "${tracks.size} tracks · updates with the folder",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedText
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.playTrackList(tracks, 0, source = name, isPlaylist = true) },
                                    enabled = tracks.isNotEmpty(),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play $name",
                                        tint = accent
                                    )
                                }
                            }
                        }
                    }
                    if (playlists.isNotEmpty()) {
                        item { PlaylistSectionLabel("MY PLAYLISTS") }
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalRaisedPanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openPlaylist(playlist) }
                                .testTag("playlist_item_${playlist.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val rows = playlistRows[playlist.id].orEmpty()
                                    PlaylistCover(playlist, rows, Modifier.size(48.dp), corner = 6.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                playlist.description.takeIf { it.isNotBlank() },
                                                "${rows.size} ${if (rows.size == 1) "song" else "songs"}"
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            playlistToRename = playlist
                                            renamePlaylistName = playlist.name
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = MutedText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { playlistToDelete = playlist },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MutedText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", color = PrimaryText) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("e.g. Chill Session, Coding Grooves", color = MutedText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = TerminalBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    )
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCreateDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }

    // RENAME PLAYLIST DIALOG
    playlistToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist", color = PrimaryText) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = TerminalBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamePlaylistName.isNotBlank()) {
                            viewModel.renamePlaylist(target.id, renamePlaylistName)
                            playlistToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { playlistToRename = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }

    // DELETE PLAYLIST DIALOG
    playlistToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist?", color = PrimaryText) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${target.name}'? Audio files on device will not be deleted.",
                    color = SecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { playlistToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }
}

/**
 * "12 SONGS · 48 MIN · CREATED 3 OCT" (the year only when it isn't this year); with no
 * creation date (folders, albums...) just "12 SONGS · 48 MIN".
 */
private fun playlistSummary(songs: Int, totalMs: Long, createdAt: Long?): String {
    val minutes = totalMs / 60_000
    val length = if (minutes >= 60) "${minutes / 60} H ${minutes % 60} MIN" else "$minutes MIN"
    val count = "$songs ${if (songs == 1) "SONG" else "SONGS"}"
    if (createdAt == null) return "$count · $length"
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val created = java.util.Calendar.getInstance().apply { timeInMillis = createdAt }
    val pattern = if (created.get(java.util.Calendar.YEAR) == thisYear) "d MMM" else "d MMM yyyy"
    val date = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(createdAt))
    return "$count · $length · CREATED ${date.uppercase()}"
}

/**
 * The top of a playlist page, shared by your playlists and read-only collections: back,
 * ⋮ [menu] (null hides it), then cover with name, description, summary and Play / Shuffle.
 */
@Composable
private fun PlaylistHeader(
    kind: String = "PLAYLIST",
    name: String,
    description: String,
    summary: String,
    cover: @Composable (Modifier) -> Unit,
    canPlay: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)?
) {
    val accent = LocalAccentColor.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText)
        }
        Text(kind, style = MaterialTheme.typography.labelSmall, color = MutedText, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
        Box(Modifier.minimumInteractiveComponentSize()) {
            if (menu != null) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Playlist options", tint = PrimaryText)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        }
    }

    // Text at the top of the cover's height and the buttons at its bottom, so there's no gap
    // above the name; a long name or description makes the row taller instead.
    Row(modifier = Modifier.padding(top = 4.dp).height(IntrinsicSize.Min)) {
        cover(Modifier.size(120.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxHeight()) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(44.dp)) {
                Button(
                    onClick = onPlay,
                    enabled = canPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShuffle,
                    enabled = canPlay,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.width(44.dp).fillMaxHeight()
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
}

/** A read-only song collection; [naturalOrder] names the order its songs come in. */
enum class CollectionKind(val label: String, val naturalOrder: String) {
    FOLDER("FOLDER", "Folder order"),
    ALBUM("ALBUM", "Track order"),
    ARTIST("ARTIST", "Album order"),
    FAVORITES("FAVORITES", "Default order"),
    HISTORY("RECENTLY PLAYED", "Most recent first")
}

/** "ALBUM · LiSA · 2014 · 12 SONGS · 48 MIN", "ARTIST · 4 ALBUMS · 31 SONGS · 2 H 5 MIN", ... */
private fun collectionSummary(kind: CollectionKind, tracks: List<Track>): String {
    val songs = playlistSummary(tracks.size, tracks.sumOf { it.durationMs }, createdAt = null)
    val extra = when (kind) {
        CollectionKind.ALBUM -> listOfNotNull(
            tracks.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key?.uppercase(),
            tracks.firstOrNull { it.year > 0 }?.year?.toString()
        )
        CollectionKind.ARTIST -> tracks.map { it.album }.distinct().size.let { listOf("$it ${if (it == 1) "ALBUM" else "ALBUMS"}") }
        else -> emptyList()
    }
    return (listOf(kind.label).takeIf { kind != CollectionKind.FAVORITES && kind != CollectionKind.HISTORY }.orEmpty() + extra + songs)
        .joinToString(" · ")
}

/**
 * A folder playlist, album, artist, Favorites or Recently Played: the same page as your
 * playlists, but read-only. Instead of editing it can be sorted (remembered per page) or saved
 * as a playlist of yours. [isPlaylist] credits plays to it in the stats (folder playlists).
 */
@Composable
fun CollectionScreen(
    viewModel: MainViewModel,
    kind: CollectionKind,
    name: String,
    givenTracks: List<Track>,
    onBack: () -> Unit,
    isPlaylist: Boolean = false
) {
    val sortKey = "${kind.name}:$name"
    val sort by remember(sortKey) { viewModel.listSort(sortKey) }.collectAsState(initial = null)
    val natural = remember(givenTracks, kind) {
        if (kind == CollectionKind.ALBUM || kind == CollectionKind.ARTIST) searchAndSort(givenTracks, "", LibrarySortOrder.ALBUM_ASC)
        else givenTracks
    }
    val tracks = remember(natural, sort) { sort?.let { searchAndSort(natural, "", it) } ?: natural }
    var sortMenu by remember { mutableStateOf(false) }
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val favoriteIdList by viewModel.favoriteTrackIds.collectAsState()
    val favoriteIds = remember(favoriteIdList) { favoriteIdList.toHashSet() }
    fun play(list: List<Track>, index: Int) {
        viewModel.playTrackList(list, index, source = name, isPlaylist = isPlaylist)
        viewModel.navigateTo(Screen.PLAYER)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistHeader(
            kind = kind.label,
            name = name,
            description = "",
            summary = collectionSummary(kind, tracks),
            cover = { TracksCover(tracks, it, corner = 10.dp) },
            canPlay = tracks.isNotEmpty(),
            onBack = onBack,
            onPlay = { play(tracks, 0) },
            onShuffle = { play(tracks.shuffled(), 0) }
        ) { close ->
            DropdownMenuItem(
                text = { Text("Save as new playlist", color = PrimaryText) },
                enabled = tracks.isNotEmpty(),
                onClick = { close(); viewModel.saveAsPlaylist(name, tracks) }
            )
        }
        Box(Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "SORT: ${(sort?.label ?: kind.naturalOrder).uppercase()} ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { sortMenu = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                // null: the page's own order (folder, track, most recent...).
                (listOf(null) + LibrarySortOrder.entries).forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                order?.label ?: kind.naturalOrder,
                                color = if (order == sort) LocalAccentColor.current else PrimaryText,
                                fontWeight = if (order == sort) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = { sortMenu = false; viewModel.setListSort(sortKey, order) }
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // By position: Recently Played can list a song more than once.
            itemsIndexed(tracks, key = { index, _ -> index }) { index, track ->
                // The same row as All Songs: heart, Play Next, Add to Queue, Add to Playlist.
                val isCurrent = track.id == currentTrack?.id
                TrackListItem(
                    track = track,
                    index = index + 1,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isFavorite = track.id in favoriteIds,
                    accentColor = LocalAccentColor.current,
                    onPlay = { play(tracks, index) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddToQueue = { viewModel.addToQueue(track) },
                    onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun PlaylistDetailScreen(
    viewModel: MainViewModel,
    playlist: PlaylistEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedTracks by viewModel.playlistTracks.collectAsState()
    val accent = LocalAccentColor.current

    // Moves instantly while dragging; the order is saved once when the drag ends.
    var playlistTracks by remember(savedTracks) { mutableStateOf(savedTracks) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = playlistTracks.indexOfFirst { it.id == from.key }
        val toIndex = playlistTracks.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            playlistTracks = playlistTracks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val trackList = remember(playlistTracks) {
        playlistTracks.map { pt ->
            Track(
                id = pt.trackId,
                contentUri = pt.uri,
                title = pt.title,
                artist = pt.artist,
                album = pt.album,
                durationMs = pt.durationMs,
                albumArtUri = pt.albumArtUri
            )
        }
    }

    // The opened snapshot doesn't change on edit; the live row does.
    val playlists by viewModel.allPlaylists.collectAsState()
    val current = playlists.firstOrNull { it.id == playlist.id } ?: playlist
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (adding) {
        BackHandler { adding = false }
        AddSongsScreen(viewModel, current, savedTracks.map { it.trackId }.toSet(), onDone = { adding = false })
        return
    }
    if (editing) {
        EditPlaylistSheet(viewModel, current, savedTracks, onDismiss = { editing = false })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete Playlist?", color = PrimaryText) },
            text = { Text("Are you sure you want to delete '${current.name}'? Audio files on device will not be deleted.", color = SecondaryText) },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = false
                        viewModel.deletePlaylist(current.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = androidx.compose.ui.graphics.Color.White)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleting = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) { Text("Cancel") }
            },
            containerColor = TerminalSurfaceElevated
        )
    }
    fun play(tracks: List<Track>, index: Int) {
        viewModel.playTrackList(tracks, index, source = current.name, isPlaylist = true, playlistId = current.id)
        viewModel.navigateTo(Screen.PLAYER)
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistHeader(
            name = current.name,
            description = current.description,
            summary = playlistSummary(trackList.size, trackList.sumOf { it.durationMs }, current.createdAt),
            cover = { PlaylistCover(current, savedTracks, it.clickable { editing = true }, corner = 10.dp) },
            canPlay = trackList.isNotEmpty(),
            onBack = onBack,
            onPlay = { play(trackList, 0) },
            onShuffle = { play(trackList.shuffled(), 0) }
        ) { close ->
            DropdownMenuItem(text = { Text("Edit details", color = PrimaryText) }, onClick = { close(); editing = true })
            DropdownMenuItem(text = { Text("Add songs", color = PrimaryText) }, onClick = { close(); adding = true })
            DropdownMenuItem(
                text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                onClick = { close(); deleting = true }
            )
        }

        if (trackList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "> PLAYLIST IS EMPTY", style = MaterialTheme.typography.labelLarge, color = MutedText)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Tap + to add songs", style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // Room to scroll the last song out from under the + button.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
            ) {
                items(playlistTracks, key = { it.id }) { pt ->
                  ReorderableItem(reorderState, key = pt.id) { isDragging ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDragging) TerminalSurfaceElevated else TerminalRaisedPanel,
                        shadowElevation = if (isDragging) 8.dp else 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                play(trackList, playlistTracks.indexOf(pt).coerceAtLeast(0))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pt.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pt.artist} · ${Track.formatDuration(pt.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removePlaylistRow(pt.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove from playlist",
                                    tint = MutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MutedText,
                                modifier = Modifier
                                    // Long-press first, so a scroll that starts on the handle stays a scroll.
                                    .longPressDraggableHandle(
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = {
                                            viewModel.reorderPlaylist(playlistTracks.map { it.id })
                                        }
                                    )
                                    .size(40.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                  }
                }
            }
        }
    }
    // Add songs, bottom right like the Playlists screen's create button.
    FloatingActionButton(
        onClick = { adding = true },
        containerColor = accent,
        contentColor = com.example.ui.theme.buttonContentColorFor(accent),
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add songs")
    }
    }
}

@Composable
fun AddToPlaylistDialog(
    viewModel: MainViewModel,
    track: Track,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val accent = LocalAccentColor.current
    var showCreateInDialog by remember { mutableStateOf(false) }
    var newPlName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add '${track.title.take(20)}' to...", color = PrimaryText) },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one below!", color = MutedText)
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(playlists) { pl ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TerminalRaisedPanel,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, TerminalBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.addTrackToPlaylist(pl.id, track)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = null, tint = accent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(pl.name, color = PrimaryText, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showCreateInDialog) {
                    OutlinedTextField(
                        value = newPlName,
                        onValueChange = { newPlName = it },
                        placeholder = { Text("Playlist name", color = MutedText) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrimaryText,
                            unfocusedTextColor = PrimaryText,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = TerminalBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (newPlName.isNotBlank()) {
                                viewModel.createPlaylist(newPlName)
                                showCreateInDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                        )
                    ) {
                        Text("Create & Select", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showCreateInDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = accent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Playlist")
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
            ) {
                Text("Close")
            }
        },
        containerColor = TerminalSurfaceElevated
    )
}
