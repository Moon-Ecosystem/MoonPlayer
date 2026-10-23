package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.service.EqualizerHelper
import com.example.viewmodel.MainViewModel

@Composable
fun EqualizerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val accent = LocalAccentColor.current

    val presets = EqualizerHelper.PRESETS
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    var selectedPreset by remember { mutableStateOf(settings.equalizerPreset) }
    var bassStrength by remember { mutableFloatStateOf(settings.bassBoostStrength.toFloat()) }
    // Slider values are in dB; the equalizer works in millibels (100 mB = 1 dB).
    var bandLevels by remember {
        mutableStateOf(FloatArray(5) { viewModel.equalizerBandLevels().getOrElse(it) { 0 } / 100f })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the platform default so the band sliders have room to be precise.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HARDWARE EQUALIZER", color = PrimaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.equalizerEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                    colors = moonSwitchColors(accent)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "[ ANDROID AUDIOFX DSP ENGINE ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Horizontal Row
                Text("PRESETS", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(presets) { preset ->
                        val isSelected = selectedPreset.equals(preset, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) accent else TerminalRaisedPanel,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isSelected) accent else TerminalBorder),
                            modifier = Modifier.clickable {
                                selectedPreset = preset
                                viewModel.setEqualizerPreset(preset)
                                // Move the bars to the preset's curve
                                bandLevels = EqualizerHelper.presetGains(preset).map { it / 100f }.toFloatArray()
                            }
                        ) {
                            Text(
                                text = preset,
                                color = if (isSelected) com.example.ui.theme.buttonContentColorFor(accent) else PrimaryText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = TerminalBorder, modifier = Modifier.padding(vertical = 12.dp))

                // 5-Band Equalizer Sliders
                Text("FREQUENCY BANDS", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Spacer(modifier = Modifier.height(6.dp))

                bandLabels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(68.dp)
                        )
                        Slider(
                            value = bandLevels[index],
                            onValueChange = { newVal ->
                                val copy = bandLevels.clone()
                                copy[index] = newVal
                                bandLevels = copy
                                viewModel.setEqualizerBandLevel(index, (newVal * 100).toInt())
                            },
                            valueRange = -10f..10f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = TerminalBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%+ddB", bandLevels[index].toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bass Boost Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Boost",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = bassStrength,
                        onValueChange = {
                            bassStrength = it
                            viewModel.setBassBoostStrength(it.toInt())
                        },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = TerminalBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(bassStrength / 10).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "* Hardware effects applied when supported by device audio HAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                )
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = TerminalSurfaceElevated
    )
}

@Composable
fun SleepTimerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sleepSeconds by viewModel.sleepTimerRemaining.collectAsState()
    val isStopAtEnd by viewModel.isStopAtEndActive.collectAsState()
    val accent = LocalAccentColor.current

    var selectedMinutes by remember { mutableIntStateOf(30) }
    var stopAtEndOfTrack by remember { mutableStateOf(isStopAtEnd) }

    val presetMinutes = listOf(15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("SLEEP TIMER", color = PrimaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (sleepSeconds != null) {
                    val m = sleepSeconds!! / 60
                    val s = sleepSeconds!! % 60
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TerminalRaisedPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "CURRENT TIMER ACTIVE", style = MaterialTheme.typography.labelSmall, color = MutedText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                style = MaterialTheme.typography.headlineMedium,
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.cancelSleepTimer()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Timer", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Select sleep duration:", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presetMinutes) { min ->
                            val isSelected = selectedMinutes == min
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) accent else TerminalRaisedPanel,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isSelected) accent else TerminalBorder),
                                modifier = Modifier.clickable { selectedMinutes = min }
                            ) {
                                Text(
                                    text = "${min}m",
                                    color = if (isSelected) com.example.ui.theme.buttonContentColorFor(accent) else PrimaryText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stopAtEndOfTrack = !stopAtEndOfTrack }
                    ) {
                        Checkbox(
                            checked = stopAtEndOfTrack,
                            onCheckedChange = { stopAtEndOfTrack = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = accent,
                                checkmarkColor = com.example.ui.theme.buttonContentColorFor(accent),
                                uncheckedColor = TerminalBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stop at end of current track",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (sleepSeconds == null) {
                Button(
                    onClick = {
                        viewModel.startSleepTimer(selectedMinutes, stopAtEndOfTrack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = com.example.ui.theme.buttonContentColorFor(accent)
                    )
                ) {
                    Text("Start Timer", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder)
            ) {
                Text("Dismiss")
            }
        },
        containerColor = TerminalSurfaceElevated
    )
}
