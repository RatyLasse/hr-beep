package com.x.hrbeep.monitoring

import com.x.hrbeep.data.HeartRateMonitorUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MonitoringController {
    private val _state = MutableStateFlow(MonitoringSessionState())
    val state: StateFlow<MonitoringSessionState> = _state.asStateFlow()

    fun update(transform: (MonitoringSessionState) -> MonitoringSessionState) {
        _state.update(transform)
    }

    fun beginMonitoring() {
        _state.update { it.beginMonitoring() }
    }

    fun endMonitoring() {
        _state.update { it.endMonitoring() }
    }

    fun updateMonitoringAverage(averageHr: Int) {
        _state.update { it.updateMonitoringAverage(averageHr) }
    }

    fun enableDistanceTracking() {
        _state.update { it.enableDistanceTracking() }
    }

    fun disableDistanceTracking() {
        _state.update { it.disableDistanceTracking() }
    }

    fun updateDistance(distanceMeters: Double) {
        _state.update { it.updateDistance(distanceMeters) }
    }

    fun onConnectionAttempt(
        deviceName: String,
        deviceAddress: String,
    ) {
        _state.update { it.onConnectionAttempt(deviceName, deviceAddress) }
    }

    fun onConnectionUpdate(
        deviceName: String,
        deviceAddress: String,
        update: HeartRateMonitorUpdate,
    ) {
        _state.update { it.onConnectionUpdate(deviceName, deviceAddress, update) }
    }

    fun onConnectionLost(errorMessage: String) {
        _state.update { it.onConnectionLost(errorMessage) }
    }

    fun clearSelectedDevice() {
        _state.update { it.clearSelectedDevice() }
    }

    fun reset() {
        _state.value = MonitoringSessionState()
    }
}
