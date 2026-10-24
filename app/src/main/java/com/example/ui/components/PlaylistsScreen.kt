package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Edit
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
import com.example.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val folderPlaylists by viewModel.folderPlaylists.collectAsState()
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

    // A folder playlist opens as a track list, the same way the Folders screen does.
    folderPlaylists.firstOrNull { it.first == openFolderName }?.let { (name, tracks) ->
        BackHandler { viewModel.closeFolderPlaylist() }
        LibraryScreen(
            viewModel = viewModel,
            title = name.uppercase(),
            tracksOverride = tracks,
            onBack = { viewModel.closeFolderPlaylist() }
        )
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
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
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
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryText
                                        )
                                        Text(
                                            text = "Tap to open and play",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = playlist.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "[ ${trackList.size} TRACKS ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (trackList.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.playTrackList(trackList, 0, source = playlist.name, isPlaylist = true)
                        viewModel.navigateTo(Screen.PLAYER)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    Text(text = "Add songs to this playlist from the Library screen", style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                val idx = playlistTracks.indexOf(pt)
                                viewModel.playTrackList(trackList, idx.coerceAtLeast(0), source = playlist.name, isPlaylist = true)
                                viewModel.navigateTo(Screen.PLAYER)
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
                                onClick = { viewModel.removeTrackFromPlaylist(playlist.id, pt.trackId) },
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
