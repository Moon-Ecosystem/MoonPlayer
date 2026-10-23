package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
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

@Composable
fun GroupedTracksScreen(
    viewModel: MainViewModel,
    mode: GroupMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTracks by viewModel.allTracks.collectAsState()
    val accent = LocalAccentColor.current

    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var folderResultInfo by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            viewModel.linkFolderAndScan(uri, autoImportAsPlaylist = false) { folderName, count ->
                folderResultInfo = Pair(folderName, count)
            }
        }
    }

    val groups = remember(allTracks, mode) {
        when (mode) {
            GroupMode.ALBUMS -> allTracks.groupBy { it.album }
            GroupMode.ARTISTS -> allTracks.groupBy { it.artist }
            GroupMode.FOLDERS -> allTracks.groupBy { it.folderPath }
        }
    }

    if (selectedGroup != null) {
        val tracksForGroup = groups[selectedGroup] ?: emptyList()
        LibraryScreen(
            viewModel = viewModel,
            title = selectedGroup!!.uppercase(),
            tracksOverride = tracksForGroup,
            onBack = { selectedGroup = null }
        )
    } else {
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
                            text = mode.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "[ ${groups.size} ENTRIES ]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // If Folders Mode, show Link Folder action button
                if (mode == GroupMode.FOLDERS) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = TerminalBlack),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("link_folder_button")
                    ) {
                        Icon(imageVector = Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Link Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Informational tip when in Folders mode
            if (mode == GroupMode.FOLDERS) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TerminalRaisedPanel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Link any folder on your device. Only .mp3 audio files will be extracted without folder clutter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "> NO ${mode.title} DISCOVERED", style = MaterialTheme.typography.labelLarge, color = MutedText)
                        if (mode == GroupMode.FOLDERS) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = TerminalBlack)
                            ) {
                                Text("Link Device Folder")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups.keys.toList()) { groupKey ->
                        val groupTracks = groups[groupKey] ?: emptyList()
                        val sampleTrack = groupTracks.firstOrNull()

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalRaisedPanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroup = groupKey }
                                .testTag("group_item_${groupKey.take(10)}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(if (mode == GroupMode.ARTISTS) CircleShape else RoundedCornerShape(6.dp))
                                            .background(TerminalBlack)
                                            .border(0.5.dp, TerminalBorder, if (mode == GroupMode.ARTISTS) CircleShape else RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (sampleTrack?.albumArtUri != null) {
                                            AsyncImage(
                                                model = sampleTrack.albumArtUri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = R.drawable.moon_player_logo)
                                            )
                                        } else {
                                            val icon = when (mode) {
                                                GroupMode.ALBUMS -> Icons.Default.Album
                                                GroupMode.ARTISTS -> Icons.Default.Person
                                                GroupMode.FOLDERS -> Icons.Default.Folder
                                            }
                                            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = groupKey,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryText
                                        )
                                        Text(
                                            text = "${groupTracks.size} MP3s (tap to view)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (mode == GroupMode.FOLDERS) {
                                        IconButton(
                                            onClick = {
                                                viewModel.createPlaylist(groupKey)
                                                // add all tracks to newly created playlist
                                                val plList = viewModel.allPlaylists.value
                                                val created = plList.firstOrNull { it.name == groupKey }
                                                if (created != null) {
                                                    groupTracks.forEach { trk ->
                                                        viewModel.addTrackToPlaylist(created.id, trk)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistAdd,
                                                contentDescription = "Import as Playlist",
                                                tint = accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.playTrackList(groupTracks, 0)
                                            viewModel.navigateTo(Screen.PLAYER)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = TerminalBlack),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // FOLDER SCAN RESULT CONFIRMATION DIALOG
    folderResultInfo?.let { (name, count) ->
        AlertDialog(
            onDismissRequest = { folderResultInfo = null },
            title = { Text("Folder Linked: $name", color = PrimaryText) },
            text = {
                Column {
                    Text(
                        text = "Successfully discovered $count .mp3 file(s) in this folder. Only MP3 files have been linked; all subfolders and other files are omitted.",
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val folderTracks = allTracks.filter { it.folderPath == name }
                        if (folderTracks.isNotEmpty()) {
                            viewModel.createPlaylist(name)
                            // Auto-add to playlist
                            val plList = viewModel.allPlaylists.value
                            val pl = plList.firstOrNull { it.name == name }
                            if (pl != null) {
                                folderTracks.forEach { trk ->
                                    viewModel.addTrackToPlaylist(pl.id, trk)
                                }
                            }
                        }
                        folderResultInfo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = TerminalBlack)
                ) {
                    Text("Import as Playlist")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { folderResultInfo = null }) {
                    Text("Keep in Library", color = PrimaryText)
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }
}

enum class GroupMode(val title: String) {
    ALBUMS("ALBUMS"),
    ARTISTS("ARTISTS"),
    FOLDERS("FOLDERS")
}
