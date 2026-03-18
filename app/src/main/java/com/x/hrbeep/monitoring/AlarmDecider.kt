package com.x.hrbeep.monitoring

class AlarmDecider(
    private val minimumIntervalMs: Long = DEFAULT_MINIMUM_INTERVAL_MS,
    private val maximumIntervalMs: Long = DEFAULT_MAXIMUM_INTERVAL_MS,
) {
    private var lastAlarmAtMs: Long? = null

    fun shouldBeep(
        currentHr: Int,
        threshold: Int,
        lowerBound: Int? = null,
        nowElapsedMs: Long,
    ): Boolean {
        val isOutOfRange = currentHr > threshold || (lowerBound != null && currentHr < lowerBound)
        if (!isOutOfRange) {
            lastAlarmAtMs = null
            return false
        }

        val intervalMs = bpmToIntervalMs(currentHr)
            .coerceIn(minimumIntervalMs, maximumIntervalMs)

        val previous = lastAlarmAtMs
        val shouldAlert = previous == null || nowElapsedMs - previous >= intervalMs
        if (shouldAlert) {
            lastAlarmAtMs = nowElapsedMs
        }
        return shouldAlert
    }

    private fun bpmToIntervalMs(currentHr: Int): Long {
        val safeHr = currentHr.coerceAtLeast(1)
        return 60_000L / safeHr
    }

    companion object {
        const val DEFAULT_MINIMUM_INTERVAL_MS = 333L
        const val DEFAULT_MAXIMUM_INTERVAL_MS = 2_000L
    }
}
