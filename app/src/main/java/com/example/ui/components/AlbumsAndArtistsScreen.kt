package com.example.ui.components

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.artModel
import com.example.R
import com.example.data.model.Track
import com.example.data.model.splitArtists
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
    val allTracks by viewModel.allTracks.collectAsState()
    val accent = LocalAccentColor.current

    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val groups = remember(allTracks, mode) {
        when (mode) {
            GroupMode.ALBUMS -> allTracks.groupBy { it.album }
            // A song with several artists is listed under each of them.
            GroupMode.ARTISTS -> allTracks
                .flatMap { track -> splitArtists(track.artist).map { it to track } }
                .groupBy({ it.first }, { it.second })
        }
    }

    if (selectedGroup != null) {
        val tracksForGroup = groups[selectedGroup] ?: emptyList()
        androidx.activity.compose.BackHandler { selectedGroup = null }
        CollectionScreen(
            viewModel = viewModel,
            kind = when (mode) {
                GroupMode.ALBUMS -> CollectionKind.ALBUM
                GroupMode.ARTISTS -> CollectionKind.ARTIST
            },
            name = selectedGroup!!.ifBlank { "Unknown" },
            givenTracks = tracksForGroup,
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "> NO ${mode.title} DISCOVERED", style = MaterialTheme.typography.labelLarge, color = MutedText)
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
                                        val icon = when (mode) {
                                            GroupMode.ALBUMS -> Icons.Default.Album
                                            GroupMode.ARTISTS -> Icons.Default.Person
                                        }
                                        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                                        sampleTrack?.let {
                                            AsyncImage(
                                                model = it.artModel,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = groupKey,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (groupTracks.size == 1) "1 song" else "${groupTracks.size} songs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Copying a folder into a playlist is the folder page's ⋮ › Save as new playlist.
                                    Surface(
                                        onClick = {
                                            viewModel.playTrackList(groupTracks, 0, source = groupKey)
                                            viewModel.navigateTo(Screen.PLAYER)
                                        },
                                        shape = CircleShape,
                                        color = accent,
                                        modifier = Modifier.padding(start = 4.dp).size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play $groupKey",
                                                tint = com.example.ui.theme.buttonContentColorFor(accent),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    // The rest of the row opens the album, artist or folder.
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MutedText,
                                        modifier = Modifier.padding(start = 6.dp).size(22.dp)
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

// Folders are on the Playlists screen, one folder playlist per folder that holds songs.
enum class GroupMode(val title: String) {
    ALBUMS("ALBUMS"),
    ARTISTS("ARTISTS")
}
