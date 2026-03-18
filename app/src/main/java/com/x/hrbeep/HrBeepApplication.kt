package com.x.hrbeep

import android.app.Application
import com.x.hrbeep.data.BleHeartRateRepository
import com.x.hrbeep.data.SessionDatabase
import com.x.hrbeep.data.SessionHistoryRepository
import com.x.hrbeep.data.ThresholdRepository
import com.x.hrbeep.monitoring.AlarmPlayer
import com.x.hrbeep.monitoring.GpsLocationTracker
import com.x.hrbeep.monitoring.HeartRateConnectionManager
import com.x.hrbeep.monitoring.MonitoringController

class HrBeepApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val monitoringController = MonitoringController()
        val bleHeartRateRepository = BleHeartRateRepository(this)
        val sessionDb = SessionDatabase.getInstance(this)
        appContainer = AppContainer(
            thresholdRepository = ThresholdRepository(this),
            bleHeartRateRepository = bleHeartRateRepository,
            sessionHistoryRepository = SessionHistoryRepository(sessionDb.sessionDao()),
            alarmPlayer = AlarmPlayer(this),
            gpsLocationTracker = GpsLocationTracker(this),
            monitoringController = monitoringController,
            heartRateConnectionManager = HeartRateConnectionManager(
                bleHeartRateRepository = bleHeartRateRepository,
                monitoringController = monitoringController,
            ),
        )
    }
}

data class AppContainer(
    val thresholdRepository: ThresholdRepository,
    val bleHeartRateRepository: BleHeartRateRepository,
    val sessionHistoryRepository: SessionHistoryRepository,
    val alarmPlayer: AlarmPlayer,
    val gpsLocationTracker: GpsLocationTracker,
    val monitoringController: MonitoringController,
    val heartRateConnectionManager: HeartRateConnectionManager,
)
