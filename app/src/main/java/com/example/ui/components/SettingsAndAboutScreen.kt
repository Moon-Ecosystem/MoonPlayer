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

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: THEME & ACCENT
        SettingsSectionHeader(title = "ACCENT COLOR THEME")

        Spacer(modifier = Modifier.height(8.dp))

        val accentOptions = listOf(
            Triple(AccentMode.DYNAMIC_COVER, "Dynamic (From Album Art)", accent),
            Triple(AccentMode.WARM_IVORY, "Warm Ivory (Default)", WarmIvory),
            Triple(AccentMode.TERMINAL_GREEN, "Terminal Emerald", TerminalGreen),
            Triple(AccentMode.AMBER_GLOW, "Amber Glow", TerminalAmber),
            Triple(AccentMode.CYAN_PULSE, "Celestial Cyan", CelestialCyan)
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

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: PLAYBACK CONTROLS
        SettingsSectionHeader(title = "PLAYBACK BEHAVIOR")

        Spacer(modifier = Modifier.height(8.dp))

        // Resume on launch
        SettingsToggleRow(
            title = "Resume on App Launch",
            subtitle = "Restore last active track and queue on launch",
            checked = settings.resumeOnLaunch,
            onCheckedChange = { viewModel.setResumeOnLaunch(it) },
            accentColor = accent
        )

        // Pause on unplug
        SettingsToggleRow(
            title = "Pause on Disconnect",
            subtitle = "Automatically pause playback when headphones are unplugged",
            checked = settings.pauseOnHeadphoneDisconnect,
            onCheckedChange = { viewModel.setPauseOnHeadphoneDisconnect(it) },
            accentColor = accent
        )

        // Show remaining time
        SettingsToggleRow(
            title = "Remaining Time Display",
            subtitle = "Show '-MM:SS' time remaining rather than total duration on seek bar",
            checked = settings.showRemainingTime,
            onCheckedChange = { viewModel.setShowRemainingTime(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(4.dp))

        SettingsToggleRow(
            title = "Online Lyrics",
            subtitle = "Look up lyrics on LRCLIB. Sends only the song's title, artist, album and length; results are saved on the phone",
            checked = settings.onlineLyrics == true,
            onCheckedChange = { viewModel.setOnlineLyrics(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(4.dp))

        SettingsToggleRow(
            title = "Prefer Romaji Lyrics",
            subtitle = "For Japanese and other non-Latin songs, show romaji when it's available. Each song can still be switched in its lyrics",
            checked = viewModel.effectivePreferRomaji(settings),
            onCheckedChange = { viewModel.setPreferRomaji(it) },
            accentColor = accent
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: LIBRARY RE-SCAN
        SettingsSectionHeader(title = "MEDIA DISCOVERY")

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
                    text = "Only music files (MP3, FLAC, M4A, AAC, OGG, Opus, WAV) in these folders and their subfolders are scanned. Access is kept across restarts while Android keeps the grant.",
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

        Spacer(modifier = Modifier.height(24.dp))
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
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TerminalRaisedPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
