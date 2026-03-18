package com.x.hrbeep.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmToneTest {
    @Test
    fun `lower bound tone keeps the same shape with a lower frequency`() {
        val upper = AlarmTone.specFor(AlarmTrigger.AboveUpperBound)
        val lower = AlarmTone.specFor(AlarmTrigger.BelowLowerBound)

        assertTrue(lower.frequencyHz < upper.frequencyHz)
        assertEquals(upper.durationMs, lower.durationMs)
        assertEquals(upper.tailSilenceMs, lower.tailSilenceMs)
        assertEquals(upper.sampleRateHz, lower.sampleRateHz)
        assertEquals(upper.attackMs, lower.attackMs)
        assertEquals(upper.releaseMs, lower.releaseMs)
        assertEquals(upper.amplitude, lower.amplitude, 0.0)
    }

    @Test
    fun `builds audible pcm samples for configured tone`() {
        val spec = AlarmTone.specFor(AlarmTrigger.AboveUpperBound)

        val samples = AlarmTone.buildSamples(spec)

        assertEquals((spec.sampleRateHz * spec.durationMs) / 1_000, samples.size)
        assertTrue(samples.any { it.toInt() != 0 })
    }

    @Test
    fun `ends each tone with explicit silence to avoid playback clicks`() {
        val spec = AlarmTone.specFor(AlarmTrigger.AboveUpperBound)

        val samples = AlarmTone.buildSamples(spec)
        val tailSilenceSamples = (spec.sampleRateHz * spec.tailSilenceMs) / 1_000

        assertTrue(samples.drop(samples.size - tailSilenceSamples).all { it == 0.toShort() })
    }

    @Test
    fun `tone stays short enough to remain distinct at 220 bpm`() {
        val spec = AlarmTone.specFor(AlarmTrigger.AboveUpperBound)

        assertTrue(spec.durationMs < (60_000 / 220))
    }
}
