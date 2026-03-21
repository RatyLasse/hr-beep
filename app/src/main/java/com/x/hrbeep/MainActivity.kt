package com.x.hrbeep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x.hrbeep.data.SessionRecord
import com.x.hrbeep.data.ThresholdRepository
import com.x.hrbeep.monitoring.ConnectionState
import com.x.hrbeep.ui.theme.HrBeepTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Accent colors ─────────────────────────────────────────────────────────────
private val NeonCyan = Color(0xFF4DD0E1)
private val NeonGreen = Color(0xFF66BB6A)
private val NeonRed = Color(0xFFEF5350)
private val NeonOrange = Color(0xFFFFB300)
private val CardBackground = Color(0xFF131C26)
private val SubCardBackground = Color(0xFF182430)

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

            HrBeepTheme {
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

// ═══════════════════════════════════════════════════════════════════════════════
//  MainScreen
// ═══════════════════════════════════════════════════════════════════════════════

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
    onLowerBoundChange: (String) -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PageDots(currentPage = pagerState.currentPage, pageCount = 2)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> MonitoringTab(
                    modifier = Modifier.fillMaxSize(),
                    uiState = uiState,
                    hasMonitoringPermissions = hasMonitoringPermissions,
                    hasLocationPermission = hasLocationPermission,
                    gpsEnabled = gpsEnabled,
                    onGrantPermissions = onGrantPermissions,
                    onGrantLocationPermission = onGrantLocationPermission,
                    onEnableBluetooth = onEnableBluetooth,
                    onThresholdChange = onThresholdChange,
                    onLowerBoundChange = onLowerBoundChange,
                    onScan = onScan,
                    onSelectDevice = onSelectDevice,
                    onStartMonitoring = onStartMonitoring,
                    onStopMonitoring = onStopMonitoring,
                )
                1 -> HistoryTab(
                    modifier = Modifier.fillMaxSize(),
                    sessions = uiState.sessionHistory.filter { it.id != uiState.pendingDeleteId },
                    onDelete = onDeleteSession,
                )
            }
        }
    }
}

