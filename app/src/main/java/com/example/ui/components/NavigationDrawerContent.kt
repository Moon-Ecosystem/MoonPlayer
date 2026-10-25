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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
fun NavigationDrawerContent(
    viewModel: MainViewModel,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val quickPlaylists by viewModel.quickPlaylists.collectAsState()
    val playlistRows by viewModel.playlistRows.collectAsState()
    val folderPlaylists by viewModel.folderPlaylists.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val accent = LocalAccentColor.current

    Surface(
        // The panel itself, not just its contents, stays clear of the clock and the gesture bar.
        modifier = modifier
            .safeDrawingPadding()
            .fillMaxHeight()
            .width(300.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            // BRAND HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.moon_player_logo),
                    contentDescription = "Moon Player Mascot",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TerminalBlack)
                        .border(1.dp, TerminalBorder, CircleShape)
                        .padding(4.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "MOON PLAYER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "[ OFFLINE // LOCAL AUDIO ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // NOW PLAYING SHORTCUT
            DrawerItem(
                icon = Icons.Default.PlayCircle,
                label = "Now Playing",
                isSelected = currentScreen == Screen.PLAYER,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.PLAYER)
                    onCloseDrawer()
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // PLAYLISTS: the one playing, then the most recently played (up to 4), so a playlist
            // is one tap from the drawer instead of going through the Playlists screen.
            Text(
                text = "PLAYLISTS",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                letterSpacing = 1.2.sp
            )
            quickPlaylists.forEach { item ->
                QuickPlaylistRow(
                    item,
                    item.playlist?.let { playlistRows[it.id] }.orEmpty(),
                    if (item.playlist == null) folderPlaylists.firstOrNull { it.first == item.name }?.second.orEmpty() else emptyList(),
                    accent,
                    isPlaying
                ) {
                    item.playlist?.let { viewModel.openPlaylist(it) } ?: viewModel.openFolderPlaylist(item.name)
                    onCloseDrawer()
                }
            }
            Text(
                text = if (quickPlaylists.isEmpty()) "Create a playlist ›" else "All playlists ›",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.navigateTo(Screen.PLAYLISTS)
                        onCloseDrawer()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )

            // MUSIC SECTIONS
            Text(
                text = "LIBRARY",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                letterSpacing = 1.2.sp
            )

            DrawerItem(
                icon = Icons.Default.MusicNote,
                label = "All Songs (${allTracks.size})",
                isSelected = currentScreen == Screen.LIBRARY,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.LIBRARY)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Album,
                label = "Albums",
                isSelected = currentScreen == Screen.ALBUMS,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.ALBUMS)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Person,
                label = "Artists",
                isSelected = currentScreen == Screen.ARTISTS,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.ARTISTS)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Folder,
                label = "Folders",
                isSelected = currentScreen == Screen.FOLDERS,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.FOLDERS)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Favorite,
                label = "Favorites",
                isSelected = currentScreen == Screen.FAVORITES,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.FAVORITES)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.History,
                label = "Recently Played",
                isSelected = currentScreen == Screen.HISTORY,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.HISTORY)
                    onCloseDrawer()
                }
            )

            // Queue, equalizer and sleep timer live on the player; rescanning is in Settings.
            HorizontalDivider(color = TerminalBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

            DrawerItem(
                icon = Icons.Default.BarChart,
                label = "Listening Stats",
                isSelected = currentScreen == Screen.STATS,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.STATS)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentScreen == Screen.SETTINGS,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.SETTINGS)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = Icons.Default.Info,
                label = "About Moon Player",
                isSelected = currentScreen == Screen.ABOUT,
                accentColor = accent,
                onClick = {
                    viewModel.navigateTo(Screen.ABOUT)
                    onCloseDrawer()
                }
            )
        }
    }
}

/** A playlist in the drawer: icon tile, name, and "Playing · Folder · N songs" (what applies) underneath. */
@Composable
private fun QuickPlaylistRow(
    item: MainViewModel.QuickPlaylist,
    rows: List<com.example.data.db.PlaylistTrackEntity>,
    folderTracks: List<com.example.data.model.Track>,
    accent: androidx.compose.ui.graphics.Color,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.playing) accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (item.playlist != null) {
            PlaylistCover(item.playlist, rows, Modifier.size(36.dp), corner = 6.dp)
        } else {
            TracksCover(folderTracks, Modifier.size(36.dp), corner = 6.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.playing) accent else PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    "Playing".takeIf { item.playing },
                    "Folder".takeIf { item.playlist == null },
                    if (item.songs == 1) "1 song" else "${item.songs} songs"
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.playing) accent else MutedText
            )
        }
        if (item.playing && isPlaying) EqualizerWaveIndicator(accent, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) TerminalSurfaceElevated else androidx.compose.ui.graphics.Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("drawer_item_${label.lowercase().replace(" ", "_").take(15)}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else SecondaryText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) accentColor else PrimaryText,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
