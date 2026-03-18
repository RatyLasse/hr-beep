package com.x.hrbeep.monitoring

enum class ConnectionState {
    Idle,
    Connecting,
    Connected,
    Monitoring,
    Disconnected,
    Error,
}

data class MonitoringSessionState(
    val isMonitoring: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val currentHr: Int? = null,
    val averageHr: Int? = null,
    val batteryLevelPercent: Int? = null,
    val threshold: Int? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val errorMessage: String? = null,
)
