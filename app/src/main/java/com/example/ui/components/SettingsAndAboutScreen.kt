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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
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
                    text = "Rescan MP3 Library",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Refreshes device MediaStore and scans for new or updated MP3 files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.scanAudio() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    )
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan Now", fontWeight = FontWeight.Bold)
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
                text = "Minimalist Terminal MP3 Engine",
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
                    text = "> ZERO TELEMETRY // 100% PRIVATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Moon Player is 100% local-first and offline. No account, no cloud servers, no ads, and no tracking. Your music, playlists, and listening history remain strictly on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryText,
                    lineHeight = 22.sp
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
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TerminalBlack,
                    checkedTrackColor = accentColor,
                    uncheckedTrackColor = TerminalBorder
                )
            )
        }
    }
}
