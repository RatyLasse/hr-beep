package com.x.hrbeep.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.x.hrbeep.HrBeepApplication
import com.x.hrbeep.MainActivity
import com.x.hrbeep.R
import com.x.hrbeep.data.SessionHistoryRepository
import com.x.hrbeep.data.SessionRecord
import com.x.hrbeep.data.ThresholdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoringService : Service() {
    private lateinit var heartRateConnectionManager: HeartRateConnectionManager
    private lateinit var thresholdRepository: ThresholdRepository
    private lateinit var monitoringController: MonitoringController
    private lateinit var alarmPlayer: AlarmPlayer
    private lateinit var gpsLocationTracker: GpsLocationTracker
    private lateinit var sessionHistoryRepository: SessionHistoryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoringJob: Job? = null
    private var distanceTrackingJob: Job? = null
    private var settingsJob: Job? = null
    private var currentSoundIntensity: Int = ThresholdRepository.DEFAULT_SOUND_INTENSITY
    private var sessionStartTimeMs: Long = 0
    private var sessionStartElapsedMs: Long = 0

    override fun onCreate() {
        super.onCreate()
        val container = (application as HrBeepApplication).appContainer
        heartRateConnectionManager = container.heartRateConnectionManager
        thresholdRepository = container.thresholdRepository
        monitoringController = container.monitoringController
        alarmPlayer = container.alarmPlayer
        gpsLocationTracker = container.gpsLocationTracker
        sessionHistoryRepository = container.sessionHistoryRepository
        createNotificationChannel()

        settingsJob = serviceScope.launch {
            thresholdRepository.soundIntensityFlow.collect { intensity ->
                currentSoundIntensity = intensity
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                val threshold = intent.getIntExtra(EXTRA_THRESHOLD, -1)
                val lowerBound = intent.getIntExtra(EXTRA_LOWER_BOUND, 0).takeIf { it > 0 }
                val soundIntensity = intent.getIntExtra(EXTRA_SOUND_INTENSITY, 80)
                currentSoundIntensity = soundIntensity
                if (deviceAddress.isNullOrBlank() || threshold <= 0) {
                    stopMonitoring("Missing device or threshold.")
                } else {
                    startMonitoring(deviceAddress, deviceName, threshold, lowerBound)
                }
                START_STICKY
            }

            ACTION_STOP -> {
                stopMonitoring()
                START_NOT_STICKY
            }

            else -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        distanceTrackingJob?.cancel()
        settingsJob?.cancel()
        alarmPlayer.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startMonitoring(
        deviceAddress: String,
        deviceName: String?,
        threshold: Int,
        lowerBound: Int? = null,
    ) {
        monitoringJob?.cancel()
        alarmPlayer.setPersistentDucking(false)
        sessionStartTimeMs = System.currentTimeMillis()
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        heartRateConnectionManager.observeDevice(
            deviceName = deviceName ?: deviceAddress,
            deviceAddress = deviceAddress,
        )
        monitoringController.beginMonitoring(threshold)
        startDistanceTracking(threshold)

        startForegroundWithState(
            contentText = notificationContentText(
                monitoringState = monitoringController.state.value,
                threshold = threshold,
            ),
            threshold = threshold,
        )

        val alarmDecider = AlarmDecider()
        val audioAlertTracker = SensorConnectionAudioAlertTracker().apply {
            onMonitoringStarted(hasLiveHeartRate = monitoringController.state.value.currentHr != null)
        }
        val hrSampleAccumulator = HeartRateSampleAccumulator()

        monitoringJob = serviceScope.launch(Dispatchers.Default) {
            heartRateConnectionManager.events.collect { event ->
                when (event) {
                    is HeartRateConnectionEvent.ConnectionLost -> {
                        if (event.deviceAddress != deviceAddress) {
                            return@collect
                        }

                        alarmPlayer.setPersistentDucking(false)
                        audioAlertTracker.onMonitoringFailure()?.let { alert ->
                            announceAudioAlert(alert)
                        }
                        updateForegroundNotification(
                            contentText = event.errorMessage,
                            threshold = threshold,
                        )
                    }

                    is HeartRateConnectionEvent.Update -> {
                        if (event.deviceAddress != deviceAddress) {
                            return@collect
                        }

                        val update = event.update
                        audioAlertTracker.onMonitorUpdate(update)?.let { alert ->
                            announceAudioAlert(alert)
                        }

                        val sample = update.heartRateSample
                        if (sample == null) {
                            updateForegroundNotification(
                                contentText = notificationContentText(
                                    monitoringState = monitoringController.state.value,
                                    threshold = threshold,
                                ),
                                threshold = threshold,
                            )
                            return@collect
                        }

                        val averageHr = hrSampleAccumulator.record(sample.bpm)
                        val activeAlertTrigger = alarmDecider.currentAlertTrigger(
                            currentHr = sample.bpm,
                            threshold = threshold,
                            lowerBound = lowerBound,
                        )
                        alarmPlayer.setPersistentDucking(activeAlertTrigger != null)
                        monitoringController.updateMonitoringAverage(averageHr)

                        if (alarmDecider.shouldBeep(
                                currentHr = sample.bpm,
                                threshold = threshold,
                                lowerBound = lowerBound,
                                nowElapsedMs = sample.receivedAtElapsedMs,
                            )
                        ) {
                            alarmPlayer.beep(
                                intensity = currentSoundIntensity,
                                trigger = when (activeAlertTrigger) {
                                    AlarmTrigger.BelowLowerBound -> AlarmTrigger.BelowLowerBound
                                    AlarmTrigger.AboveUpperBound,
                                    null,
                                    -> AlarmTrigger.AboveUpperBound
                                },
                            )
                        }

                        updateForegroundNotification(
                            contentText = notificationContentText(
                                monitoringState = monitoringController.state.value,
                                threshold = threshold,
                            ),
                            threshold = threshold,
                        )
                    }
                }
            }
        }
    }

    private fun stopMonitoring(errorMessage: String? = null) {
        monitoringJob?.cancel()
        monitoringJob = null
        distanceTrackingJob?.cancel()
        distanceTrackingJob = null
        alarmPlayer.setPersistentDucking(false)

        val startElapsed = sessionStartElapsedMs
        if (errorMessage == null && startElapsed > 0) {
            val finalState = monitoringController.state.value
            val durationSeconds = ((SystemClock.elapsedRealtime() - startElapsed) / 1000).toInt()
            if (durationSeconds >= MIN_SESSION_DURATION_SECONDS) {
                serviceScope.launch(Dispatchers.IO) {
                    sessionHistoryRepository.saveSession(
                        SessionRecord(
                            startTimeMs = sessionStartTimeMs,
                            durationSeconds = durationSeconds,
                            averageHr = finalState.averageHr,
                            distanceMeters = finalState.distanceMeters,
                        ),
                    )
                }
            }
            sessionStartElapsedMs = 0
        }

        if (errorMessage == null) {
            monitoringController.endMonitoring()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun announceAudioAlert(alert: SessionAudioAlert) {
        withContext(Dispatchers.Default) {
            alarmPlayer.speak(alert.spokenText(this@MonitoringService))
        }
    }

    private fun startDistanceTracking(threshold: Int) {
        distanceTrackingJob?.cancel()
        distanceTrackingJob = null

        if (!gpsLocationTracker.canTrackDistance()) {
            monitoringController.disableDistanceTracking()
            return
        }

        monitoringController.enableDistanceTracking()

        val distanceTracker = DistanceTracker()
        distanceTrackingJob = serviceScope.launch {
            runCatching {
                gpsLocationTracker.updates().collect { event ->
                    when (event) {
                        is GpsTrackingEvent.LocationUpdate -> {
                            val progress = distanceTracker.record(event.point) ?: return@collect
                            monitoringController.updateDistance(progress.totalMeters)

                            progress.completedKilometers.forEach { kilometer ->
                                announceAudioAlert(SessionAudioAlert.DistanceMarker(kilometer))
                            }

                            updateForegroundNotification(
                                contentText = notificationContentText(
                                    monitoringState = monitoringController.state.value,
                                    threshold = threshold,
                                ),
                                threshold = threshold,
                            )
                        }

                        GpsTrackingEvent.ProviderDisabled -> {
                            monitoringController.disableDistanceTracking()
                            updateForegroundNotification(
                                contentText = notificationContentText(
                                    monitoringState = monitoringController.state.value,
                                    threshold = threshold,
                                ),
                                threshold = threshold,
                            )
                        }
                    }
                }
            }.onFailure {
                monitoringController.disableDistanceTracking()
                updateForegroundNotification(
                    contentText = notificationContentText(
                        monitoringState = monitoringController.state.value,
                        threshold = threshold,
                    ),
                    threshold = threshold,
                )
            }
        }
    }

    private fun startForegroundWithState(contentText: String, threshold: Int) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(contentText = contentText, threshold = threshold),
            foregroundServiceType(),
        )
    }

    private fun updateForegroundNotification(contentText: String, threshold: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(contentText = contentText, threshold = threshold),
        )
    }

    private fun foregroundServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return 0
        }

        var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (gpsLocationTracker.canTrackDistance()) {
            type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return type
    }

    private fun notificationContentText(
        monitoringState: MonitoringSessionState,
        threshold: Int,
    ): String = when (monitoringState.connectionState) {
        ConnectionState.Monitoring -> {
            val currentHr = monitoringState.currentHr ?: return "Waiting for heart-rate data | limit $threshold bpm"
            val stats = buildList {
                monitoringState.averageHr?.let { add("avg: $it bpm") }
                monitoringState.distanceMeters?.let { add("dist: ${formatKilometers(it)} km") }
            }
            val suffix = stats.takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty()
            "HR $currentHr bpm$suffix | limit $threshold bpm"
        }

        ConnectionState.Disconnected -> monitoringState.errorMessage ?: "Sensor disconnected."
        else -> "Waiting for heart-rate data | limit $threshold bpm"
    }

    private fun buildNotification(contentText: String, threshold: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MonitoringService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\nThreshold: $threshold bpm")
            )
            .build()
    }

    private fun formatKilometers(distanceMeters: Double): String =
        String.format(java.util.Locale.US, "%.2f", distanceMeters / 1_000.0)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1001

        private const val MIN_SESSION_DURATION_SECONDS = 10

        private const val ACTION_START = "com.x.hrbeep.action.START"
        private const val ACTION_STOP = "com.x.hrbeep.action.STOP"
        private const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        private const val EXTRA_DEVICE_NAME = "extra_device_name"
        private const val EXTRA_THRESHOLD = "extra_threshold"
        private const val EXTRA_LOWER_BOUND = "extra_lower_bound"
        private const val EXTRA_SOUND_INTENSITY = "extra_sound_intensity"

        fun startIntent(
            context: Context,
            deviceAddress: String,
            deviceName: String,
            threshold: Int,
            lowerBound: Int? = null,
            soundIntensity: Int,
        ): Intent = Intent(context, MonitoringService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(EXTRA_DEVICE_NAME, deviceName)
            putExtra(EXTRA_THRESHOLD, threshold)
            putExtra(EXTRA_LOWER_BOUND, lowerBound ?: 0)
            putExtra(EXTRA_SOUND_INTENSITY, soundIntensity)
        }

        fun stopIntent(context: Context): Intent = Intent(context, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
