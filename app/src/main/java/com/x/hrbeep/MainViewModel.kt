package com.x.hrbeep

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x.hrbeep.data.BleDeviceCandidate
import com.x.hrbeep.data.BleHeartRateRepository
import com.x.hrbeep.data.SessionHistoryRepository
import com.x.hrbeep.data.SessionRecord
import com.x.hrbeep.data.ThresholdRepository
import com.x.hrbeep.monitoring.HeartRateConnectionManager
import com.x.hrbeep.monitoring.MonitoringController
import com.x.hrbeep.monitoring.MonitoringService
import com.x.hrbeep.monitoring.MonitoringSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Re-seeds [MainUiState.availableDevices] and [MainUiState.selectedDeviceAddress] from the current
 * monitoring state when the ViewModel is recreated while the connection manager is still active.
 */
internal fun MainUiState.seedDeviceFromMonitoringState(monitoringState: MonitoringSessionState): MainUiState {
    val addr = monitoringState.deviceAddress ?: return this
    val name = monitoringState.deviceName ?: return this
    if (availableDevices.any { it.address == addr }) return this
    return copy(
        availableDevices = (availableDevices + BleDeviceCandidate(address = addr, name = name, rssi = 0))
            .sortedBy { it.name.lowercase() },
        selectedDeviceAddress = selectedDeviceAddress ?: addr,
    )
}

