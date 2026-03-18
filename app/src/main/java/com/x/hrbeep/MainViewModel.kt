package com.x.hrbeep

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x.hrbeep.data.BleDeviceCandidate
import com.x.hrbeep.data.BleHeartRateRepository
import com.x.hrbeep.data.ThresholdRepository
import com.x.hrbeep.monitoring.MonitoringController
import com.x.hrbeep.monitoring.MonitoringService
import com.x.hrbeep.monitoring.MonitoringSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val thresholdInput: String = ThresholdRepository.DEFAULT_THRESHOLD_BPM.toString(),
    val persistedThreshold: Int = ThresholdRepository.DEFAULT_THRESHOLD_BPM,
    val soundIntensity: Int = ThresholdRepository.DEFAULT_SOUND_INTENSITY,
    val bluetoothEnabled: Boolean = false,
    val availableDevices: List<BleDeviceCandidate> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val isScanning: Boolean = false,
    val monitoringState: MonitoringSessionState = MonitoringSessionState(),
    val message: String? = null,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val container = (application as HrBeepApplication).appContainer
    private val bleHeartRateRepository: BleHeartRateRepository = container.bleHeartRateRepository
    private val thresholdRepository: ThresholdRepository = container.thresholdRepository
    private val monitoringController: MonitoringController = container.monitoringController

    private val _uiState = MutableStateFlow(
        MainUiState(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var previewJob: Job? = null
    private var previewDeviceAddress: String? = null
    private var autoScanTriggered = false
    private var wasMonitoring = false

    init {
        viewModelScope.launch {
            thresholdRepository.thresholdFlow.collect { threshold ->
                _uiState.update { state ->
                    state.copy(
                        persistedThreshold = threshold,
                        thresholdInput = threshold.toString(),
                    )
                }
            }
        }

        viewModelScope.launch {
            thresholdRepository.soundIntensityFlow.collect { intensity ->
                _uiState.update { state ->
                    state.copy(soundIntensity = intensity)
                }
            }
        }

        viewModelScope.launch {
            monitoringController.state.collect { monitoringState ->
                val monitoringChanged = monitoringState.isMonitoring != wasMonitoring
                wasMonitoring = monitoringState.isMonitoring
                _uiState.update { state ->
                    state.copy(monitoringState = monitoringState)
                }
                if (monitoringChanged) {
                    syncPreviewSubscription()
                }
            }
        }
    }

    fun refreshBluetoothState() {
        _uiState.update { state ->
            state.copy(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
        }
        syncPreviewSubscription()
    }

    fun onThresholdInputChanged(input: String) {
        val filtered = input.filter(Char::isDigit).take(3)
        _uiState.update { state -> state.copy(thresholdInput = filtered) }

        filtered.toIntOrNull()?.takeIf { it in 20..300 }?.let { parsed ->
            viewModelScope.launch {
                thresholdRepository.saveThreshold(parsed)
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { state -> state.copy(message = null) }
    }

    fun updateSoundIntensity(value: Float) {
        val intensity = value.toInt().coerceIn(0, 100)
        _uiState.update { state -> state.copy(soundIntensity = intensity) }
        viewModelScope.launch {
            thresholdRepository.saveSoundIntensity(intensity)
        }
    }

    fun selectDevice(address: String) {
        _uiState.update { state -> state.copy(selectedDeviceAddress = address) }
        syncPreviewSubscription()
    }

    fun scanForDevices() {
        if (!bleHeartRateRepository.isBluetoothEnabled()) {
            _uiState.update { state ->
                state.copy(
                    bluetoothEnabled = false,
                    message = "Turn Bluetooth on before scanning.",
                )
            }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    bluetoothEnabled = true,
                    isScanning = true,
                    message = null,
                )
            }

            runCatching {
                bleHeartRateRepository.scanPolarDevices().collect { devices ->
                    _uiState.update { state ->
                        val selectedAddress = state.selectedDeviceAddress
                        val nextSelection = when {
                            selectedAddress != null && devices.any { it.address == selectedAddress } -> selectedAddress
                            devices.isNotEmpty() -> devices.first().address
                            else -> null
                        }
                        state.copy(
                            availableDevices = devices,
                            selectedDeviceAddress = nextSelection,
                            message = null,
                        )
                    }
                    syncPreviewSubscription()
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        message = throwable.message ?: "Scanning failed.",
                    )
                }
            }

            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    message = if (state.availableDevices.isEmpty()) {
                        "No Polar devices found in this scan window."
                    } else {
                        state.message
                    },
                )
            }
        }
    }

    fun triggerAutoScanIfReady(hasAllPermissions: Boolean) {
        if (autoScanTriggered || !hasAllPermissions || !bleHeartRateRepository.isBluetoothEnabled()) {
            return
        }

        autoScanTriggered = true
        scanForDevices()
    }

    fun startMonitoring() {
        val currentState = _uiState.value
        val threshold = currentState.thresholdInput.toIntOrNull()
        val selectedDevice = currentState.availableDevices.firstOrNull {
            it.address == currentState.selectedDeviceAddress
        }

        when {
            !bleHeartRateRepository.isBluetoothEnabled() -> {
                _uiState.update { state -> state.copy(message = "Turn Bluetooth on before monitoring.") }
            }

            threshold == null || threshold !in 20..300 -> {
                _uiState.update { state -> state.copy(message = "Set a heart-rate limit between 20 and 300 bpm.") }
            }

            selectedDevice == null -> {
                _uiState.update { state -> state.copy(message = "Pick a Polar device before starting.") }
            }

            else -> {
                stopPreviewSubscription()
                val context = getApplication<Application>()
                val intent = MonitoringService.startIntent(
                    context = context,
                    deviceAddress = selectedDevice.address,
                    deviceName = selectedDevice.name,
                    threshold = threshold,
                    soundIntensity = currentState.soundIntensity,
                )
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun stopMonitoring() {
        val context = getApplication<Application>()
        context.startService(MonitoringService.stopIntent(context))
    }

    fun openBluetoothEnableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    override fun onCleared() {
        stopPreviewSubscription(clearSession = false)
        scanJob?.cancel()
        super.onCleared()
    }

    private fun syncPreviewSubscription() {
        val selectedDeviceAddress = _uiState.value.selectedDeviceAddress
        if (monitoringController.state.value.isMonitoring || !bleHeartRateRepository.isBluetoothEnabled()) {
            stopPreviewSubscription()
            if (!monitoringController.state.value.isMonitoring) {
                clearPreviewSessionState()
            }
            return
        }

        if (selectedDeviceAddress.isNullOrBlank()) {
            stopPreviewSubscription()
            clearPreviewSessionState()
            return
        }

        if (previewJob != null && previewDeviceAddress == selectedDeviceAddress) {
            return
        }

        stopPreviewSubscription()
        startPreviewSubscription(selectedDeviceAddress)
    }

    private fun startPreviewSubscription(deviceAddress: String) {
        val deviceName = selectedDeviceName(deviceAddress)
        previewDeviceAddress = deviceAddress
        monitoringController.update { state ->
            if (state.isMonitoring) {
                state
            } else {
                state.beginPreview(
                    deviceName = deviceName,
                    deviceAddress = deviceAddress,
                )
            }
        }

        previewJob = viewModelScope.launch {
            while (isActive && shouldKeepPreviewing(deviceAddress)) {
                var failureMessage: String? = null

                bleHeartRateRepository.observeHeartRateMonitor(deviceAddress)
                    .catch { throwable ->
                        failureMessage = throwable.message ?: "Heart-rate preview failed."
                    }
                    .collect { update ->
                        monitoringController.update { state ->
                            if (state.isMonitoring || state.deviceAddress != deviceAddress) {
                                state
                            } else {
                                state.withPreviewUpdate(
                                    deviceName = deviceName,
                                    deviceAddress = deviceAddress,
                                    update = update,
                                )
                            }
                        }
                    }

                if (!shouldKeepPreviewing(deviceAddress)) {
                    break
                }

                monitoringController.update { state ->
                    if (state.isMonitoring || state.deviceAddress != deviceAddress) {
                        state
                    } else {
                        state.withPreviewError(
                            failureMessage ?: "Heart-rate strap disconnected.",
                        )
                    }
                }

                delay(PREVIEW_RECONNECT_DELAY_MS)

                if (shouldKeepPreviewing(deviceAddress)) {
                    monitoringController.update { state ->
                        if (state.isMonitoring || state.deviceAddress != deviceAddress) {
                            state
                        } else {
                            state.beginPreview(
                                deviceName = deviceName,
                                deviceAddress = deviceAddress,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopPreviewSubscription(clearSession: Boolean = false) {
        previewJob?.cancel()
        previewJob = null
        previewDeviceAddress = null
        if (clearSession) {
            clearPreviewSessionState()
        }
    }

    private fun clearPreviewSessionState() {
        monitoringController.update { state ->
            if (state.isMonitoring) {
                state
            } else {
                state.clearPreview()
            }
        }
    }

    private fun shouldKeepPreviewing(deviceAddress: String): Boolean =
        !monitoringController.state.value.isMonitoring &&
            bleHeartRateRepository.isBluetoothEnabled() &&
            _uiState.value.selectedDeviceAddress == deviceAddress

    private fun selectedDeviceName(deviceAddress: String): String =
        _uiState.value.availableDevices
            .firstOrNull { it.address == deviceAddress }
            ?.name
            ?: deviceAddress

    companion object {
        private const val PREVIEW_RECONNECT_DELAY_MS = 1_000L
    }
}
