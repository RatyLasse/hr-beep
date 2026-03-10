package com.x.hrbeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    @Test
    fun `parses 8-bit bpm with rr interval`() {
        val payload = byteArrayOf(
            0x10,
            0x48,
            0x54,
            0x03,
        )

        val sample = HeartRateParser.parse(payload, timestampElapsedMs = 42L)

        assertEquals(72, sample.bpm)
        assertNull(sample.contactDetected)
        assertEquals(1, sample.rrIntervalsMs.size)
        assertEquals(832.03125f, sample.rrIntervalsMs.first(), 0.01f)
        assertEquals(42L, sample.receivedAtElapsedMs)
    }

    @Test
    fun `parses 16-bit bpm with contact flag`() {
        val payload = byteArrayOf(
            0x07,
            0x34,
            0x01,
        )

        val sample = HeartRateParser.parse(payload, timestampElapsedMs = 99L)

        assertEquals(308, sample.bpm)
        assertEquals(true, sample.contactDetected)
        assertEquals(99L, sample.receivedAtElapsedMs)
    }
}
