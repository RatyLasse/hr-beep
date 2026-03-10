package com.x.hrbeep.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDeciderTest {
    @Test
    fun `beeps when first crossing above threshold`() {
        val decider = AlarmDecider(cooldownMs = 1_000L)

        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 100L))
    }

    @Test
    fun `suppresses repeated beeps inside cooldown`() {
        val decider = AlarmDecider(cooldownMs = 1_000L)

        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 152, threshold = 150, nowElapsedMs = 400L))
        assertTrue(decider.shouldBeep(currentHr = 153, threshold = 150, nowElapsedMs = 1_200L))
    }

    @Test
    fun `resets once heart rate drops back below threshold`() {
        val decider = AlarmDecider(cooldownMs = 1_000L)

        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 100L))
        assertFalse(decider.shouldBeep(currentHr = 149, threshold = 150, nowElapsedMs = 500L))
        assertTrue(decider.shouldBeep(currentHr = 151, threshold = 150, nowElapsedMs = 600L))
    }
}
