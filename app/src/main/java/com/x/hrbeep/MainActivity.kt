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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
                            hasAllPermissions = requiredPermissions.all { permission ->
                                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                            },
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
                            onPreviewSoundStyle = viewModel::previewSoundStyle,
                            onSoundIntensityChange = viewModel::updateSoundIntensity,
                            onPreviewCurrentSound = viewModel::previewCurrentSound,
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
    onPreviewSoundStyle: (AlarmSoundStyle) -> Unit,
    onSoundIntensityChange: (Float) -> Unit,
    onPreviewCurrentSound: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Heart rate alarm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect your Polar H10, set your own HR ceiling, and let the app beep when you drift above it during a run.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            StatusCard(
                hasAllPermissions = hasAllPermissions,
                bluetoothEnabled = uiState.bluetoothEnabled,
                monitoringState = uiState.monitoringState,
                onGrantPermissions = onGrantPermissions,
                onEnableBluetooth = onEnableBluetooth,
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Heart-rate limit", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = uiState.thresholdInput,
                        onValueChange = onThresholdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Beep above (bpm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Text(
                        text = "Saved limit: ${uiState.persistedThreshold} bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Alert sound", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Try a few tones before locking one in. The alert timing will still follow your live heart rate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    AlarmSoundStyle.entries.forEachIndexed { index, style ->
                        SoundStyleRow(
                            style = style,
                            selected = style == uiState.selectedSoundStyle,
                            onSelect = { onSelectSoundStyle(style) },
                            onPreview = { onPreviewSoundStyle(style) },
                        )
                        if (index != AlarmSoundStyle.entries.lastIndex) {
                            HorizontalDivider()
                        }
                    }

                    Text("Alert intensity", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = uiState.soundIntensity.toFloat(),
                        onValueChange = onSoundIntensityChange,
                        valueRange = 0f..100f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Relative level: ${uiState.soundIntensity}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AssistChip(
                            onClick = onPreviewCurrentSound,
                            label = { Text("Preview current") },
                        )
                    }
                    Text(
                        text = "This changes the alert strength inside the media stream. Your phone's media volume still sets the overall ceiling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Polar straps", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onScan, enabled = hasAllPermissions && uiState.bluetoothEnabled) {
                            Text(if (uiState.isScanning) "Scanning..." else "Scan")
                        }
                    }

                    if (uiState.availableDevices.isEmpty()) {
                        Text(
                            text = "No strap selected yet. Put on the H10 and run a scan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.availableDevices.forEach { device ->
                            DeviceRow(
                                device = device,
                                selected = device.address == uiState.selectedDeviceAddress,
                                onClick = { onSelectDevice(device.address) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Current HR", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = uiState.monitoringState.currentHr?.let { "$it" } ?: "--",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (uiState.monitoringState.currentHr == null) "Waiting for live data" else "bpm",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onStartMonitoring,
                            enabled = hasAllPermissions && uiState.bluetoothEnabled && !uiState.monitoringState.isMonitoring,
                        ) {
                            Text("Start monitoring")
                        }
                        TextButton(
                            onClick = onStopMonitoring,
                            enabled = uiState.monitoringState.isMonitoring,
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
private fun SoundStyleRow(
    style: AlarmSoundStyle,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.padding(start = 8.dp, end = 12.dp)) {
                Text(style.displayName, fontWeight = FontWeight.Medium)
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AssistChip(
            onClick = onPreview,
            label = { Text("Preview") },
        )
    }
}

@Composable
private fun StatusCard(
    hasAllPermissions: Boolean,
    bluetoothEnabled: Boolean,
    monitoringState: com.x.hrbeep.monitoring.MonitoringSessionState,
    onGrantPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
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

            if (!hasAllPermissions) {
                Button(onClick = onGrantPermissions) {
                    Text("Grant permissions")
                }
            } else if (!bluetoothEnabled) {
                Button(onClick = onEnableBluetooth) {
                    Text("Enable Bluetooth")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: BleDeviceCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(device.name, fontWeight = FontWeight.Medium)
            Text(
                text = "${device.address} • RSSI ${device.rssi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
