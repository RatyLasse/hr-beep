package com.x.heartbeep.monitoring

import com.x.heartbeep.data.HeartRateMonitorUpdate
import com.x.heartbeep.data.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringSessionStateTest {
    @Test
    fun `begin monitoring keeps an existing sensor connection`() {
        val monitoring = MonitoringSessionState(
            connectionState = ConnectionState.Connected,
            currentHr = 156,
            batteryLevelPercent = 82,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).beginMonitoring()

        assertTrue(monitoring.isMonitoring)
        assertEquals(ConnectionState.Monitoring, monitoring.connectionState)
        assertEquals(156, monitoring.currentHr)
        assertEquals(82, monitoring.batteryLevelPercent)
        assertNull(monitoring.distanceMeters)
        assertFalse(monitoring.isDistanceTrackingEnabled)
        assertEquals("Polar H10", monitoring.deviceName)
        assertEquals("AA:BB", monitoring.deviceAddress)
    }

    @Test
    fun `end monitoring keeps the connection alive`() {
        val stopped = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            currentHr = 156,
            averageHr = 148,
            distanceMeters = 3_620.0,
            paceSecondsPerKm = 310,
            isDistanceTrackingEnabled = true,
            batteryLevelPercent = 76,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).endMonitoring()

        assertFalse(stopped.isMonitoring)
        assertEquals(ConnectionState.Connected, stopped.connectionState)
        assertEquals(156, stopped.currentHr)
        assertEquals(148, stopped.averageHr)
        assertEquals(3_620.0, stopped.distanceMeters!!, 0.0)
        assertEquals(310, stopped.paceSecondsPerKm)
        assertTrue(stopped.isDistanceTrackingEnabled)
        assertEquals(76, stopped.batteryLevelPercent)
        assertEquals("Polar H10", stopped.deviceName)
        assertEquals("AA:BB", stopped.deviceAddress)
    }

    @Test
    fun `end monitoring freezes duration via finalDurationSeconds`() {
        val startTime = System.currentTimeMillis() - 5000L
        val stopped = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            monitoringStartTimeMs = startTime,
        ).endMonitoring()

        assertFalse(stopped.isMonitoring)
        assertEquals(startTime, stopped.monitoringStartTimeMs)
        assertTrue(stopped.finalDurationSeconds!! >= 5L)
    }

    @Test
    fun `connection updates keep monitoring state active`() {
        val updated = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Connecting,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).onConnectionUpdate(
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
            update = HeartRateMonitorUpdate(
                heartRateSample = HeartRateSample(
                    bpm = 151,
                    rrIntervalsMs = emptyList(),
                    contactDetected = true,
                    receivedAtElapsedMs = 1L,
                ),
                batteryLevelPercent = 68,
            ),
        )

        assertTrue(updated.isMonitoring)
        assertEquals(ConnectionState.Monitoring, updated.connectionState)
        assertEquals(151, updated.currentHr)
        assertEquals(68, updated.batteryLevelPercent)
    }

    @Test
    fun `connection loss keeps monitoring active until manually stopped`() {
        val disconnected = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            currentHr = 151,
            averageHr = 144,
            batteryLevelPercent = 68,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).onConnectionLost("Heart-rate strap disconnected.")

        assertTrue(disconnected.isMonitoring)
        assertEquals(ConnectionState.Disconnected, disconnected.connectionState)
        assertEquals("Heart-rate strap disconnected.", disconnected.errorMessage)
        assertNull(disconnected.currentHr)
        assertEquals(144, disconnected.averageHr)
    }

    @Test
    fun `begin monitoring clears previous session distance and pace`() {
        val restarted = MonitoringSessionState(
            averageHr = 148,
            distanceMeters = 4_280.0,
            paceSecondsPerKm = 295,
            isDistanceTrackingEnabled = true,
            connectionState = ConnectionState.Connected,
        ).beginMonitoring()

        assertNull(restarted.averageHr)
        assertNull(restarted.distanceMeters)
        assertNull(restarted.paceSecondsPerKm)
        assertFalse(restarted.isDistanceTrackingEnabled)
    }

    @Test
    fun `clear selected device resets distance and pace state`() {
        val cleared = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            distanceMeters = 2_145.0,
            paceSecondsPerKm = 312,
            isDistanceTrackingEnabled = true,
            averageHr = 141,
            deviceName = "Polar H10",
            deviceAddress = "AA:BB",
        ).clearSelectedDevice()

        assertFalse(cleared.isMonitoring)
        assertEquals(ConnectionState.Idle, cleared.connectionState)
        assertNull(cleared.distanceMeters)
        assertNull(cleared.paceSecondsPerKm)
        assertFalse(cleared.isDistanceTrackingEnabled)
        assertNull(cleared.averageHr)
    }

    @Test
    fun `updateDistance stores pace alongside distance`() {
        val state = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
        ).updateDistance(distanceMeters = 2_000.0, paceSecondsPerKm = 330)

        assertEquals(2_000.0, state.distanceMeters!!, 0.0)
        assertEquals(330, state.paceSecondsPerKm)
        assertTrue(state.isDistanceTrackingEnabled)
    }

    @Test
    fun `updateDistance with null pace clears previous pace`() {
        val state = MonitoringSessionState(
            isMonitoring = true,
            connectionState = ConnectionState.Monitoring,
            distanceMeters = 50.0,
            paceSecondsPerKm = 300,
        ).updateDistance(distanceMeters = 80.0, paceSecondsPerKm = null)

        assertEquals(80.0, state.distanceMeters!!, 0.0)
        assertNull(state.paceSecondsPerKm)
    }
}
