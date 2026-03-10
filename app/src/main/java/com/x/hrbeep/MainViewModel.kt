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
import com.x.hrbeep.monitoring.AlarmPlayer
import com.x.hrbeep.monitoring.AlarmSoundStyle
import com.x.hrbeep.monitoring.ConnectionState
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

data class MainUiState(
    val thresholdInput: String = ThresholdRepository.DEFAULT_THRESHOLD_BPM.toString(),
    val persistedThreshold: Int = ThresholdRepository.DEFAULT_THRESHOLD_BPM,
    val selectedSoundStyle: AlarmSoundStyle = AlarmSoundStyle.default,
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
    private val alarmPlayer: AlarmPlayer = container.alarmPlayer

    private val _uiState = MutableStateFlow(
        MainUiState(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

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
            thresholdRepository.soundStyleFlow.collect { soundStyle ->
                _uiState.update { state ->
                    state.copy(selectedSoundStyle = soundStyle)
                }
            }
        }

        viewModelScope.launch {
            monitoringController.state.collect { monitoringState ->
                _uiState.update { state ->
                    state.copy(monitoringState = monitoringState)
                }
            }
        }
    }

    fun refreshBluetoothState() {
        _uiState.update { state ->
            state.copy(bluetoothEnabled = bleHeartRateRepository.isBluetoothEnabled())
        }
    }

    fun onThresholdInputChanged(input: String) {
        val filtered = input.filter(Char::isDigit).take(3)
        _uiState.update { state -> state.copy(thresholdInput = filtered) }

        filtered.toIntOrNull()?.takeIf { it in 40..240 }?.let { parsed ->
            viewModelScope.launch {
                thresholdRepository.saveThreshold(parsed)
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { state -> state.copy(message = null) }
    }

    fun selectSoundStyle(style: AlarmSoundStyle) {
        viewModelScope.launch {
            thresholdRepository.saveSoundStyle(style)
        }
    }

    fun previewSoundStyle(style: AlarmSoundStyle) {
        alarmPlayer.beep(style)
    }

    fun selectDevice(address: String) {
        _uiState.update { state -> state.copy(selectedDeviceAddress = address) }
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

            threshold == null || threshold !in 40..240 -> {
                _uiState.update { state -> state.copy(message = "Set a heart-rate limit between 40 and 240 bpm.") }
            }

            selectedDevice == null -> {
                _uiState.update { state -> state.copy(message = "Pick a Polar device before starting.") }
            }

            else -> {
                val context = getApplication<Application>()
                val intent = MonitoringService.startIntent(
                    context = context,
                    deviceAddress = selectedDevice.address,
                    deviceName = selectedDevice.name,
                    threshold = threshold,
                    soundStyle = currentState.selectedSoundStyle,
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
}
