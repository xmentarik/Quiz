package com.xmentarik.quiz.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibrates along with the game's sound cues. */
class GameHaptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    var enabled: Boolean = true

    fun play(cue: SoundCue) {
        if (!enabled) return
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        val pattern = Haptics.patternFor(cue) ?: return
        val effect = if (device.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(pattern.timings, NO_REPEAT)
        }
        device.vibrate(effect)
    }

    fun cancel() {
        vibrator?.cancel()
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}
