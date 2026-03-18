package com.x.hrbeep.data

data class HeartRateMonitorUpdate(
    val heartRateSample: HeartRateSample? = null,
    val batteryLevelPercent: Int? = null,
)
