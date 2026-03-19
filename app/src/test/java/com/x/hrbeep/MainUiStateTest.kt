package com.x.hrbeep

import com.x.hrbeep.data.BleDeviceCandidate
import com.x.hrbeep.monitoring.ConnectionState
import com.x.hrbeep.monitoring.MonitoringSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainUiStateTest {

    private val connectedState = MonitoringSessionState(
        connectionState = ConnectionState.Connected,
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceName = "Polar H10",
    )

    @Test
    fun `seedDeviceFromMonitoringState adds device when availableDevices is empty`() {
        val uiState = MainUiState()

        val result = uiState.seedDeviceFromMonitoringState(connectedState)

        assertEquals(1, result.availableDevices.size)
        assertEquals("AA:BB:CC:DD:EE:FF", result.availableDevices[0].address)
        assertEquals("Polar H10", result.availableDevices[0].name)
        assertEquals("AA:BB:CC:DD:EE:FF", result.selectedDeviceAddress)
    }

    @Test
    fun `seedDeviceFromMonitoringState does not duplicate device already in list`() {
        val existing = BleDeviceCandidate(address = "AA:BB:CC:DD:EE:FF", name = "Polar H10", rssi = -60)
        val uiState = MainUiState(availableDevices = listOf(existing), selectedDeviceAddress = existing.address)

        val result = uiState.seedDeviceFromMonitoringState(connectedState)

        assertEquals(1, result.availableDevices.size)
        assertEquals(-60, result.availableDevices[0].rssi) // original entry preserved
    }

    @Test
    fun `seedDeviceFromMonitoringState does not overwrite existing selectedDeviceAddress`() {
        val uiState = MainUiState(selectedDeviceAddress = "11:22:33:44:55:66")

        val result = uiState.seedDeviceFromMonitoringState(connectedState)

        assertEquals("11:22:33:44:55:66", result.selectedDeviceAddress)
    }

    @Test
    fun `seedDeviceFromMonitoringState is a no-op when monitoring state has no device`() {
        val uiState = MainUiState()

        val result = uiState.seedDeviceFromMonitoringState(MonitoringSessionState())

        assertEquals(0, result.availableDevices.size)
        assertNull(result.selectedDeviceAddress)
    }
}
