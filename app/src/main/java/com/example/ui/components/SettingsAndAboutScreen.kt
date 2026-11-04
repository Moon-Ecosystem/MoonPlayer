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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.settings.AccentMode
import com.example.ui.theme.CelestialCyan
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.WarmIvory
import com.example.data.repository.folderNameFromTreeUri
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val libraryFolders by viewModel.libraryFolders.collectAsState()
    val folderAccessError by viewModel.folderAccessError.collectAsState()
    val accent = LocalAccentColor.current
    val pickFolder = rememberFolderPicker { uri, flags ->
        viewModel.addFolder(uri, flags)
    }
    val tabs = SettingsTab.entries
    // Saved, so rotating the phone keeps the tab.
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "[ PREFERENCES & AUDIO BEHAVIOR ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        SettingsTabRow(tabs.map { it.title }, pager, accent) { i -> scope.launch { pager.animateScrollToPage(i) } }

        // Swipe or tap between tabs. The neighbours stay composed, so a swipe never waits for a
        // page to build; each page scrolls on its own.
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
        val tab = tabs[page]
        fun shows(s: SettingsSection) = s.tab == tab
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {

        if (shows(SettingsSection.APPEARANCE)) {
        SettingsSectionHeader(title = "ACCENT COLOR")

        Spacer(modifier = Modifier.height(8.dp))

        val accentOptions = listOf(
            Triple(AccentMode.DYNAMIC_COVER, "Dynamic (From Album Art)", accent),
            Triple(AccentMode.WARM_IVORY, "Warm Ivory (Default)", WarmIvory),
            Triple(AccentMode.TERMINAL_GREEN, "Terminal Emerald", TerminalGreen),
            Triple(AccentMode.AMBER_GLOW, "Amber Glow", TerminalAmber),
            Triple(AccentMode.CYAN_PULSE, "Celestial Cyan", CelestialCyan),
            Triple(AccentMode.MOON_WHITE, "Moon White", MoonWhite)
        ) + listOfNotNull(
            // Wallpaper colours only exist on Android 12+.
            Triple(AccentMode.SYSTEM, "System (Wallpaper)", viewModel.systemAccentColor())
                .takeIf { android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S }
        )

        accentOptions.forEach { (mode, label, color) ->
            val isSelected = settings.accentMode == mode
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TerminalRaisedPanel,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) color else TerminalBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.setAccentMode(mode) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, TerminalBorder, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        }

        if (shows(SettingsSection.PLAYBACK)) {
        SettingsSectionHeader(title = "PLAYBACK")
        Spacer(modifier = Modifier.height(8.dp))
        // Resume on launch
        SettingsToggleRow(
            title = "Resume on App Launch",
            subtitle = "Reopen paused at the last song and spot",
            checked = settings.resumeOnLaunch,
            onCheckedChange = { viewModel.setResumeOnLaunch(it) },
            accentColor = accent
        )

        // Pause on unplug
        SettingsToggleRow(
            title = "Pause on Disconnect",
            subtitle = "Pause when headphones are unplugged",
            checked = settings.pauseOnHeadphoneDisconnect,
            onCheckedChange = { viewModel.setPauseOnHeadphoneDisconnect(it) },
            accentColor = accent
        )
        }

        if (shows(SettingsSection.PLAYER)) {
        Spacer(modifier = Modifier.height(20.dp))
        SettingsSectionHeader(title = "PLAYER & QUEUE")
        Spacer(modifier = Modifier.height(8.dp))
        // Show remaining time
        SettingsToggleRow(
            title = "Show Time Left",
            subtitle = "Show -2:31 instead of the song's length",
            checked = settings.showRemainingTime,
            onCheckedChange = { viewModel.setShowRemainingTime(it) },
            accentColor = accent
        )
        SettingsToggleRow(
            title = "Format Opens Song Info",
            subtitle = "Tap MP3 · 128 KBPS under the seek bar for file details",
            checked = settings.badgeOpensSongInfo,
            onCheckedChange = { viewModel.setBadgeOpensSongInfo(it) },
            accentColor = accent
        )
        SettingsToggleRow(
            title = "Show Played Songs in Queue",
            subtitle = "Open the queue with played songs listed",
            checked = settings.queueShowPlayed,
            onCheckedChange = { viewModel.setQueueShowPlayed(it) },
            accentColor = accent
        )
        }

        if (shows(SettingsSection.LYRICS)) {
        SettingsSectionHeader(title = "WHERE LYRICS COME FROM")
        Spacer(modifier = Modifier.height(8.dp))
        LyricsSourceOrder(settings.lyricsSourceOrder, accent) { source, up -> viewModel.moveLyricsSource(source, up) }
        Text(
            text = "Tried top to bottom. Change one song's from the line under its lyrics.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )

        SettingsToggleRow(
            title = "Prefer Synced Lyrics",
            subtitle = "Synced lyrics win over the order",
            checked = settings.preferSyncedLyrics,
            onCheckedChange = { viewModel.setPreferSyncedLyrics(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(20.dp))
        SettingsSectionHeader(title = "LRCLIB (ONLINE)")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            title = "Use LRCLIB",
            subtitle = "Look songs up when you tap. Off: nothing goes online",
            checked = settings.lrclibEnabled,
            onCheckedChange = { viewModel.setLrclibEnabled(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(4.dp))

        SettingsToggleRow(
            title = "Look Up Lyrics Automatically",
            subtitle = "Look each song up once, without a tap",
            checked = settings.lrclibEnabled && settings.onlineLyrics == true,
            onCheckedChange = { viewModel.setOnlineLyrics(it) },
            accentColor = accent,
            enabled = settings.lrclibEnabled
        )

        Spacer(modifier = Modifier.height(20.dp))
        SettingsSectionHeader(title = "DISPLAY")
        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            title = "Prefer Romaji Lyrics",
            subtitle = "Open non-Latin songs on romaji when there is some",
            checked = viewModel.effectivePreferRomaji(settings),
            onCheckedChange = { viewModel.setPreferRomaji(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(4.dp))

        SettingsToggleRow(
            title = "Show Source Over Lyrics",
            subtitle = "Briefly show it over the lyrics when there's a choice",
            checked = settings.lyricsSourceChip,
            onCheckedChange = { viewModel.setLyricsSourceChip(it) },
            accentColor = accent
        )
        }

        if (shows(SettingsSection.LIBRARY)) {
        SettingsSectionHeader(title = "MUSIC FOLDERS")
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TerminalRaisedPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Music Folders",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (libraryFolders.isEmpty()) {
                    Text(
                        text = "No folder selected. Add the folders Moon Player may read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                libraryFolders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = folderNameFromTreeUri(folder),
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeFolder(folder) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove ${folderNameFromTreeUri(folder)}",
                                tint = MutedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Music in these folders and their subfolders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                folderAccessError?.let { error ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = pickFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Folder", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.rescanLibrary() },
                        enabled = libraryFolders.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }
            }
        }
        }

        // The Last.fm / Libre.fm account read on the stats page
        if (shows(SettingsSection.STATS)) {
        Spacer(modifier = Modifier.height(20.dp))
        SettingsSectionHeader(title = "SCROBBLE ACCOUNT")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            title = "Show Scrobbles in Stats",
            subtitle = "The Last.fm / Libre.fm side of Listening stats",
            checked = settings.showScrobbles,
            onCheckedChange = { viewModel.setShowScrobbles(it) },
            accentColor = accent
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TerminalRaisedPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (settings.scrobbleUser.isBlank()) {
                    Text(
                        text = "Not connected. Connect in Listening stats › SCROBBLES.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                } else {
                    Text(
                        text = settings.scrobbleUser,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )
                    Text(
                        text = "${settings.scrobbleService.label} · read-only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = viewModel::disconnectScrobbles) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "Removes the saved online stats; your own stay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }
}

/** The lyrics sources in the user's order, each with buttons to move it up or down. */
@Composable
private fun LyricsSourceOrder(
    order: List<com.example.data.lyrics.LyricsSource>,
    accent: Color,
    onMove: (com.example.data.lyrics.LyricsSource, Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            order.forEachIndexed { i, source ->
                if (i > 0) HorizontalDivider(color = TerminalBorder)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text("${i + 1}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                        Text(source.long, style = MaterialTheme.typography.bodySmall, color = MutedText)
                    }
                    IconButton(onClick = { onMove(source, true) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${source.label} up", tint = if (i > 0) PrimaryText else TerminalBorder)
                    }
                    IconButton(onClick = { onMove(source, false) }, enabled = i < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${source.label} down", tint = if (i < order.lastIndex) PrimaryText else TerminalBorder)
                    }
                }
            }
        }
    }
}

/** Settings tabs; About is its own screen, in the drawer. */
internal enum class SettingsTab(val title: String) { PLAYER("Player"), LYRICS("Lyrics"), LOOK("Look"), LIBRARY("Library") }

/** Groups of settings and the tab each sits on, in page order. */
internal enum class SettingsSection(val tab: SettingsTab) {
    PLAYBACK(SettingsTab.PLAYER),
    PLAYER(SettingsTab.PLAYER),
    LYRICS(SettingsTab.LYRICS),
    APPEARANCE(SettingsTab.LOOK),
    LIBRARY(SettingsTab.LIBRARY),
    STATS(SettingsTab.LIBRARY)
}

/**
 * Tabs whose underline follows the pager as it's dragged, sliding and resizing between tab
 * widths, instead of jumping once the swipe settles.
 */
@Composable
private fun SettingsTabRow(titles: List<String>, pager: PagerState, accent: Color, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = pager.currentPage,
        containerColor = TerminalBlack,
        contentColor = accent,
        indicator = { positions ->
            if (positions.isNotEmpty()) {
                // Read here only, so a drag redraws the underline, not the tabs.
                val fraction = pager.currentPageOffsetFraction
                val from = positions[pager.currentPage]
                val to = positions[(pager.currentPage + if (fraction < 0) -1 else 1).coerceIn(positions.indices)]
                val t = kotlin.math.abs(fraction)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = lerp(from.left, to.left, t))
                        .width(lerp(from.width, to.width, t))
                        .height(2.dp)
                        .background(accent)
                )
            }
        },
        divider = { HorizontalDivider(color = TerminalBorder) }
    ) {
        titles.forEachIndexed { i, title ->
            val selected = pager.currentPage == i
            Tab(
                selected = selected,
                onClick = { onSelect(i) },
                text = {
                    Text(
                        title.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) accent else MutedText
                    )
                }
            )
        }
    }
}

@Composable
fun AboutScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "ABOUT MOON PLAYER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "[ VERSION 1.0.0 // BUILD 2026.10 ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.moon_player_logo),
                contentDescription = "Moon Player Cat Mascot",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(TerminalRaisedPanel)
                    .border(2.dp, accent, CircleShape)
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MOON PLAYER",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 2.sp
            )

            Text(
                text = "Minimalist Terminal Audio Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TerminalRaisedPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "> ARCHITECTURE & DESIGN",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Moon Player is an Android-native local audio player built with Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and Room Database. Designed with terminal-inspired cues, warm ivory accents, and dynamic album-art palette synchronization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryText,
                    lineHeight = 22.sp
                )

                HorizontalDivider(color = TerminalBorder, modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "> ZERO TELEMETRY // LOCAL-FIRST",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Moon Player is local-first. No account, no cloud servers, no ads, and no tracking. Your music, playlists, and listening history stay on your device. The only thing that ever goes online is the optional lyrics lookup: if you allow it, the song's title, artist, album and length are sent to LRCLIB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryText,
                    lineHeight = 22.sp
                )

                HorizontalDivider(color = TerminalBorder, modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "> TECHNICAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProfileTelemetryRow("Engine", "AndroidX Media3 ExoPlayer")
                ProfileTelemetryRow("Formats", com.example.data.repository.SUPPORTED_FORMAT_LABELS)
                ProfileTelemetryRow(
                    "Equalizer",
                    if (viewModel.isEqualizerSupported()) "Device equalizer active" else "Not supported on this device"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}


@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        letterSpacing = 1.2.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    // False greys the row out: a setting that needs another one turned on first.
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (enabled) PrimaryText else MutedText)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = moonSwitchColors(accentColor)
            )
        }
    }
}

/**
 * Switch colours for the dark UI. Unset unchecked colours fell back to the outline colour,
 * which is the same as the track, so an "off" switch showed no knob at all.
 */
@Composable
internal fun moonSwitchColors(accent: androidx.compose.ui.graphics.Color) = SwitchDefaults.colors(
    checkedThumbColor = com.example.ui.theme.buttonContentColorFor(accent),
    checkedTrackColor = accent,
    checkedBorderColor = accent,
    uncheckedThumbColor = MutedText,
    uncheckedTrackColor = TerminalBlack,
    uncheckedBorderColor = MutedText
)
