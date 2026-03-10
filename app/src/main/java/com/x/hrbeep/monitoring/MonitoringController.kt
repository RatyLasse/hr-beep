package com.x.hrbeep.monitoring

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

    fun reset() {
        _state.value = MonitoringSessionState()
    }
}
