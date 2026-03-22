package com.x.heartbeep

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x.heartbeep.ui.MainScreen
import com.x.heartbeep.ui.theme.HeartBeepTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = androidx.compose.ui.platform.LocalContext.current
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

            LaunchedEffect(uiState.monitoringState.isMonitoring) {
                if (uiState.monitoringState.isMonitoring) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    setShowWhenLocked(false)
                    setTurnScreenOn(false)
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }

            LaunchedEffect(uiState.message) {
                val message = uiState.message ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(message)
                viewModel.dismissMessage()
            }

            LaunchedEffect(uiState.pendingDeleteId) {
                if (uiState.pendingDeleteId == null) return@LaunchedEffect
                val result = snackbarHostState.showSnackbar(
                    message = "Session deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                    SnackbarResult.Dismissed -> viewModel.commitDelete()
                }
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

            HeartBeepTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
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
                        onLowerBoundChange = viewModel::onLowerBoundInputChanged,
                        onScan = viewModel::scanForDevices,
                        onSelectDevice = viewModel::selectDevice,
                        onStartMonitoring = viewModel::startMonitoring,
                        onStopMonitoring = viewModel::stopMonitoring,
                        onDeleteSession = viewModel::requestDeleteSession,
                        onExportSessions = {
                            viewModel.exportSessionsTcxIntent()?.let { intent ->
                                startActivity(Intent.createChooser(intent, "Export Sessions"))
                            }
                        },
                    )
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
