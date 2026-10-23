package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Moon Player AMOLED Pitch Black Color Tokens
val TerminalBlack = Color(0xFF000000) // Pure 100% AMOLED pitch black
val TerminalRaisedPanel = Color(0xFF0D0D0D) // Very subtle dark surface
val TerminalSurfaceElevated = Color(0xFF141414) // Elevated cards / sheets
val TerminalSurfaceHighlight = Color(0xFF1E1E1E) // Hover / press highlight

val WarmIvory = Color(0xFFE8D0AE)
val MutedText = Color(0xFF8E8E8E)
val SubduedText = Color(0xFF5E5750)
val TerminalBorder = Color(0xFF262626) // Sharp contrast border on pitch black
val PrimaryText = Color(0xFFFFFFFF) // Crisp pure white text
val SecondaryText = Color(0xFFCCCCCC) // Clear readable secondary text

// Accent options
val TerminalGreen = Color(0xFF69B37E)
val TerminalAmber = Color(0xFFE5A855)
val CelestialCyan = Color(0xFF70C4E8)
val NeonCoral = Color(0xFFE87A65)
val VioletGlow = Color(0xFFB88EE8)

/**
 * Black or white, whichever contrasts more with [backgroundColor] (WCAG contrast ratio).
 * The crossover sits near luminance 0.18, so mid-tone accents like a saturated blue get
 * black text; the old 0.45 cut-off gave them hard-to-read white text.
 */
fun buttonContentColorFor(backgroundColor: Color): Color {
    val l = backgroundColor.luminance()
    val contrastWithBlack = (l + 0.05f) / 0.05f
    val contrastWithWhite = 1.05f / (l + 0.05f)
    return if (contrastWithBlack >= contrastWithWhite) Color(0xFF000000) else Color(0xFFFFFFFF)
}

