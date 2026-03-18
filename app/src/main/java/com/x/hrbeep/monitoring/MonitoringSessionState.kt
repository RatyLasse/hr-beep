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
    val batteryLevelPercent: Int? = null,
    val threshold: Int? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val errorMessage: String? = null,
) {
    fun beginMonitoring(
        deviceName: String,
        deviceAddress: String,
        threshold: Int,
    ): MonitoringSessionState = copy(
        isMonitoring = true,
        connectionState = ConnectionState.Connecting,
        currentHr = null,
        averageHr = null,
        batteryLevelPercent = null,
        threshold = threshold,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        errorMessage = null,
    )

    fun withMonitoringBatteryLevel(batteryLevelPercent: Int?): MonitoringSessionState = copy(
        isMonitoring = true,
        batteryLevelPercent = batteryLevelPercent ?: this.batteryLevelPercent,
        errorMessage = null,
    )

    fun withMonitoringSample(
        currentHr: Int,
        averageHr: Int,
        threshold: Int,
        batteryLevelPercent: Int?,
    ): MonitoringSessionState = copy(
        isMonitoring = true,
        connectionState = ConnectionState.Monitoring,
        currentHr = currentHr,
        averageHr = averageHr,
        batteryLevelPercent = batteryLevelPercent ?: this.batteryLevelPercent,
        threshold = threshold,
        errorMessage = null,
    )

    fun endMonitoring(errorMessage: String? = null): MonitoringSessionState =
        if (errorMessage == null) {
            copy(
                isMonitoring = false,
                connectionState = ConnectionState.Idle,
                currentHr = null,
                threshold = null,
                deviceName = null,
                deviceAddress = null,
                errorMessage = null,
            )
        } else {
            copy(
                isMonitoring = false,
                connectionState = ConnectionState.Error,
                errorMessage = errorMessage,
            )
        }

    fun beginPreview(
        deviceName: String,
        deviceAddress: String,
    ): MonitoringSessionState = copy(
        connectionState = ConnectionState.Connecting,
        currentHr = null,
        batteryLevelPercent = batteryLevelPercent.takeIf { this.deviceAddress == deviceAddress },
        threshold = null,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        errorMessage = null,
    )

    fun withPreviewUpdate(
        deviceName: String,
        deviceAddress: String,
        update: HeartRateMonitorUpdate,
    ): MonitoringSessionState = copy(
        connectionState = if (update.heartRateSample != null) {
            ConnectionState.Connected
        } else {
            connectionState
        },
        currentHr = update.heartRateSample?.bpm ?: currentHr,
        batteryLevelPercent = update.batteryLevelPercent ?: batteryLevelPercent,
        threshold = null,
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        errorMessage = null,
    )

    fun withPreviewError(errorMessage: String): MonitoringSessionState = copy(
        connectionState = ConnectionState.Error,
        currentHr = null,
        batteryLevelPercent = null,
        threshold = null,
        errorMessage = errorMessage,
    )

    fun clearPreview(): MonitoringSessionState = copy(
        connectionState = ConnectionState.Idle,
        currentHr = null,
        batteryLevelPercent = null,
        threshold = null,
        deviceName = null,
        deviceAddress = null,
        errorMessage = null,
    )
}
