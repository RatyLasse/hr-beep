package com.x.hrbeep.monitoring

import com.x.hrbeep.data.HeartRateMonitorUpdate
import com.x.hrbeep.data.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorConnectionAudioAlertTrackerTest {
    @Test
    fun `emits connected only once when live heart rate starts`() {
        val tracker = SensorConnectionAudioAlertTracker().apply { onMonitoringStarted() }
        val sampleUpdate = HeartRateMonitorUpdate(
            heartRateSample = HeartRateSample(
                bpm = 153,
                rrIntervalsMs = emptyList(),
                contactDetected = true,
                receivedAtElapsedMs = 1L,
            ),
        )

        assertEquals(SessionAudioAlert.SensorConnected, tracker.onMonitorUpdate(sampleUpdate))
        assertNull(tracker.onMonitorUpdate(sampleUpdate))
        assertTrue(tracker.hasSeenLiveHeartRate)
    }

    @Test
    fun `does not emit connected for battery only updates`() {
        val tracker = SensorConnectionAudioAlertTracker().apply { onMonitoringStarted() }

        assertNull(tracker.onMonitorUpdate(HeartRateMonitorUpdate(batteryLevelPercent = 77)))
        assertNull(tracker.onMonitoringFailure())
    }

    @Test
    fun `emits disconnected only after live heart rate was seen`() {
        val tracker = SensorConnectionAudioAlertTracker().apply { onMonitoringStarted() }

        tracker.onMonitorUpdate(
            HeartRateMonitorUpdate(
                heartRateSample = HeartRateSample(
                    bpm = 147,
                    rrIntervalsMs = emptyList(),
                    contactDetected = true,
                    receivedAtElapsedMs = 1L,
                ),
            ),
        )

        assertEquals(SessionAudioAlert.SensorDisconnected, tracker.onMonitoringFailure())
    }
}
