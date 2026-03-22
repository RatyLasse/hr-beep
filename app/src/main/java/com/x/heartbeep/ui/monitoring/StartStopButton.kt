package com.x.heartbeep.ui.monitoring

import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x.heartbeep.ui.NeonCyan
import com.x.heartbeep.ui.NeonRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_DURATION_MS = 2000
private const val CONFIRM_HOLD_MS = 200L

@Composable
internal fun StartStopButton(
    isMonitoring: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (isMonitoring) {
        val stopShape = RoundedCornerShape(14.dp)
        val progress = remember { Animatable(0f) }
        val fillAlpha = remember { Animatable(0.2f) }
        val scope = rememberCoroutineScope()
        val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(stopShape)
                .border(1.5.dp, NeonRed.copy(alpha = 0.7f), stopShape)
                .pointerInput(onStop) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var holdCompleted = false
                        val animJob = scope.launch {
                            progress.snapTo(0f)
                            fillAlpha.snapTo(0.2f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = HOLD_DURATION_MS,
                                    easing = LinearEasing,
                                ),
                            )
                            holdCompleted = true
                            // Confirmation: haptic tick + full-opacity flash
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .build(),
                            )
                            fillAlpha.animateTo(
                                targetValue = 0.5f,
                                animationSpec = tween(durationMillis = 80),
                            )
                            delay(CONFIRM_HOLD_MS)
                            onStop()
                        }
                        waitForUpOrCancellation()
                        if (!holdCompleted && animJob.isActive) {
                            animJob.cancel()
                            scope.launch {
                                progress.snapTo(0f)
                                fillAlpha.snapTo(0.2f)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Red fill layer (animates left to right)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.value)
                    .background(NeonRed.copy(alpha = fillAlpha.value)),
            )
            // Button text centered
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u25A0  Stop",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = NeonRed,
                )
            }
        }
    } else {
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = NeonCyan.copy(alpha = 0.25f),
                disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                "Start",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}
