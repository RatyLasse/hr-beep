package com.x.hrbeep.monitoring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmPlayer(
    context: Context,
) {
    private companion object {
        const val EXTRA_DUCK_MS = 350L
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val generators = mutableMapOf<AlarmSoundStyle, ToneGenerator>()
    private val generatorVolumes = mutableMapOf<AlarmSoundStyle, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var abandonFocusJob: Job? = null
    private var persistentDucking = false

    private val audioFocusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
        } else {
            null
        }

    @Synchronized
    fun beep(style: AlarmSoundStyle, intensity: Int) {
        val clampedIntensity = intensity.coerceIn(0, 100)
        val effectiveVolume = (style.volume * (clampedIntensity / 100f))
            .toInt()
            .coerceIn(0, 100)
        if (!persistentDucking) {
            requestTransientAudioFocus(style.durationMs)
        }

        val generator = generators[style]
        val activeGenerator = if (generator != null && generatorVolumes[style] == effectiveVolume) {
            generator
        } else {
            generator?.release()
            ToneGenerator(AudioManager.STREAM_MUSIC, effectiveVolume).also {
                generators[style] = it
                generatorVolumes[style] = effectiveVolume
            }
        }
        activeGenerator.startTone(style.toneCode, style.durationMs)
    }

    @Synchronized
    fun setPersistentDucking(enabled: Boolean) {
        if (persistentDucking == enabled) {
            return
        }

        persistentDucking = enabled
        abandonFocusJob?.cancel()
        abandonFocusJob = null

        if (enabled) {
            requestAudioFocus()
        } else {
            abandonAudioFocus()
        }
    }

    @Synchronized
    fun release() {
        persistentDucking = false
        abandonFocusJob?.cancel()
        abandonAudioFocus()
        generators.values.forEach(ToneGenerator::release)
        generators.clear()
        generatorVolumes.clear()
    }

    private fun requestTransientAudioFocus(durationMs: Int) {
        requestAudioFocus()

        if (persistentDucking) {
            return
        }

        abandonFocusJob?.cancel()
        abandonFocusJob = scope.launch {
            delay(durationMs.toLong() + EXTRA_DUCK_MS)
            if (!persistentDucking) {
                abandonAudioFocus()
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::requestAudioFocus)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
