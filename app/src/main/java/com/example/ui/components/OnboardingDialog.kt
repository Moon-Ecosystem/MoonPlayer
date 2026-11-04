package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.repository.folderNameFromTreeUri
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.buttonContentColorFor
import com.example.viewmodel.MainViewModel

/** The first-start choice about LRCLIB: never, only on a tap, or every song automatically. */
private enum class LyricsLookupChoice(val title: String, val detail: String) {
    OFF("Off", "Lyrics only from your files. Nothing goes online"),
    ON_REQUEST("Only when I ask", "Tap Look up on LRCLIB under a song's lyrics"),
    AUTOMATIC("Automatically", "Each song is looked up once, the first time its lyrics open")
}

/**
 * First-run setup: asks for notification access (Android 13+), lets the user add one or more
 * music folders, then asks whether lyrics may come from LRCLIB. Shown until finished; the result
 * is persisted, so relaunching the app never brings it back.
 */
@Composable
fun OnboardingDialog(viewModel: MainViewModel) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val libraryFolders by viewModel.libraryFolders.collectAsState()
    val folderAccessError by viewModel.folderAccessError.collectAsState()

    val needsNotificationStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    var onFolderStep by rememberSaveable { mutableStateOf(!needsNotificationStep) }
    var onLyricsStep by rememberSaveable { mutableStateOf(false) }
    var lyricsChoice by rememberSaveable { mutableStateOf(LyricsLookupChoice.ON_REQUEST) }

    if (onLyricsStep) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Lyrics from LRCLIB?", color = PrimaryText, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "Moon Player shows lyrics from .lrc files and from your songs' own tags. LRCLIB, a free online lyrics " +
                            "library, can add synced lyrics, romaji and translations. A lookup sends only the song's title, " +
                            "artist, album and length. You can change this later in Settings › Lyrics.",
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LyricsLookupChoice.entries.forEach { choice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = lyricsChoice == choice, role = Role.RadioButton) { lyricsChoice = choice }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = lyricsChoice == choice,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = MutedText)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(choice.title, color = PrimaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(choice.detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishOnboarding(
                            lrclibEnabled = lyricsChoice != LyricsLookupChoice.OFF,
                            automatic = lyricsChoice == LyricsLookupChoice.AUTOMATIC
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColorFor(accent))
                ) { Text("Done", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { onLyricsStep = false }) { Text("Back", color = MutedText) }
            },
            containerColor = TerminalSurfaceElevated
        )
        return
    }

    // Granted or denied, move on; the media notification still works without it on Android 13+.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onFolderStep = true }
    val pickFolder = rememberFolderPicker { uri, flags -> viewModel.addFolder(uri, flags) }

    val primaryColors = ButtonDefaults.buttonColors(
        containerColor = accent,
        contentColor = buttonContentColorFor(accent)
    )

    AlertDialog(
        // Only the buttons close it, so a stray tap can't skip setup.
        onDismissRequest = {},
        title = {
            Text(
                text = if (onFolderStep) "Choose your music" else "Playback controls",
                color = PrimaryText,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = if (onFolderStep) {
                        "Pick the folders that hold your music (MP3, FLAC, M4A, OGG, Opus, WAV). Moon Player only reads these folders and their subfolders. You can add or remove folders later in Settings."
                    } else {
                        "Allow notifications to control playback from the notification shade and lock screen."
                    },
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (onFolderStep) {
                    Spacer(modifier = Modifier.height(12.dp))
                    libraryFolders.forEach { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folderNameFromTreeUri(folder),
                                color = PrimaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    folderAccessError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = pickFolder) {
                        Text(
                            if (libraryFolders.isEmpty()) "Add Folder" else "Add Another Folder",
                            color = PrimaryText
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (onFolderStep) {
                Button(onClick = { onLyricsStep = true }, colors = primaryColors) {
                    Text(if (libraryFolders.isEmpty()) "Skip" else "Next", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    colors = primaryColors
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!onFolderStep) {
                OutlinedButton(onClick = { onFolderStep = true }) {
                    Text("Not now", color = MutedText)
                }
            }
        },
        containerColor = TerminalSurfaceElevated
    )
}
