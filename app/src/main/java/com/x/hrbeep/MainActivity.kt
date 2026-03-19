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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x.hrbeep.data.BleDeviceCandidate
import com.x.hrbeep.data.SessionRecord
import com.x.hrbeep.monitoring.ConnectionState
import com.x.hrbeep.ui.theme.HrBeepTheme
import java.text.SimpleDateFormat
import java.util.Date
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
                            onLowerBoundChange = viewModel::onLowerBoundInputChanged,
                            onScan = viewModel::scanForDevices,
                            onSelectDevice = viewModel::selectDevice,
                            onSoundIntensityChange = viewModel::updateSoundIntensity,
                            onStartMonitoring = viewModel::startMonitoring,
                            onStopMonitoring = viewModel::stopMonitoring,
                            onDeleteSession = viewModel::deleteSession,
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
    onLowerBoundChange: (String) -> Unit,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onSoundIntensityChange: (Float) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    onSoundIntensityChange = onSoundIntensityChange,
                    onStartMonitoring = onStartMonitoring,
                    onStopMonitoring = onStopMonitoring,
                )
                1 -> HistoryTab(
                    modifier = Modifier.fillMaxSize(),
                    sessions = uiState.sessionHistory,
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
    onSoundIntensityChange: (Float) -> Unit,
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
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Limits", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.lowerBoundInput,
                        onValueChange = onLowerBoundChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Min BPM (opt.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.thresholdInput,
                        onValueChange = onThresholdChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Max BPM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Alert intensity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${uiState.soundIntensity}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = uiState.soundIntensity.toFloat(),
                    onValueChange = onSoundIntensityChange,
                    valueRange = 0f..100f,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HrGraph(
                    hrHistory = uiState.monitoringState.hrHistory,
                    isMonitoring = uiState.monitoringState.isMonitoring,
                    upperBound = uiState.persistedThreshold,
                    lowerBound = uiState.persistedLowerBound,
                    modifier = Modifier.matchParentSize(),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 16.dp),
                ) {
                    val hrColor = when {
                        !uiState.monitoringState.isMonitoring || uiState.monitoringState.currentHr == null ->
                            MaterialTheme.colorScheme.onSurface
                        isHrOutOfBounds(
                            uiState.monitoringState.currentHr,
                            uiState.persistedThreshold,
                            uiState.persistedLowerBound,
                        ) -> Color(0xFFEF5350)
                        else -> Color(0xFF66BB6A)
                    }
                    Text(
                        text = uiState.monitoringState.currentHr?.let { "$it" } ?: "--",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = hrColor,
                    )
                    Text(
                        text = if (uiState.monitoringState.currentHr == null) "Waiting for live data" else "bpm",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.monitoringState.averageHr != null) {
                        Text(
                            text = "Average HR: ${uiState.monitoringState.averageHr} bpm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.monitoringState.distanceMeters?.let { distanceMeters ->
                        Text(
                            text = if (uiState.monitoringState.isMonitoring) {
                                "Distance: ${formatKilometers(distanceMeters)} km"
                            } else {
                                "Last distance: ${formatKilometers(distanceMeters)} km"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.monitoringState.paceSecondsPerKm?.let { pace ->
                        Text(
                            text = "Pace: ${formatPace(pace)} min/km",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (hasLocationPermission && !gpsEnabled) {
                            showGpsDialog = true
                        } else {
                            onStartMonitoring()
                        }
                    },
                    enabled = hasMonitoringPermissions && uiState.bluetoothEnabled && !uiState.monitoringState.isMonitoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start")
                }
                OutlinedButton(
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

@Composable
private fun HrGraph(
    hrHistory: List<Int>,
    isMonitoring: Boolean,
    upperBound: Int,
    lowerBound: Int?,
    modifier: Modifier = Modifier,
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

        // Compositing layer so DstOut masking only affects what we draw here
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())

        // Merge consecutive same-color segments into one continuous path to avoid dots at joints
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
                    isHrOutOfBounds(hrHistory[i + 1], upperBound, lowerBound) -> Color(0xFFEF5350)
                else -> Color(0xFF66BB6A)
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

        // Erase the center so the HR number reads cleanly, fading in from both edges
        drawRect(
            brush = Brush.horizontalGradient(
                0.15f to Color.Transparent,
                0.38f to Color.Black.copy(alpha = 0.80f),
                0.62f to Color.Black.copy(alpha = 0.80f),
                0.85f to Color.Transparent,
            ),
            blendMode = BlendMode.DstOut,
        )

        drawContext.canvas.restore()
    }
}

private fun isHrOutOfBounds(hr: Int, upperBound: Int, lowerBound: Int?): Boolean =
    hr > upperBound || (lowerBound != null && hr < lowerBound)

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
            Text(
                text = "No sessions recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(sessions, key = { it.id }) { session ->
                SessionItem(session = session, onDelete = { onDelete(session.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SessionItem(session: SessionRecord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatSessionDate(session.startTimeMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(formatDuration(session.durationSeconds))
                    session.averageHr?.let { append(" · avg $it bpm") }
                    session.distanceMeters?.let { append(" · ${formatKilometers(it)} km") }
                    session.paceSecondsPerKm?.let { append(" · ${formatPace(it)} min/km") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            val statusColor = when {
                !hasMonitoringPermissions || !bluetoothEnabled ||
                monitoringState.connectionState == ConnectionState.Disconnected ||
                monitoringState.connectionState == ConnectionState.Error ->
                    MaterialTheme.colorScheme.error
                monitoringState.connectionState == ConnectionState.Monitoring ||
                monitoringState.connectionState == ConnectionState.Connected ->
                    Color(0xFF66BB6A)
                monitoringState.connectionState == ConnectionState.Connecting ->
                    Color(0xFFFFB300)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = when {
                    !hasMonitoringPermissions -> "Bluetooth and notification permissions are still missing."
                    !bluetoothEnabled -> "Bluetooth is off."
                    monitoringState.connectionState == ConnectionState.Connected ->
                        "Connected to ${monitoringState.deviceName ?: "heart rate monitor"}. Alarm is idle."
                    monitoringState.connectionState == ConnectionState.Monitoring ->
                        "Monitoring ${monitoringState.deviceName ?: "heart rate monitor"}"
                    monitoringState.connectionState == ConnectionState.Connecting ->
                        "Connecting to ${monitoringState.deviceName ?: "heart rate monitor"}..."
                    monitoringState.connectionState == ConnectionState.Disconnected ->
                        monitoringState.errorMessage ?: "Sensor disconnected."
                    monitoringState.connectionState == ConnectionState.Error ->
                        monitoringState.errorMessage ?: "Monitoring failed."
                    else -> "Ready to scan for your H10."
                },
                color = statusColor,
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
        Text("Distance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun formatSessionDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun DeviceCompactRow(
    uiState: MainUiState,
    onScan: () -> Unit,
    onSelectDevice: (String) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Device", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
