package com.x.hrbeep.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDeciderTest {
    @Test
    fun `returns above-upper trigger when heart rate exceeds threshold`() {
        val decider = AlarmDecider()

        assertEquals(
            AlarmTrigger.AboveUpperBound,
            decider.currentAlertTrigger(currentHr = 151, threshold = 150, lowerBound = 50),
        )
    }

    @Test
    fun `returns below-lower trigger when heart rate drops below lower bound`() {
        val decider = AlarmDecider()

        assertEquals(
            AlarmTrigger.BelowLowerBound,
            decider.currentAlertTrigger(currentHr = 49, threshold = 150, lowerBound = 50),
        )
    }

    @Test
    fun `returns no trigger when heart rate is within configured bounds`() {
        val decider = AlarmDecider()

        assertNull(decider.currentAlertTrigger(currentHr = 100, threshold = 150, lowerBound = 50))
    }

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
    fun `produces correct beep rate at 160bpm when polled at sub-interval frequency`() {
        val decider = AlarmDecider(minimumIntervalMs = 300L, maximumIntervalMs = 2_000L)
        // At 160 BPM the interval is 375 ms. Over 10 seconds: 10_000 / 375 ≈ 26 beeps.
        // Polling every 100 ms is fine-grained enough to hit each window.
        var beepCount = 0
        for (t in 0..10_000L step 100L) {
            if (decider.shouldBeep(currentHr = 160, threshold = 150, nowElapsedMs = t)) {
                beepCount++
            }
        }
        assertTrue("Expected ~26 beeps at 160 BPM but got $beepCount", beepCount in 25..28)
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
