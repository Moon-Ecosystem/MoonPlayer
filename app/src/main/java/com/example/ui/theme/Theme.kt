package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAccentColor = staticCompositionLocalOf { WarmIvory }

@Composable
fun MoonPlayerTheme(
    accentColor: Color = WarmIvory,
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = accentColor,
        onPrimary = TerminalBlack,
        primaryContainer = TerminalRaisedPanel,
        onPrimaryContainer = accentColor,
        secondary = accentColor.copy(alpha = 0.85f),
        onSecondary = TerminalBlack,
        secondaryContainer = TerminalSurfaceElevated,
        onSecondaryContainer = PrimaryText,
        tertiary = TerminalGreen,
        onTertiary = TerminalBlack,
        background = TerminalBlack,
        onBackground = PrimaryText,
        surface = TerminalBlack,
        onSurface = PrimaryText,
        surfaceVariant = TerminalRaisedPanel,
        onSurfaceVariant = SecondaryText,
        surfaceContainer = TerminalRaisedPanel,
        surfaceContainerHigh = TerminalSurfaceElevated,
        surfaceContainerHighest = TerminalSurfaceHighlight,
        outline = TerminalBorder,
        outlineVariant = TerminalBorder.copy(alpha = 0.5f),
        error = Color(0xFFE05D52),
        onError = TerminalBlack
    )

    CompositionLocalProvider(LocalAccentColor provides accentColor) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