data class MainUiState(
    val thresholdInput: String = ThresholdRepository.DEFAULT_THRESHOLD_BPM.toString(),
    val persistedThreshold: Int? = ThresholdRepository.DEFAULT_THRESHOLD_BPM,
    val lowerBoundInput: String = "",
    val persistedLowerBound: Int? = null,
    val soundIntensity: Int = ThresholdRepository.DEFAULT_SOUND_INTENSITY,
    val bluetoothEnabled: Boolean = false,
    val availableDevices: List<BleDeviceCandidate> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val lastConnectedAddress: String? = null,
    val isScanning: Boolean = false,
    val monitoringState: MonitoringSessionState = MonitoringSessionState(),
    val sessionHistory: List<SessionRecord> = emptyList(),
    val pendingDeleteId: Long? = null,
    val message: String? = null,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val container = (application as HrBeepApplication).appContainer
    private val bleHeartRateRepository: BleHeartRateRepository = container.bleHeartRateRepository
    private val thresholdRepository: ThresholdRepository = container.thresholdRepository
    private val sessionHistoryRepository: SessionHistoryRepository = container.sessionHistoryRepository
    private val monitoringController: MonitoringController = container.monitoringController
    private val heartRateConnectionManager: HeartRateConnectionManager =
        container.heartRateConnectionManager

    private val _uiState = MutableStateFlow(
        MainUiState(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var autoScanTriggered = false

    init {
        viewModelScope.launch {
            thresholdRepository.thresholdFlow.collect { threshold ->
                _uiState.update { state ->
                    state.copy(
                        persistedThreshold = threshold,
                        thresholdInput = threshold?.toString() ?: "",
                    )
                }
            }
        }

        viewModelScope.launch {
            thresholdRepository.lastConnectedAddressFlow.collect { address ->
                _uiState.update { state -> state.copy(lastConnectedAddress = address) }
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
            thresholdRepository.lowerBoundFlow.collect { lowerBound ->
                _uiState.update { state ->
                    state.copy(
                        persistedLowerBound = lowerBound,
                        lowerBoundInput = lowerBound?.toString() ?: "",
                    )
                }
            }
        }

        viewModelScope.launch {
            monitoringController.state.collect { monitoringState ->
                _uiState.update { state ->
                    state.copy(monitoringState = monitoringState)
                        .seedDeviceFromMonitoringState(monitoringState)
                }
                val addr = monitoringState.deviceAddress
                if (addr != null && (monitoringState.connectionState == com.x.hrbeep.monitoring.ConnectionState.Connected ||
                        monitoringState.connectionState == com.x.hrbeep.monitoring.ConnectionState.Monitoring)) {
                    thresholdRepository.saveLastConnectedAddress(addr)
                }
            }
        }

        viewModelScope.launch {
            sessionHistoryRepository.sessions.collect { sessions ->
                _uiState.update { state -> state.copy(sessionHistory = sessions) }
            }
        }
    }

    fun refreshBluetoothState() {
        _uiState.update { state ->
            state.copy(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
        }
        syncObservedDevice()
    }

    fun onThresholdInputChanged(input: String) {
        val filtered = input.filter(Char::isDigit).take(3)
        _uiState.update { state -> state.copy(thresholdInput = filtered) }

        if (filtered.isEmpty()) {
            viewModelScope.launch { thresholdRepository.saveThreshold(null) }
        } else {
            filtered.toIntOrNull()?.takeIf { it in 20..300 }?.let { parsed ->
                viewModelScope.launch { thresholdRepository.saveThreshold(parsed) }
            }
        }
    }

    fun onLowerBoundInputChanged(input: String) {
        val filtered = input.filter(Char::isDigit).take(3)
        _uiState.update { state -> state.copy(lowerBoundInput = filtered) }

        if (filtered.isEmpty()) {
            viewModelScope.launch { thresholdRepository.saveLowerBound(null) }
        } else {
            filtered.toIntOrNull()?.takeIf { it in 20..300 }?.let { parsed ->
                viewModelScope.launch { thresholdRepository.saveLowerBound(parsed) }
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
        syncObservedDevice()
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
                bleHeartRateRepository.scanHeartRateDevices().collect { devices ->
                    _uiState.update { state ->
                        val selectedAddress = state.selectedDeviceAddress
                        val nextSelection = when {
                            // Keep existing selection if it's still in the list
                            selectedAddress != null && devices.any { it.address == selectedAddress } -> selectedAddress
                            // Auto-select a single result
                            devices.size == 1 -> devices.first().address
                            // Auto-select a previously-connected device even among multiple results
                            state.lastConnectedAddress != null &&
                                devices.any { it.address == state.lastConnectedAddress } ->
                                state.lastConnectedAddress
                            // Multiple unknown devices found — let the user choose
                            else -> null
                        }
                        state.copy(
                            availableDevices = devices,
                            selectedDeviceAddress = nextSelection,
                            message = null,
                        )
                    }
                    syncObservedDevice()
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
        val selectedDeviceAddress = currentState.selectedDeviceAddress

        when {
            !bleHeartRateRepository.isBluetoothEnabled() -> {
                _uiState.update { state -> state.copy(message = "Turn Bluetooth on before monitoring.") }
            }

            selectedDeviceAddress.isNullOrBlank() -> {
                _uiState.update { state -> state.copy(message = "Select a device before starting.") }
            }

            else -> {
                val context = getApplication<Application>()
                val intent = MonitoringService.startIntent(
                    context = context,
                    deviceAddress = selectedDeviceAddress,
                    deviceName = selectedDeviceName(selectedDeviceAddress),
                    threshold = currentState.persistedThreshold,
                    lowerBound = currentState.persistedLowerBound,
                    soundIntensity = currentState.soundIntensity,
                )
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun requestDeleteSession(id: Long) {
        // Commit any previous pending delete before starting a new one
        val existing = _uiState.value.pendingDeleteId
        if (existing != null) {
            viewModelScope.launch { sessionHistoryRepository.deleteSession(existing) }
        }
        _uiState.update { state -> state.copy(pendingDeleteId = id) }
    }

    fun commitDelete() {
        val id = _uiState.value.pendingDeleteId ?: return
        _uiState.update { state -> state.copy(pendingDeleteId = null) }
        viewModelScope.launch { sessionHistoryRepository.deleteSession(id) }
    }

    fun undoDelete() {
        _uiState.update { state -> state.copy(pendingDeleteId = null) }
    }

    fun stopMonitoring() {
        val context = getApplication<Application>()
        context.startService(MonitoringService.stopIntent(context))
    }

    fun openBluetoothEnableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    private fun syncObservedDevice() {
        val selectedDeviceAddress = _uiState.value.selectedDeviceAddress
        val isMonitoring = monitoringController.state.value.isMonitoring
        if (!bleHeartRateRepository.isBluetoothEnabled()) {
            if (!isMonitoring) {
                heartRateConnectionManager.clearObservedDevice()
            }
            return
        }

        if (selectedDeviceAddress.isNullOrBlank()) {
            if (!isMonitoring) {
                heartRateConnectionManager.clearObservedDevice()
            }
            return
        }

        heartRateConnectionManager.observeDevice(
            deviceName = selectedDeviceName(selectedDeviceAddress),
            deviceAddress = selectedDeviceAddress,
        )
    }

    private fun selectedDeviceName(deviceAddress: String): String =
        _uiState.value.availableDevices
            .firstOrNull { it.address == deviceAddress }
            ?.name
            ?: deviceAddress
}
