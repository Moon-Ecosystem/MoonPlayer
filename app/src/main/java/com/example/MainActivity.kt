package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.AboutScreen
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.GroupMode
import com.example.ui.components.GroupedTracksScreen
import com.example.ui.components.LibraryScreen
import com.example.ui.components.MainPlayerScreen
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PlaylistDetailScreen
import com.example.ui.components.PlaylistQueueScreen
import com.example.ui.components.PlaylistsScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.StatsScreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MoonPlayerTheme
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val activeAccent by viewModel.activeAccentColor.collectAsState()

            MoonPlayerTheme(accentColor = activeAccent) {
                MoonPlayerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MoonPlayerApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isEqOpen by viewModel.isEqualizerOpen.collectAsState()
    val isSleepTimerOpen by viewModel.isSleepTimerOpen.collectAsState()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState(initial = emptyList())
    val playHistory by viewModel.playHistory.collectAsState()
    val accent = LocalAccentColor.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Permission check & rationale dialog
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            viewModel.scanAudio()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Show permission explanation prompt if not yet granted
        showPermissionRationale = true
    }

    // Hardware Back Button handling
    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.PLAYER) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen != Screen.PLAYER) {
            viewModel.navigateTo(Screen.PLAYER)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                viewModel = viewModel,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalBlack)
                .safeDrawingPadding(),
            containerColor = TerminalBlack
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CURRENT SCREEN CONTENT
                when (currentScreen) {
                    Screen.PLAYER -> {
                        MainPlayerScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    }

                    Screen.QUEUE -> {
                        PlaylistQueueScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.STATS -> {
                        StatsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.LIBRARY -> {
                        LibraryScreen(
                            viewModel = viewModel,
                            title = "ALL TRACKS",
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.PLAYLISTS -> {
                        PlaylistsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.PLAYLIST_DETAIL -> {
                        selectedPlaylist?.let { pl ->
                            PlaylistDetailScreen(
                                viewModel = viewModel,
                                playlist = pl,
                                onBack = { viewModel.navigateTo(Screen.PLAYLISTS) }
                            )
                        } ?: viewModel.navigateTo(Screen.PLAYLISTS)
                    }

                    Screen.ALBUMS -> {
                        GroupedTracksScreen(
                            viewModel = viewModel,
                            mode = GroupMode.ALBUMS,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.ARTISTS -> {
                        GroupedTracksScreen(
                            viewModel = viewModel,
                            mode = GroupMode.ARTISTS,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.FOLDERS -> {
                        GroupedTracksScreen(
                            viewModel = viewModel,
                            mode = GroupMode.FOLDERS,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.FAVORITES -> {
                        LibraryScreen(
                            viewModel = viewModel,
                            title = "FAVORITES",
                            tracksOverride = favoriteTracks,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.HISTORY -> {
                        val historyTracks = playHistory.map {
                            com.example.data.model.Track(
                                id = it.trackId,
                                contentUri = it.uri,
                                title = it.title,
                                artist = it.artist,
                                album = it.album,
                                durationMs = it.durationMs,
                                albumArtUri = it.albumArtUri
                            )
                        }
                        LibraryScreen(
                            viewModel = viewModel,
                            title = "RECENTLY PLAYED",
                            tracksOverride = historyTracks,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }

                    Screen.ABOUT -> {
                        AboutScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(Screen.PLAYER) }
                        )
                    }
                }
            }
        }
    }

    // DIALOGS & OVERLAYS
    if (isEqOpen) {
        EqualizerDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeEqualizer() }
        )
    }

    if (isSleepTimerOpen) {
        SleepTimerDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSleepTimer() }
        )
    }

    trackToAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { viewModel.setTrackToAddToPlaylist(null) }
        )
    }

    // PERMISSION RATIONALE EXPLANATION DIALOG
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Text(
                    text = "Storage Permission",
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Moon Player needs permission to discover and play your local MP3 audio files. Your audio stays 100% offline and private on your device.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    )
                ) {
                    Text("Grant Permission", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPermissionRationale = false
                        // Fall back to bundled demo tracks
                        viewModel.scanAudio()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryText
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.TerminalBorder)
                ) {
                    Text("Use Demo Tracks")
                }
            },
            containerColor = TerminalSurfaceElevated
        )
    }
}