@Composable
private fun PageDots(currentPage: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == currentPage) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MonitoringTab
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MonitoringTab(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    hasMonitoringPermissions: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onGrantPermissions: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onThresholdChange: (String) -> Unit,
    onLowerBoundChange: (String) -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showGpsDialog by remember { mutableStateOf(false) }

    if (showGpsDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDialog = false },
            title = { Text("GPS is off") },
            text = { Text("Enable GPS for distance tracking, or continue with heart-rate only.") },
            confirmButton = {
                TextButton(onClick = {
                    showGpsDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Enable GPS")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGpsDialog = false
                    onStartMonitoring()
                }) {
                    Text("HR only")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        // ── Top controls ────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceStatusCard(
                uiState = uiState,
                hasMonitoringPermissions = hasMonitoringPermissions,
                onGrantPermissions = onGrantPermissions,
                onEnableBluetooth = onEnableBluetooth,
                onScan = onScan,
                onSelectDevice = onSelectDevice,
            )

            BpmLimitsSection(
                uiState = uiState,
                onThresholdChange = onThresholdChange,
                onLowerBoundChange = onLowerBoundChange,
            )

            DistanceStatusSection(
                isMonitoring = uiState.monitoringState.isMonitoring,
                isDistanceTrackingEnabled = uiState.monitoringState.isDistanceTrackingEnabled,
                hasLocationPermission = hasLocationPermission,
                gpsEnabled = gpsEnabled,
                onGrantLocationPermission = onGrantLocationPermission,
            )
        }

        // ── Bottom section: HR graph + stats + button ───────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // HR graph + big number
            val hrColorTarget = when {
                !uiState.monitoringState.isMonitoring || uiState.monitoringState.currentHr == null ->
                    MaterialTheme.colorScheme.onSurface
                isHrOutOfBounds(
                    uiState.monitoringState.currentHr,
                    uiState.persistedThreshold,
                    uiState.persistedLowerBound,
                ) -> NeonRed
                else -> NeonGreen
            }
            val hrColor by animateColorAsState(hrColorTarget, label = "hrColor")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HrGraph(
                    hrHistory = uiState.monitoringState.hrHistory,
                    isMonitoring = uiState.monitoringState.isMonitoring,
                    upperBound = uiState.persistedThreshold,
                    lowerBound = uiState.persistedLowerBound,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = uiState.monitoringState.currentHr?.let { "$it" } ?: "--",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = hrColor,
                )
            }

            Text(
                text = if (uiState.monitoringState.currentHr == null) "Waiting for live data" else "bpm",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SessionStatsRow(monitoringState = uiState.monitoringState)

            Spacer(modifier = Modifier.height(12.dp))

            StartStopButton(
                isMonitoring = uiState.monitoringState.isMonitoring,
                enabled = hasMonitoringPermissions && uiState.bluetoothEnabled,
                onStart = {
                    if (hasLocationPermission && !gpsEnabled) {
                        showGpsDialog = true
                    } else {
                        onStartMonitoring()
                    }
                },
                onStop = onStopMonitoring,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Device Status Card — neon-bordered combined status + device + scan
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DeviceStatusCard(
    uiState: MainUiState,
    hasMonitoringPermissions: Boolean,
    onGrantPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    val monitoringState = uiState.monitoringState
    val isConnected = monitoringState.connectionState == ConnectionState.Connected ||
        monitoringState.connectionState == ConnectionState.Monitoring
    val isConnecting = monitoringState.connectionState == ConnectionState.Connecting
    val hasError = monitoringState.connectionState == ConnectionState.Disconnected ||
        monitoringState.connectionState == ConnectionState.Error
    val enabled = hasMonitoringPermissions &&
        uiState.bluetoothEnabled &&
        !uiState.monitoringState.isMonitoring

    val borderColorTarget = when {
        !hasMonitoringPermissions || !uiState.bluetoothEnabled || hasError -> NeonRed.copy(alpha = 0.6f)
        isConnected -> NeonGreen.copy(alpha = 0.7f)
        isConnecting -> NeonOrange.copy(alpha = 0.6f)
        else -> NeonCyan.copy(alpha = 0.5f)
    }
    val borderColor by animateColorAsState(borderColorTarget, label = "deviceBorder")
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = cardShape)
            .clip(cardShape)
            .background(CardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isConnected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Connected",
                    tint = NeonGreen,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                when {
                    !hasMonitoringPermissions -> {
                        Text(
                            "Permissions required",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonRed,
                        )
                    }
                    !uiState.bluetoothEnabled -> {
                        Text(
                            "Bluetooth is off",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonRed,
                        )
                    }
                    uiState.availableDevices.isEmpty() && !isConnected -> {
                        Text(
                            "No device selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    else -> {
                        val device = uiState.availableDevices.firstOrNull {
                            it.address == uiState.selectedDeviceAddress
                        } ?: uiState.availableDevices.firstOrNull()
                        Text(
                            text = device?.name ?: monitoringState.deviceName ?: "Heart rate monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val batteryText = when {
                            monitoringState.batteryLevelPercent != null ->
                                "Battery ${monitoringState.batteryLevelPercent}%"
                            isConnecting -> "Connecting..."
                            isConnected -> "Connected"
                            else -> null
                        }
                        if (batteryText != null) {
                            Text(
                                text = batteryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            when {
                !hasMonitoringPermissions -> {
                    TextButton(onClick = onGrantPermissions) {
                        Text("Grant", color = NeonCyan)
                    }
                }
                !uiState.bluetoothEnabled -> {
                    TextButton(onClick = onEnableBluetooth) {
                        Text("Enable", color = NeonCyan)
                    }
                }
                else -> {
                    TextButton(onClick = onScan, enabled = enabled) {
                        Text(
                            text = if (uiState.isScanning) "Scanning..." else "Scan",
                            color = if (enabled) NeonCyan else NeonCyan.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }

        // Multi-device selection
        if (uiState.availableDevices.size > 1 && hasMonitoringPermissions && uiState.bluetoothEnabled) {
            uiState.availableDevices.forEach { device ->
                val isSelected = device.address == uiState.selectedDeviceAddress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { onSelectDevice(device.address) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = if (enabled) { { onSelectDevice(device.address) } } else null,
                    )
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BPM Limits — two side-by-side dark cards
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BpmLimitsSection(
    uiState: MainUiState,
    onThresholdChange: (String) -> Unit,
    onLowerBoundChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "BPM Limits",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BpmLimitCard(
                label = "Min BPM",
                inputValue = uiState.lowerBoundInput,
                onValueChange = onLowerBoundChange,
                onDecrement = {
                    val lb = uiState.persistedLowerBound
                    if (lb != null) onLowerBoundChange(if (lb <= 20) "" else (lb - 1).toString())
                },
                onIncrement = {
                    val lb = uiState.persistedLowerBound
                    onLowerBoundChange(((lb ?: 39) + 1).coerceAtMost(300).toString())
                },
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f),
            )
            BpmLimitCard(
                label = "Max BPM",
                inputValue = uiState.thresholdInput,
                onValueChange = onThresholdChange,
                onDecrement = {
                    val t = uiState.persistedThreshold
                    if (t != null) onThresholdChange((t - 1).coerceAtLeast(20).toString())
                },
                onIncrement = {
                    val t = uiState.persistedThreshold
                    onThresholdChange(((t ?: (ThresholdRepository.DEFAULT_THRESHOLD_BPM - 1)) + 1).coerceAtMost(300).toString())
                },
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val DarkButtonBackground = Color(0xFF0E151D)
private val LimitCardBg = Color(0xFF2A3544)
private val LimitButtonBg = Color(0xFF1C2834)
private val LimitValueBg = Color(0xFF0E151D)

@Composable
private fun BpmLimitCard(
    label: String,
    inputValue: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val cardShape = RoundedCornerShape(14.dp)
    val segmentCorner = 10.dp

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(LimitCardBg)
            .padding(top = 12.dp, bottom = 10.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Minus button — left rounded corners only
            RepeatButton(
                action = onDecrement,
                shape = RoundedCornerShape(topStart = segmentCorner, bottomStart = segmentCorner),
                background = LimitButtonBg,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "\u2212",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Value field — rectangular, darkest background
            BasicTextField(
                value = inputValue,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1.2f)
                    .background(LimitValueBg)
                    .padding(vertical = 14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { focusManager.clearFocus() },
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(NeonCyan),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        innerTextField()
                    }
                },
            )

            // Plus button — right rounded corners only
            RepeatButton(
                action = onIncrement,
                shape = RoundedCornerShape(topEnd = segmentCorner, bottomEnd = segmentCorner),
                background = LimitButtonBg,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "+",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Distance Status — compact one-liner
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DistanceStatusSection(
    isMonitoring: Boolean,
    isDistanceTrackingEnabled: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onGrantLocationPermission: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                !hasLocationPermission -> "GPS distance tracking is off."
                !gpsEnabled -> "Turn GPS on for distance tracking."
                isMonitoring && !isDistanceTrackingEnabled -> "GPS unavailable."
                isMonitoring -> "Distance tracking active."
                else -> "Distance tracking auto-starts with GPS."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!hasLocationPermission) {
            TextButton(onClick = onGrantLocationPermission) {
                Text("Allow", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Session Stats Row — three stat boxes during monitoring
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SessionStatsRow(
    monitoringState: com.x.hrbeep.monitoring.MonitoringSessionState,
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val startTime = monitoringState.monitoringStartTimeMs

    LaunchedEffect(startTime) {
        if (startTime == null) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            delay(1000L)
        }
    }

    val stats = listOf(
        "Average HR" to (monitoringState.averageHr?.let { "$it bpm" } ?: "--"),
        "Duration" to (if (startTime != null) formatDurationLong(elapsedSeconds) else "--"),
        "Distance" to (monitoringState.distanceMeters?.let { "${formatKilometers(it)} km" } ?: "--"),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SubCardBackground)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Start / Stop Button — neon-styled toggle
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StartStopButton(
    isMonitoring: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (isMonitoring) {
        val stopShape = RoundedCornerShape(14.dp)
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(
                    width = 1.5.dp,
                    color = NeonRed.copy(alpha = 0.7f),
                    shape = stopShape,
                ),
            shape = stopShape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NeonRed,
            ),
        ) {
            Text(
                "\u25A0  Stop",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    } else {
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = Color(0xFF0A1018),
                disabledContainerColor = NeonCyan.copy(alpha = 0.25f),
                disabledContentColor = Color(0xFF0A1018).copy(alpha = 0.5f),
            ),
        ) {
            Text(
                "Start",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RepeatButton — dark rectangle with hold-to-repeat
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RepeatButton(
    action: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    background: Color = DarkButtonBackground,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentAction by rememberUpdatedState(action)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentAction()
                    val job = scope.launch {
                        delay(400L)
                        var interval = 150L
                        while (true) {
                            currentAction()
                            delay(interval)
                            interval = (interval - 15L).coerceAtLeast(60L)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  HR Graph — unchanged (user likes current visual)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HrGraph(
    hrHistory: List<Int>,
    isMonitoring: Boolean,
    upperBound: Int?,
    lowerBound: Int?,
    modifier: Modifier = Modifier,
    showCenterMask: Boolean = true,
) {
    val idleLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val n = hrHistory.size
        if (n < 2) return@Canvas

        val minHr = (hrHistory.min() - 5).coerceAtLeast(30)
        val maxHr = (hrHistory.max() + 5).coerceAtMost(300)
        val range = (maxHr - minHr).toFloat().coerceAtLeast(10f)

        fun hrToY(hr: Int): Float = size.height - (hr - minHr) / range * size.height
        fun indexToX(i: Int): Float = i.toFloat() / (n - 1) * size.width

        val xs = FloatArray(n) { indexToX(it) }
        val ys = FloatArray(n) { hrToY(hrHistory[it]) }
        val strokeWidth = 3.dp.toPx()
        val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())

        var currentColor: Color? = null
        var currentPath: Path? = null

        fun flushPath() {
            val p = currentPath ?: return
            val c = currentColor ?: return
            drawPath(path = p, color = c, style = strokeStyle)
            currentPath = null
            currentColor = null
        }

        for (i in 0 until n - 1) {
            val color = when {
                !isMonitoring -> idleLineColor
                isHrOutOfBounds(hrHistory[i], upperBound, lowerBound) ||
                    isHrOutOfBounds(hrHistory[i + 1], upperBound, lowerBound) -> NeonRed
                else -> NeonGreen
            }

            if (color != currentColor) {
                flushPath()
                currentColor = color
                currentPath = Path().apply { moveTo(xs[i], ys[i]) }
            }

            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 2).coerceAtMost(n - 1)
            val cp1x = xs[i] + (xs[i + 1] - xs[prev]) / 6f
            val cp1y = ys[i] + (ys[i + 1] - ys[prev]) / 6f
            val cp2x = xs[i + 1] - (xs[next] - xs[i]) / 6f
            val cp2y = ys[i + 1] - (ys[next] - ys[i]) / 6f
            currentPath!!.cubicTo(cp1x, cp1y, cp2x, cp2y, xs[i + 1], ys[i + 1])
        }

        flushPath()

        if (showCenterMask) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.15f to Color.Transparent,
                    0.38f to Color.Black.copy(alpha = 0.80f),
                    0.62f to Color.Black.copy(alpha = 0.80f),
                    0.85f to Color.Transparent,
                ),
                blendMode = BlendMode.DstOut,
            )
        }

        drawContext.canvas.restore()
    }
}

private fun isHrOutOfBounds(hr: Int, upperBound: Int?, lowerBound: Int?): Boolean =
    (upperBound != null && hr > upperBound) || (lowerBound != null && hr < lowerBound)

// ═══════════════════════════════════════════════════════════════════════════════
//  History Tab — card-based layout with HR graphs
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryTab(
    modifier: Modifier = Modifier,
    sessions: List<SessionRecord>,
    onDelete: (Long) -> Unit,
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = "No sessions recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(session = session, onDelete = { onDelete(session.id) })
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionRecord, onDelete: () -> Unit) {
    val cardShape = RoundedCornerShape(14.dp)
    val hrHistoryList = remember(session.hrHistory) { session.hrHistoryList() }
    val minHr = remember(hrHistoryList) { hrHistoryList.minOrNull() }
    val maxHr = remember(hrHistoryList) { hrHistoryList.maxOrNull() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header: date + delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatSessionDate(session.startTimeMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete session",
                    tint = NeonRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Stats grid: 2x2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryStatBox(
                label = "Duration",
                value = formatDuration(session.durationSeconds),
                modifier = Modifier.weight(1f),
            )
            HistoryStatBox(
                label = "Avg BPM",
                value = session.averageHr?.let { "$it" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryStatBox(
                label = "Distance",
                value = session.distanceMeters?.let { "${formatKilometers(it)} km" } ?: "--",
                modifier = Modifier.weight(1f),
            )
            HistoryStatBox(
                label = "Pace",
                value = session.paceSecondsPerKm?.let { "${formatPace(it)} min/km" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }

        // Full-width HR graph
        if (hrHistoryList.size >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                HrGraph(
                    hrHistory = hrHistoryList,
                    isMonitoring = true,
                    upperBound = session.upperBound,
                    lowerBound = session.lowerBound,
                    showCenterMask = false,
                    modifier = Modifier.fillMaxSize(),
                )
                if (minHr != null && maxHr != null) {
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "$maxHr",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
                        Text(
                            text = "$minHr",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(SubCardBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Formatting helpers
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatSessionDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private fun formatDurationLong(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
