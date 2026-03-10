package com.x.hrbeep.monitoring

class AlarmDecider(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private var lastAlarmAtMs: Long? = null

    fun shouldBeep(currentHr: Int, threshold: Int, nowElapsedMs: Long): Boolean {
        if (currentHr <= threshold) {
            lastAlarmAtMs = null
            return false
        }

        val previous = lastAlarmAtMs
        val shouldAlert = previous == null || nowElapsedMs - previous >= cooldownMs
        if (shouldAlert) {
            lastAlarmAtMs = nowElapsedMs
        }
        return shouldAlert
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 1_500L
    }
}

