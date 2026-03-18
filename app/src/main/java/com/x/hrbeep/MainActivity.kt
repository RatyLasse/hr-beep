package com.x.hrbeep

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.x.hrbeep.monitoring.ConnectionState
import com.x.hrbeep.ui.theme.HrBeepTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current
            var systemStateVersion by remember { mutableIntStateOf(0) }
            val monitoringPermissions = remember { requiredMonitoringPermissions() }
            val hasMonitoringPermissions = monitoringPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            val hasLocationPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            val gpsEnabled = context.getSystemService(LocationManager::class.java)
                .isProviderEnabled(LocationManager.GPS_PROVIDER)

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                systemStateVersion += 1
                viewModel.refreshBluetoothState()
            }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                systemStateVersion += 1
                viewModel.refreshBluetoothState()
            }

            val enableBluetoothLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                systemStateVersion += 1
                viewModel.refreshBluetoothState()
            }

            LaunchedEffect(uiState.message) {
                val message = uiState.message ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(message)
                viewModel.dismissMessage()
            }

            LaunchedEffect(systemStateVersion, hasMonitoringPermissions, uiState.bluetoothEnabled) {
                viewModel.triggerAutoScanIfReady(hasMonitoringPermissions)
            }

            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        systemStateVersion += 1
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
                            hasMonitoringPermissions = hasMonitoringPermissions,
                            hasLocationPermission = hasLocationPermission,
                            gpsEnabled = gpsEnabled,
                            onGrantPermissions = {
                                permissionLauncher.launch(monitoringPermissions)
                            },
                            onGrantLocationPermission = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                            onEnableBluetooth = {
                                enableBluetoothLauncher.launch(viewModel.openBluetoothEnableIntent())
                            },
                            onThresholdChange = viewModel::onThresholdInputChanged,
                            onScan = viewModel::scanForDevices,
                            onSelectDevice = viewModel::selectDevice,
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

private fun requiredMonitoringPermissions(): Array<String> = buildList {
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
    hasMonitoringPermissions: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onGrantPermissions: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onThresholdChange: (String) -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
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
                        hasMonitoringPermissions = hasMonitoringPermissions,
                        bluetoothEnabled = uiState.bluetoothEnabled,
                        monitoringState = uiState.monitoringState,
                        onGrantPermissions = onGrantPermissions,
                        onEnableBluetooth = onEnableBluetooth,
                    )

                    DeviceCompactRow(
                        uiState = uiState,
                        onScan = onScan,
                        onSelectDevice = onSelectDevice,
                        enabled = hasMonitoringPermissions &&
                            uiState.bluetoothEnabled &&
                            !uiState.monitoringState.isMonitoring,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
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

                    DistanceStatusSection(
                        isMonitoring = uiState.monitoringState.isMonitoring,
                        distanceMeters = uiState.monitoringState.distanceMeters,
                        isDistanceTrackingEnabled = uiState.monitoringState.isDistanceTrackingEnabled,
                        hasLocationPermission = hasLocationPermission,
                        gpsEnabled = gpsEnabled,
                        onGrantLocationPermission = onGrantLocationPermission,
                    )
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

                    if (uiState.monitoringState.averageHr != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Average HR: ${uiState.monitoringState.averageHr} bpm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    uiState.monitoringState.distanceMeters?.let { distanceMeters ->
                        Text(
                            text = if (uiState.monitoringState.isMonitoring) {
                                "Distance: ${formatDistanceKilometers(distanceMeters)} km"
                            } else {
                                "Last distance: ${formatDistanceKilometers(distanceMeters)} km"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onStartMonitoring,
                            enabled = hasMonitoringPermissions && uiState.bluetoothEnabled && !uiState.monitoringState.isMonitoring,
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
private fun DashboardStatusRow(
    hasMonitoringPermissions: Boolean,
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
                    !hasMonitoringPermissions -> "Bluetooth and notification permissions are still missing."
                    !bluetoothEnabled -> "Bluetooth is off."
                    monitoringState.connectionState == ConnectionState.Connected ->
                        "Connected to ${monitoringState.deviceName ?: "Polar H10"}. Alarm is idle."
                    monitoringState.connectionState == ConnectionState.Monitoring ->
                        "Monitoring ${monitoringState.deviceName ?: "Polar H10"}"
                    monitoringState.connectionState == ConnectionState.Connecting ->
                        "Connecting to ${monitoringState.deviceName ?: "Polar H10"}..."
                    monitoringState.connectionState == ConnectionState.Disconnected ->
                        monitoringState.errorMessage ?: "Sensor disconnected."
                    monitoringState.connectionState == ConnectionState.Error ->
                        monitoringState.errorMessage ?: "Monitoring failed."
                    else -> "Ready to scan for your H10."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (!hasMonitoringPermissions) {
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
private fun DistanceStatusSection(
    isMonitoring: Boolean,
    distanceMeters: Double?,
    isDistanceTrackingEnabled: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onGrantLocationPermission: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Distance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = when {
                !hasLocationPermission ->
                    "Optional GPS distance tracking is off until you allow location access."
                !gpsEnabled ->
                    "Turn GPS on to add distance tracking to the next session."
                isMonitoring && !isDistanceTrackingEnabled ->
                    "GPS distance tracking is unavailable right now."
                isMonitoring ->
                    "Distance tracking is active."
                else ->
                    "Distance tracking starts automatically when GPS is on."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasLocationPermission) {
            TextButton(onClick = onGrantLocationPermission) {
                Text("Allow location")
            }
        }
    }
}

private fun formatDistanceKilometers(distanceMeters: Double): String =
    String.format(Locale.US, "%.2f", distanceMeters / 1_000.0)

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
                val batteryLabel = when {
                    uiState.monitoringState.deviceAddress == selected.address &&
                        uiState.monitoringState.batteryLevelPercent != null ->
                        "Battery ${uiState.monitoringState.batteryLevelPercent}%"
                    else -> "Battery --"
                }
                Text(
                    text = selected.name,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        onSelectDevice(selected.address)
                    },
                )
                Text(
                    text = batteryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onScan, enabled = enabled) {
            Text(if (uiState.isScanning) "Scanning..." else "Scan")
        }
    }
}
