package com.x.hrbeep.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.x.hrbeep.HrBeepApplication
import com.x.hrbeep.MainActivity
import com.x.hrbeep.R
import com.x.hrbeep.data.BleHeartRateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoringService : Service() {
    private lateinit var bleHeartRateRepository: BleHeartRateRepository
    private lateinit var monitoringController: MonitoringController
    private lateinit var alarmPlayer: AlarmPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as HrBeepApplication).appContainer
        bleHeartRateRepository = container.bleHeartRateRepository
        monitoringController = container.monitoringController
        alarmPlayer = container.alarmPlayer
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                val threshold = intent.getIntExtra(EXTRA_THRESHOLD, -1)
                val soundIntensity = intent.getIntExtra(EXTRA_SOUND_INTENSITY, 80)
                val soundStyle = AlarmSoundStyle.fromStorageValue(
                    intent.getStringExtra(EXTRA_SOUND_STYLE)
                )
                if (deviceAddress.isNullOrBlank() || threshold <= 0) {
                    stopMonitoring("Missing device or threshold.")
                } else {
                    startMonitoring(deviceAddress, deviceName, threshold, soundStyle, soundIntensity)
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startMonitoring(
        deviceAddress: String,
        deviceName: String?,
        threshold: Int,
        soundStyle: AlarmSoundStyle,
        soundIntensity: Int,
    ) {
        monitoringJob?.cancel()
        alarmPlayer.setPersistentDucking(false)

        monitoringController.update {
            it.copy(
                isMonitoring = true,
                connectionState = ConnectionState.Connecting,
                currentHr = null,
                threshold = threshold,
                deviceName = deviceName ?: deviceAddress,
                deviceAddress = deviceAddress,
                errorMessage = null,
            )
        }

        startForegroundWithState(
            contentText = getString(R.string.notification_connecting),
            threshold = threshold,
        )

        val alarmDecider = AlarmDecider()
        monitoringJob = serviceScope.launch {
            bleHeartRateRepository.observeHeartRate(deviceAddress)
                .catch { throwable ->
                    stopMonitoring(throwable.message ?: "Monitoring failed.")
                }
                .collect { sample ->
                    val isAboveThreshold = sample.bpm > threshold
                    alarmPlayer.setPersistentDucking(isAboveThreshold)

                    monitoringController.update { state ->
                        state.copy(
                            isMonitoring = true,
                            connectionState = ConnectionState.Monitoring,
                            currentHr = sample.bpm,
                            threshold = threshold,
                            errorMessage = null,
                        )
                    }

                    val rrIntervalMs = sample.rrIntervalsMs.lastOrNull()
                    if (alarmDecider.shouldBeep(
                            currentHr = sample.bpm,
                            threshold = threshold,
                            nowElapsedMs = sample.receivedAtElapsedMs,
                            rrIntervalMs = rrIntervalMs,
                        )
                    ) {
                        withContext(Dispatchers.Default) {
                            alarmPlayer.beep(soundStyle, soundIntensity)
                        }
                    }

                    updateForegroundNotification(
                        contentText = "HR ${sample.bpm} bpm | limit $threshold bpm",
                        threshold = threshold,
                    )
                }
        }
    }

    private fun stopMonitoring(errorMessage: String? = null) {
        monitoringJob?.cancel()
        monitoringJob = null
        alarmPlayer.setPersistentDucking(false)

        monitoringController.update {
            if (errorMessage == null) {
                MonitoringSessionState()
            } else {
                it.copy(
                    isMonitoring = false,
                    connectionState = ConnectionState.Error,
                    errorMessage = errorMessage,
                )
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundWithState(contentText: String, threshold: Int) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(contentText = contentText, threshold = threshold),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            },
        )
    }

    private fun updateForegroundNotification(contentText: String, threshold: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(contentText = contentText, threshold = threshold),
        )
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

        private const val ACTION_START = "com.x.hrbeep.action.START"
        private const val ACTION_STOP = "com.x.hrbeep.action.STOP"
        private const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        private const val EXTRA_DEVICE_NAME = "extra_device_name"
        private const val EXTRA_THRESHOLD = "extra_threshold"
        private const val EXTRA_SOUND_STYLE = "extra_sound_style"
        private const val EXTRA_SOUND_INTENSITY = "extra_sound_intensity"

        fun startIntent(
            context: Context,
            deviceAddress: String,
            deviceName: String,
            threshold: Int,
            soundStyle: AlarmSoundStyle,
            soundIntensity: Int,
        ): Intent = Intent(context, MonitoringService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(EXTRA_DEVICE_NAME, deviceName)
            putExtra(EXTRA_THRESHOLD, threshold)
            putExtra(EXTRA_SOUND_STYLE, soundStyle.storageValue)
            putExtra(EXTRA_SOUND_INTENSITY, soundIntensity)
        }

        fun stopIntent(context: Context): Intent = Intent(context, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
