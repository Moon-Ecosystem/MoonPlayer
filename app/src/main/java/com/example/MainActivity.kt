package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.ui.components.AboutScreen
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CollectionKind
import com.example.ui.components.CollectionScreen
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.GroupMode
import com.example.ui.components.GroupedTracksScreen
import com.example.ui.components.LibraryScreen
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.OnboardingDialog
import com.example.ui.components.PlaylistDetailScreen
import com.example.ui.components.PlayerQueuePager
import com.example.ui.components.PlaylistsScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.StatsScreen
import com.example.ui.theme.MoonPlayerTheme
import com.example.ui.theme.TerminalBlack
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark, so force light system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

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
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isEqOpen by viewModel.isEqualizerOpen.collectAsState()
    val isSleepTimerOpen by viewModel.isSleepTimerOpen.collectAsState()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState(initial = emptyList())
    val playHistory by viewModel.playHistory.collectAsState()
    // null until the saved flag loads, so returning users never see the dialog flash
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (onboardingComplete == false) {
        OnboardingDialog(viewModel = viewModel)
    }

    // Hardware Back Button handling
    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.PLAYER) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen == Screen.PLAYLIST_DETAIL) {
            // Same as the page's own back arrow (and folder playlists): back to the list.
            viewModel.navigateTo(Screen.PLAYLISTS)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CURRENT SCREEN CONTENT
                AnimatedContent(
                    targetState = currentScreen,
                    // Player and queue share one pager, so switching between them must not
                    // swap content here; the pager animates that move itself.
                    contentKey = { if (it == Screen.QUEUE) Screen.PLAYER else it },
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "screen",
                    modifier = Modifier.weight(1f)
                ) { screen ->
                when (screen) {
                    Screen.PLAYER, Screen.QUEUE -> {
                        PlayerQueuePager(
                            viewModel = viewModel,
                            currentScreen = screen,
                            onOpenDrawer = { scope.launch { drawerState.open() } }
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
                        CollectionScreen(
                            viewModel = viewModel,
                            kind = CollectionKind.FAVORITES,
                            name = "Favorites",
                            givenTracks = favoriteTracks,
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
                        CollectionScreen(
                            viewModel = viewModel,
                            kind = CollectionKind.HISTORY,
                            name = "Recently played",
                            givenTracks = historyTracks,
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

                // Sits below the screen (not over it), so no list needs extra bottom padding.
                val track = currentTrack
                if (track != null && currentScreen != Screen.PLAYER && currentScreen != Screen.QUEUE) {
                    MiniPlayerBar(viewModel = viewModel, track = track)
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
}
