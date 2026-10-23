package com.example.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

class EqualizerHelper {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    var isSupported: Boolean = false
        private set

    fun init(audioSessionId: Int) {
        if (audioSessionId == 0) return
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
            isSupported = true
        } catch (e: Exception) {
            Log.w("EqualizerHelper", "Hardware Equalizer not supported on this device/emulator: ${e.message}")
            isSupported = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            Log.w("EqualizerHelper", "Error toggling equalizer: ${e.message}")
        }
    }

    fun getBandLevels(): IntArray {
        val eq = equalizer ?: return intArrayOf(0, 0, 0, 0, 0)
        return try {
            val numBands = eq.numberOfBands.toInt()
            IntArray(numBands) { band -> eq.getBandLevel(band.toShort()).toInt() }
        } catch (e: Exception) {
            intArrayOf(0, 0, 0, 0, 0)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMilliBels: Int) {
        try {
            val eq = equalizer ?: return
            val min = eq.bandLevelRange[0]
            val max = eq.bandLevelRange[1]
            val clamped = levelMilliBels.coerceIn(min.toInt(), max.toInt()).toShort()
            eq.setBandLevel(bandIndex.toShort(), clamped)
        } catch (e: Exception) {
            Log.w("EqualizerHelper", "Error setting band level: ${e.message}")
        }
    }

    fun setBassBoost(strength: Int) {
        try {
            val bb = bassBoost ?: return
            bb.setStrength(strength.coerceIn(0, 1000).toShort())
        } catch (e: Exception) {
            Log.w("EqualizerHelper", "Error setting bass boost: ${e.message}")
        }
    }

    fun applyPreset(presetName: String) {
        presetGains(presetName).forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
    }

    companion object {
        /** Preset names in display order. */
        val PRESETS = listOf(
            "Flat", "Bass Boost", "Vocal", "Rock", "Pop", "Jazz",
            "Classical", "Dance", "Hip Hop", "Heavy Metal", "Folk"
        )

        /**
         * Per-band gains in millibels (100 mB = 1 dB) for the 60/230/910/3600/14000 Hz bands;
         * the equalizer UI shows the same values. Named genre curves are Android's own
         * (AOSP EffectBundle.h, EQNB_5BandSoftPresets, the table the platform EQ applies).
         * Bass Boost and Vocal aren't in AOSP: a low shelf, and a mid/presence lift with low cut.
         */
        fun presetGains(presetName: String): IntArray = when (presetName.lowercase()) {
            "bass boost" -> intArrayOf(600, 400, 100, 0, 0)
            "vocal" -> intArrayOf(-200, 0, 400, 300, 0)
            "rock" -> intArrayOf(500, 300, -100, 300, 500)
            "pop" -> intArrayOf(-100, 200, 500, 100, -200)
            "jazz" -> intArrayOf(400, 200, -200, 200, 500)
            "classical" -> intArrayOf(500, 300, -200, 400, 400)
            "dance" -> intArrayOf(600, 0, 200, 400, 100)
            "hip hop" -> intArrayOf(500, 300, 0, 100, 300)
            "heavy metal" -> intArrayOf(400, 100, 900, 300, 0)
            "folk" -> intArrayOf(300, 0, 0, 200, -100)
            else -> intArrayOf(0, 0, 0, 0, 0) // Flat
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        isSupported = false
    }
}
