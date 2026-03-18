package com.x.hrbeep.monitoring

import kotlin.math.PI
import kotlin.math.sin

internal data class AlarmToneSpec(
    val frequencyHz: Double,
    val durationMs: Int,
    val tailSilenceMs: Int,
    val sampleRateHz: Int,
    val attackMs: Int,
    val releaseMs: Int,
    val amplitude: Double,
)

internal object AlarmTone {
    private const val SAMPLE_RATE_HZ = 24_000
    private const val DURATION_MS = 110
    private const val TAIL_SILENCE_MS = 12
    private const val ATTACK_MS = 6
    private const val RELEASE_MS = 30
    private const val AMPLITUDE = 0.35
    private const val UPPER_FREQUENCY_HZ = 600.0
    private const val LOWER_FREQUENCY_HZ = 400.0

    fun specFor(trigger: AlarmTrigger): AlarmToneSpec = AlarmToneSpec(
        frequencyHz = when (trigger) {
            AlarmTrigger.AboveUpperBound -> UPPER_FREQUENCY_HZ
            AlarmTrigger.BelowLowerBound -> LOWER_FREQUENCY_HZ
        },
        durationMs = DURATION_MS,
        tailSilenceMs = TAIL_SILENCE_MS,
        sampleRateHz = SAMPLE_RATE_HZ,
        attackMs = ATTACK_MS,
        releaseMs = RELEASE_MS,
        amplitude = AMPLITUDE,
    )

    fun samplesFor(trigger: AlarmTrigger): ShortArray = buildSamples(specFor(trigger))

    fun buildSamples(spec: AlarmToneSpec): ShortArray {
        val sampleCount = (spec.sampleRateHz * spec.durationMs) / 1_000
        val tailSilenceSamples = ((spec.sampleRateHz * spec.tailSilenceMs) / 1_000).coerceAtLeast(1)
        val toneSampleCount = (sampleCount - tailSilenceSamples).coerceAtLeast(1)
        val attackSamples = ((spec.sampleRateHz * spec.attackMs) / 1_000).coerceAtLeast(1)
        val releaseSamples = ((spec.sampleRateHz * spec.releaseMs) / 1_000)
            .coerceAtLeast(1)
            .coerceAtMost(toneSampleCount)
        val maxAmplitude = Short.MAX_VALUE * spec.amplitude

        return ShortArray(sampleCount) { index ->
            if (index >= toneSampleCount) {
                return@ShortArray 0
            }

            val envelope = when {
                index < attackSamples -> index / attackSamples.toDouble()
                index >= toneSampleCount - releaseSamples ->
                    (toneSampleCount - index - 1).coerceAtLeast(0) / releaseSamples.toDouble()

                else -> 1.0
            }

            val phase = 2.0 * PI * spec.frequencyHz * index / spec.sampleRateHz
            (sin(phase) * envelope * maxAmplitude)
                .toInt()
                .toShort()
        }
    }
}
