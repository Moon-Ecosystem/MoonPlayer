package com.example

import com.example.data.repository.accentFromSwatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentColorTest {

    @Test
    fun `reds spread over many shades beat one large flat blue`() {
        // Like a cover with a blue backdrop and a red/pink subject: blue is the single biggest
        // cluster (what Palette calls dominant), but the red family covers more in total.
        val blueBackdrop = 0x1E4FD0 to 300
        val deepRed = 0xC0102A to 140
        val hairRed = 0xE0304A to 120
        val pink = 0xF06080 to 110
        val darkRed = 0x8A1020 to 60

        val accent = accentFromSwatches(listOf(blueBackdrop, deepRed, hairRed, pink, darkRed))

        // Red family wins on total weight; its strongest swatch (population x saturation) is deepRed.
        assertEquals(0xC0102A, accent)
    }

    @Test
    fun `a genuinely blue cover stays blue`() {
        val accent = accentFromSwatches(listOf(0x1E4FD0 to 500, 0x3A6AE8 to 200, 0xC0102A to 40))
        assertEquals(0x1E4FD0, accent)
    }

    @Test
    fun `grey covers fall back to the caller`() {
        assertNull(accentFromSwatches(listOf(0x808080 to 500, 0x101010 to 300, 0xF0F0F0 to 200)))
    }
}
