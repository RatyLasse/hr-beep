package com.x.hrbeep.monitoring

import android.media.ToneGenerator

enum class AlarmSoundStyle(
    val storageValue: String,
    val displayName: String,
    val toneCode: Int,
    val durationMs: Int,
    val volume: Int,
) {
    Bright(
        storageValue = "bright",
        displayName = "Bright",
        toneCode = ToneGenerator.TONE_PROP_BEEP,
        durationMs = 120,
        volume = 85,
    ),
    Pulse(
        storageValue = "pulse",
        displayName = "Pulse",
        toneCode = ToneGenerator.TONE_CDMA_PIP,
        durationMs = 110,
        volume = 90,
    ),
    ;

    companion object {
        val default: AlarmSoundStyle = Bright

        fun fromStorageValue(value: String?): AlarmSoundStyle {
            return entries.firstOrNull { it.storageValue == value } ?: default
        }
    }
}
