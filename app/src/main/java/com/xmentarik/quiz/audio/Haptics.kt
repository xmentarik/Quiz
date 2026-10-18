package com.xmentarik.quiz.audio

/**
 * A vibration waveform. [timings] alternate off/on durations in ms, starting with "off";
 * [amplitudes] are 0 for the pauses and 1..255 for the pulses.
 */
class HapticPattern(val timings: LongArray, val amplitudes: IntArray)

object Haptics {
    fun patternFor(cue: SoundCue): HapticPattern? = when (cue) {
        SoundCue.SELECT -> pulses(20L to 90)
        SoundCue.TICK -> pulses(15L to 60)
        SoundCue.LIFELINE -> pulses(30L to 120)
        SoundCue.GAME_START -> pulses(80L to 200)
        SoundCue.WALK_AWAY -> pulses(60L to 150)
        // A heartbeat that speeds up and grows while the answer is locked in.
        SoundCue.FINAL_ANSWER -> pulses(40L to 110, 260L to 0, 40L to 160, 180L to 0, 60L to 230)
        SoundCue.CORRECT -> pulses(40L to 180, 80L to 0, 40L to 180)
        SoundCue.MILESTONE -> pulses(50L to 200, 70L to 0, 50L to 200, 70L to 0, 140L to 255)
        SoundCue.WRONG -> pulses(400L to 255)
        SoundCue.TIME_UP -> pulses(250L to 230, 100L to 0, 250L to 230)
        SoundCue.WIN -> pulses(100L to 255, 100L to 0, 100L to 255, 100L to 0, 350L to 255)
        SoundCue.NEXT_QUESTION -> null
    }

    /** Segments are (duration ms, amplitude); amplitude 0 means a pause. */
    private fun pulses(vararg segments: Pair<Long, Int>): HapticPattern = HapticPattern(
        timings = longArrayOf(0L) + segments.map { it.first },
        amplitudes = intArrayOf(0) + segments.map { it.second },
    )
}
