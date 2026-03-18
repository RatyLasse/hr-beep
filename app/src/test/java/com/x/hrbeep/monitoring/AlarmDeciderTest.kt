package com.x.hrbeep.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDeciderTest {
    @Test
    fun `beeps when first crossing above threshold`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 100L))
    }

    @Test
    fun `suppresses repeated beeps until the bpm-based interval passes`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 120, threshold = 100, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 120, threshold = 100, nowElapsedMs = 450L))
        assertTrue(decider.shouldBeep(currentHr = 120, threshold = 100, nowElapsedMs = 650L))
    }

    @Test
    fun `uses current bpm cadence even when it differs from the threshold`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 150, threshold = 145, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 150, threshold = 145, nowElapsedMs = 450L))
        assertTrue(decider.shouldBeep(currentHr = 150, threshold = 145, nowElapsedMs = 500L))
    }

    @Test
    fun `resets once heart rate drops back below threshold`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 149, threshold = 150, nowElapsedMs = 500L))
        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 600L))
    }

    @Test
    fun `beeps when heart rate drops below lower bound`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 49, threshold = 150, lowerBound = 50, nowElapsedMs = 100L))
    }

    @Test
    fun `does not beep when heart rate is within bounds`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertFalse(decider.shouldBeep(currentHr = 100, threshold = 150, lowerBound = 50, nowElapsedMs = 100L))
    }

    @Test
    fun `does not beep below lower bound when no lower bound set`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertFalse(decider.shouldBeep(currentHr = 30, threshold = 150, lowerBound = null, nowElapsedMs = 100L))
    }

    @Test
    fun `resets once heart rate rises back above lower bound`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)

        assertTrue(decider.shouldBeep(currentHr = 49, threshold = 150, lowerBound = 50, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 51, threshold = 150, lowerBound = 50, nowElapsedMs = 500L))
        assertTrue(decider.shouldBeep(currentHr = 49, threshold = 150, lowerBound = 50, nowElapsedMs = 600L))
    }
}
