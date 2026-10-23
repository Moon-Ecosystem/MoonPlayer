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
        val gains = when (presetName.lowercase()) {
            "bass boost" -> intArrayOf(600, 400, 0, 100, 200)
            "rock" -> intArrayOf(400, 200, -100, 200, 500)
            "pop" -> intArrayOf(-100, 200, 500, 200, -100)
            "jazz" -> intArrayOf(300, 200, -100, 200, 400)
            "electronic" -> intArrayOf(500, 300, 0, 100, 400)
            "vocal" -> intArrayOf(-200, 0, 500, 300, 0)
            else -> intArrayOf(0, 0, 0, 0, 0) // Flat
        }

        gains.forEachIndexed { index, level ->
            setBandLevel(index, level)
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
