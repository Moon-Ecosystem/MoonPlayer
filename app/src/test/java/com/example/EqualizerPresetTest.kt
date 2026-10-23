package com.example

import com.example.service.EqualizerHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetTest {

    @Test
    fun `genre presets match Android's built-in equalizer curves`() {
        // AOSP EffectBundle.h EQNB_5BandSoftPresets, in dB -> millibels
        assertArrayEquals(intArrayOf(500, 300, -100, 300, 500), EqualizerHelper.presetGains("Rock"))
        assertArrayEquals(intArrayOf(-100, 200, 500, 100, -200), EqualizerHelper.presetGains("Pop"))
        assertArrayEquals(intArrayOf(400, 100, 900, 300, 0), EqualizerHelper.presetGains("Heavy Metal"))
    }

    @Test
    fun `every listed preset has five bands within the slider range`() {
        for (preset in EqualizerHelper.PRESETS) {
            val gains = EqualizerHelper.presetGains(preset)
            assertEquals(preset, 5, gains.size)
            // The dialog's sliders span -10..+10 dB
            assertTrue(preset, gains.all { it in -1000..1000 })
        }
    }

    @Test
    fun `unknown or legacy preset names fall back to flat`() {
        assertArrayEquals(IntArray(5), EqualizerHelper.presetGains("Electronic"))
    }
}
