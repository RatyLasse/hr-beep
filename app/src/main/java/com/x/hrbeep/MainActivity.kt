package com.x.hrbeep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x.hrbeep.data.BleDeviceCandidate
import com.x.hrbeep.monitoring.AlarmSoundStyle
import com.x.hrbeep.monitoring.ConnectionState
import com.x.hrbeep.ui.theme.HrBeepTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current
            val requiredPermissions = remember { requiredRuntimePermissions() }
            val hasAllPermissions = requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                viewModel.refreshBluetoothState()
            }

            val enableBluetoothLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                viewModel.refreshBluetoothState()
            }

            LaunchedEffect(uiState.message) {
                val message = uiState.message ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(message)
                viewModel.dismissMessage()
            }

            LaunchedEffect(hasAllPermissions, uiState.bluetoothEnabled) {
                viewModel.triggerAutoScanIfReady(hasAllPermissions)
            }

            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshBluetoothState()
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            HrBeepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    ) { padding ->
                        MainScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            uiState = uiState,
                            hasAllPermissions = hasAllPermissions,
                            onGrantPermissions = {
                                permissionLauncher.launch(requiredPermissions)
                            },
                            onEnableBluetooth = {
                                enableBluetoothLauncher.launch(viewModel.openBluetoothEnableIntent())
                            },
                            onThresholdChange = viewModel::onThresholdInputChanged,
                            onScan = viewModel::scanForDevices,
                            onSelectDevice = viewModel::selectDevice,
                            onSelectSoundStyle = viewModel::selectSoundStyle,
                            onSoundIntensityChange = viewModel::updateSoundIntensity,
                            onStartMonitoring = viewModel::startMonitoring,
                            onStopMonitoring = viewModel::stopMonitoring,
                        )
                    }
                }
            }
        }
    }
}

private fun requiredRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    hasAllPermissions: Boolean,
    onGrantPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onThresholdChange: (String) -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onSelectSoundStyle: (AlarmSoundStyle) -> Unit,
    onSoundIntensityChange: (Float) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Heart rate alarm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DashboardStatusRow(
                        hasAllPermissions = hasAllPermissions,
                        bluetoothEnabled = uiState.bluetoothEnabled,
                        monitoringState = uiState.monitoringState,
                        onGrantPermissions = onGrantPermissions,
                        onEnableBluetooth = onEnableBluetooth,
                    )

                    DeviceCompactRow(
                        uiState = uiState,
                        onScan = onScan,
                        onSelectDevice = onSelectDevice,
                        enabled = hasAllPermissions && uiState.bluetoothEnabled,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Limit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = uiState.thresholdInput,
                                    onValueChange = onThresholdChange,
                                    modifier = Modifier.width(112.dp),
                                    label = { Text("bpm") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Sound", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SoundOptionChip(
                                    style = AlarmSoundStyle.Bright,
                                    selected = uiState.selectedSoundStyle == AlarmSoundStyle.Bright,
                                    onClick = { onSelectSoundStyle(AlarmSoundStyle.Bright) },
                                )
                                SoundOptionChip(
                                    style = AlarmSoundStyle.Pulse,
                                    selected = uiState.selectedSoundStyle == AlarmSoundStyle.Pulse,
                                    onClick = { onSelectSoundStyle(AlarmSoundStyle.Pulse) },
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Alert intensity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = uiState.soundIntensity.toFloat(),
                            onValueChange = onSoundIntensityChange,
                            valueRange = 0f..100f,
                        )
                        Text(
                            text = "Relative level: ${uiState.soundIntensity}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Current HR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = uiState.monitoringState.currentHr?.let { "$it" } ?: "--",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (uiState.monitoringState.currentHr == null) "Waiting for live data" else "bpm",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onStartMonitoring,
                            enabled = hasAllPermissions && uiState.bluetoothEnabled && !uiState.monitoringState.isMonitoring,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Start")
                        }
                        TextButton(
                            onClick = onStopMonitoring,
                            enabled = uiState.monitoringState.isMonitoring,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOptionChip(
    style: AlarmSoundStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = style.displayName,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardStatusRow(
    hasAllPermissions: Boolean,
    bluetoothEnabled: Boolean,
    monitoringState: com.x.hrbeep.monitoring.MonitoringSessionState,
    onGrantPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    !hasAllPermissions -> "Bluetooth and notification permissions are still missing."
                    !bluetoothEnabled -> "Bluetooth is off."
                    monitoringState.connectionState == ConnectionState.Monitoring ->
                        "Monitoring ${monitoringState.deviceName ?: "Polar H10"}"
                    monitoringState.connectionState == ConnectionState.Connecting ->
                        "Connecting to ${monitoringState.deviceName ?: "Polar H10"}..."
                    monitoringState.connectionState == ConnectionState.Error ->
                        monitoringState.errorMessage ?: "Monitoring failed."
                    else -> "Ready to scan for your H10."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (!hasAllPermissions) {
            TextButton(onClick = onGrantPermissions) {
                Text("Grant")
            }
        } else if (!bluetoothEnabled) {
            TextButton(onClick = onEnableBluetooth) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun DeviceCompactRow(
    uiState: MainUiState,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (uiState.availableDevices.isEmpty()) {
                Text("No strap selected yet", fontWeight = FontWeight.Medium)
            } else {
                val selected = uiState.availableDevices.firstOrNull { it.address == uiState.selectedDeviceAddress }
                    ?: uiState.availableDevices.first()
                Text(
                    text = selected.name,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        onSelectDevice(selected.address)
                    },
                )
            }
        }
        TextButton(onClick = onScan, enabled = enabled) {
            Text(if (uiState.isScanning) "Scanning..." else "Scan")
        }
    }
}
