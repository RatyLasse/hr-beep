package com.x.hrbeep.data

data class HeartRateSample(
    val bpm: Int,
    val rrIntervalsMs: List<Float>,
    val contactDetected: Boolean?,
    val receivedAtElapsedMs: Long,
)

