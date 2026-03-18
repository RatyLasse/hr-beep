package com.x.hrbeep.monitoring

import com.x.hrbeep.data.HeartRateMonitorUpdate

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
    val distanceMeters: Double? = null,
    val isDistanceTrackingEnabled: Boolean = false,
    val batteryLevelPercent: Int? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val errorMessage: String? = null,
) {
    fun beginMonitoring(): MonitoringSessionState = resetSessionMetrics().copy(
        isMonitoring = true,
        errorMessage = null,
        connectionState = when (connectionState) {
            ConnectionState.Connected,
            ConnectionState.Monitoring,
            -> ConnectionState.Monitoring

            else -> connectionState
        },
    )

    fun updateMonitoringAverage(
        averageHr: Int,
    ): MonitoringSessionState = copy(
        averageHr = averageHr,
    )

    fun enableDistanceTracking(): MonitoringSessionState = copy(
        isDistanceTrackingEnabled = true,
        distanceMeters = distanceMeters ?: 0.0,
    )

    fun disableDistanceTracking(): MonitoringSessionState = copy(
        isDistanceTrackingEnabled = false,
    )

    fun updateDistance(
        distanceMeters: Double,
    ): MonitoringSessionState = copy(
        distanceMeters = distanceMeters.coerceAtLeast(0.0),
        isDistanceTrackingEnabled = true,
    )

    fun endMonitoring(): MonitoringSessionState = copy(
        isMonitoring = false,
        errorMessage = null,
        connectionState = when (connectionState) {
            ConnectionState.Monitoring -> ConnectionState.Connected
            else -> connectionState
        },
    )

    fun onConnectionAttempt(
        deviceName: String,
        deviceAddress: String,
    ): MonitoringSessionState = copy(
        connectionState = ConnectionState.Connecting,
        batteryLevelPercent = batteryLevelPercent.takeIf { this.deviceAddress == deviceAddress },
        currentHr = null,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        errorMessage = null,
    )

    fun onConnectionUpdate(
        deviceName: String,
        deviceAddress: String,
        update: HeartRateMonitorUpdate,
    ): MonitoringSessionState = copy(
        connectionState = if (update.heartRateSample != null) {
            if (isMonitoring) ConnectionState.Monitoring else ConnectionState.Connected
        } else {
            connectionState
        },
        currentHr = update.heartRateSample?.bpm ?: currentHr,
        batteryLevelPercent = update.batteryLevelPercent ?: batteryLevelPercent,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        errorMessage = null,
    )

    fun onConnectionLost(errorMessage: String): MonitoringSessionState = copy(
        connectionState = ConnectionState.Disconnected,
        currentHr = null,
        errorMessage = errorMessage,
    )

    fun clearSelectedDevice(): MonitoringSessionState = resetSessionMetrics().copy(
        isMonitoring = false,
        connectionState = ConnectionState.Idle,
        currentHr = null,
        batteryLevelPercent = null,
        deviceName = null,
        deviceAddress = null,
        errorMessage = null,
    )

    private fun resetSessionMetrics(): MonitoringSessionState = copy(
        averageHr = null,
        distanceMeters = null,
        isDistanceTrackingEnabled = false,
    )
}
