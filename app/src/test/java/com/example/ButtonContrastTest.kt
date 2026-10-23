package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.buttonContentColorFor
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonContrastTest {
    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    @Test
    fun `light and mid-tone accents get black text`() {
        assertEquals(black, buttonContentColorFor(WarmIvory))
        // Grey-blue album-art accent: luminance ~0.3, which the old 0.45 cut-off gave white text
        assertEquals(black, buttonContentColorFor(Color(0xFF8E9BAA)))
        // Saturated mid blue
        assertEquals(black, buttonContentColorFor(Color(0xFF4F8EF0)))
    }

    @Test
    fun `dark accents get white text`() {
        assertEquals(white, buttonContentColorFor(Color(0xFF1B1F3A)))
        assertEquals(white, buttonContentColorFor(Color(0xFF7A1F1F)))
    }
}
