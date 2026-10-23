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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.viewmodel.LibrarySortOrder
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    title: String = "ALL TRACKS",
    tracksOverride: List<Track>? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val accent = LocalAccentColor.current

    val tracks = tracksOverride ?: filteredTracks

    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP APP BAR
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "[ ${tracks.size} MP3 FILES ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu Button
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort order",
                            tint = PrimaryText
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(TerminalRaisedPanel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Title (A-Z)", color = PrimaryText) },
                            onClick = {
                                viewModel.setSortOrder(LibrarySortOrder.TITLE_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist (A-Z)", color = PrimaryText) },
                            onClick = {
                                viewModel.setSortOrder(LibrarySortOrder.ARTIST_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Album (A-Z)", color = PrimaryText) },
                            onClick = {
                                viewModel.setSortOrder(LibrarySortOrder.ALBUM_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration (Longest first)", color = PrimaryText) },
                            onClick = {
                                viewModel.setSortOrder(LibrarySortOrder.DURATION_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Newest)", color = PrimaryText) },
                            onClick = {
                                viewModel.setSortOrder(LibrarySortOrder.DATE_ADDED_DESC)
                                showSortMenu = false
                            }
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.scanAudio() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan Library",
                        tint = PrimaryText
                    )
                }
            }
        }

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search by title, artist, album...", color = MutedText) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MutedText
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MutedText
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("library_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TerminalRaisedPanel,
                unfocusedContainerColor = TerminalRaisedPanel,
                focusedBorderColor = accent,
                unfocusedBorderColor = TerminalBorder,
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText
            ),
            shape = RoundedCornerShape(8.dp)
        )

        // PLAY ALL / SHUFFLE ALL BAR
        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.playTrackList(tracks, 0)
                        viewModel.navigateTo(Screen.PLAYER)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = TerminalBlack
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val shuffled = tracks.shuffled()
                        viewModel.playTrackList(shuffled, 0)
                        viewModel.navigateTo(Screen.PLAYER)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalRaisedPanel,
                        contentColor = PrimaryText
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // TRACK LIST
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.moon_player_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(TerminalRaisedPanel)
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "> NO TRACKS FOUND",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan device storage for MP3 files or reset search",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.scanAudio() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = TerminalBlack
                        )
                    ) {
                        Text("Scan Storage")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks, key = { index, trk -> "${trk.id}_$index" }) { index, track ->
                    val isCurrent = track.id == currentTrack?.id
                    TrackListItem(
                        track = track,
                        index = index + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isFavorite = favoriteIds.contains(track.id),
                        accentColor = accent,
                        onPlay = {
                            viewModel.playTrackList(tracks, index)
                            viewModel.navigateTo(Screen.PLAYER)
                        },
                        onPlayNext = { viewModel.playNext(track) },
                        onAddToQueue = { viewModel.addToQueue(track) },
                        onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                        onToggleFavorite = { viewModel.toggleFavorite(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackListItem(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) TerminalSurfaceElevated else TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) accentColor.copy(alpha = 0.8f) else TerminalBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("track_item_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork / Mascot Thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalBlack)
                    .border(0.5.dp, TerminalBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (track.albumArtUri != null) {
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.moon_player_logo)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.moon_player_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) accentColor else PrimaryText,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration in monospace
            Text(
                text = track.formattedDuration,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 6.dp)
            )

            // Favorite Icon
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(32.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) accentColor else MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Overflow Menu
            Box {
                IconButton(
                    onClick = { showTrackMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false },
                    modifier = Modifier.background(TerminalRaisedPanel)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Now", color = PrimaryText) },
                        onClick = {
                            showTrackMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next", color = PrimaryText) },
                        onClick = {
                            showTrackMenu = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = PrimaryText) },
                        onClick = {
                            showTrackMenu = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = PrimaryText) },
                        onClick = {
                            showTrackMenu = false
                            onAddToPlaylist()
                        }
                    )
                }
            }
        }
    }
}
