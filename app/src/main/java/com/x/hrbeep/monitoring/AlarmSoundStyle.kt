package com.x.hrbeep.monitoring

import android.media.ToneGenerator

enum class AlarmSoundStyle(
    val storageValue: String,
    val displayName: String,
    val toneCode: Int,
    val durationMs: Int,
    val volume: Int,
    val description: String,
) {
    Gentle(
        storageValue = "gentle",
        displayName = "Gentle",
        toneCode = ToneGenerator.TONE_PROP_BEEP2,
        durationMs = 140,
        volume = 72,
        description = "Short and softer. Good default for repeated alerts.",
    ),
    Bright(
        storageValue = "bright",
        displayName = "Bright",
        toneCode = ToneGenerator.TONE_PROP_BEEP,
        durationMs = 120,
        volume = 85,
        description = "Clearer and lighter than the previous alarm.",
    ),
    Chime(
        storageValue = "chime",
        displayName = "Chime",
        toneCode = ToneGenerator.TONE_SUP_CONFIRM,
        durationMs = 180,
        volume = 80,
        description = "More rounded and musical.",
    ),
    Pulse(
        storageValue = "pulse",
        displayName = "Pulse",
        toneCode = ToneGenerator.TONE_CDMA_PIP,
        durationMs = 110,
        volume = 90,
        description = "Sharper and more rhythmic for outdoor runs.",
    ),
    ;

    companion object {
        val default: AlarmSoundStyle = Gentle

        fun fromStorageValue(value: String?): AlarmSoundStyle {
            return entries.firstOrNull { it.storageValue == value } ?: default
        }
    }
}
