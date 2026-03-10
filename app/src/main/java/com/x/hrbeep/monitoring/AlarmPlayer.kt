package com.x.hrbeep.monitoring

import android.media.AudioManager
import android.media.ToneGenerator

class AlarmPlayer {
    private val generators = mutableMapOf<AlarmSoundStyle, ToneGenerator>()

    @Synchronized
    fun beep(style: AlarmSoundStyle) {
        val generator = generators.getOrPut(style) {
            ToneGenerator(AudioManager.STREAM_MUSIC, style.volume)
        }
        generator.startTone(style.toneCode, style.durationMs)
    }

    @Synchronized
    fun release() {
        generators.values.forEach(ToneGenerator::release)
        generators.clear()
    }
}
