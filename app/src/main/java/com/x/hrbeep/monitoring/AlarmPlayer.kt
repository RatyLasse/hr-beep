package com.x.hrbeep.monitoring

import android.media.AudioManager
import android.media.ToneGenerator

class AlarmPlayer {
    private val generators = mutableMapOf<AlarmSoundStyle, ToneGenerator>()

    @Synchronized
    fun beep(style: AlarmSoundStyle, intensity: Int) {
        val clampedIntensity = intensity.coerceIn(0, 100)
        val effectiveVolume = (style.volume * (clampedIntensity / 100f))
            .toInt()
            .coerceIn(0, 100)
        val generator = generators.remove(style)?.also(ToneGenerator::release)
            ?: ToneGenerator(AudioManager.STREAM_MUSIC, effectiveVolume)
        generators[style] = generator
        generator.startTone(style.toneCode, style.durationMs)
    }

    @Synchronized
    fun release() {
        generators.values.forEach(ToneGenerator::release)
        generators.clear()
    }
}
