package com.xmentarik.quiz.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {
    @Test
    fun patternsAreValidWaveforms() {
        SoundCue.entries.mapNotNull { cue -> Haptics.patternFor(cue)?.let { cue to it } }.forEach { (cue, pattern) ->
            assertEquals(cue.name, pattern.timings.size, pattern.amplitudes.size)
            assertTrue(cue.name, pattern.timings.size >= 2)
            pattern.amplitudes.forEachIndexed { i, amplitude ->
                // Even slots are pauses, odd slots are pulses (required for devices without amplitude control).
                if (i % 2 == 0) assertEquals(cue.name, 0, amplitude) else assertTrue(cue.name, amplitude in 1..255)
            }
            assertTrue(cue.name, pattern.timings.all { it >= 0 })
            assertTrue(cue.name, pattern.timings.sum() <= 1_000)
        }
    }

    @Test
    fun bigMomentsVibrateLongerThanTaps() {
        val duration = { cue: SoundCue -> Haptics.patternFor(cue)!!.timings.sum() }
        assertTrue(duration(SoundCue.WRONG) > duration(SoundCue.SELECT))
        assertTrue(duration(SoundCue.WIN) > duration(SoundCue.CORRECT))
        assertNull(Haptics.patternFor(SoundCue.NEXT_QUESTION))
    }
}
