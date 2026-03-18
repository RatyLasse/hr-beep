package com.x.hrbeep.monitoring

import com.x.hrbeep.data.HeartRateMonitorUpdate
import com.x.hrbeep.data.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class MonitoringSessionStateTest {
    @Test
    fun `end monitoring keeps the last session average visible`() {
        val stopped = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            currentHr = 156,
            averageHr = 149,
            batteryLevelPercent = 82,
            threshold = 150,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).endMonitoring()

        assertFalse(stopped.isMonitoring)
        assertEquals(ConnectionState.Idle, stopped.connectionState)
        assertEquals(149, stopped.averageHr)
        assertNull(stopped.currentHr)
        assertNull(stopped.threshold)
        assertNull(stopped.deviceName)
        assertNull(stopped.deviceAddress)
    }

    @Test
    fun `stop then preview keeps the previous monitoring average`() {
        val stopped = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            currentHr = 156,
            averageHr = 148,
            batteryLevelPercent = 76,
            threshold = 150,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).endMonitoring()

        val previewState = stopped.beginPreview(
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        )

        val connected = previewState.withPreviewUpdate(
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
            update = HeartRateMonitorUpdate(
                heartRateSample = HeartRateSample(
                    bpm = 154,
                    rrIntervalsMs = emptyList(),
                    contactDetected = true,
                    receivedAtElapsedMs = 1L,
                ),
                batteryLevelPercent = 75,
            ),
        )

        val cleared = connected.clearPreview()

        assertEquals(ConnectionState.Connected, connected.connectionState)
        assertEquals(154, connected.currentHr)
        assertEquals(148, connected.averageHr)
        assertEquals(75, connected.batteryLevelPercent)
        assertEquals(148, cleared.averageHr)
        assertNull(cleared.currentHr)
        assertNull(cleared.deviceAddress)
    }
}
