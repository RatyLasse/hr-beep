package com.x.heartbeep.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x.heartbeep.formatDurationLong
import com.x.heartbeep.formatKilometers
import com.x.heartbeep.monitoring.MonitoringSessionState
import com.x.heartbeep.ui.CardBackground
import kotlinx.coroutines.delay

@Composable
internal fun SessionStatsRow(monitoringState: MonitoringSessionState) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val startTime = monitoringState.monitoringStartTimeMs
    val isMonitoring = monitoringState.isMonitoring

    LaunchedEffect(startTime, isMonitoring) {
        if (startTime == null) {
            elapsedSeconds = monitoringState.finalDurationSeconds ?: 0L
            return@LaunchedEffect
        }
        if (!isMonitoring) {
            elapsedSeconds = monitoringState.finalDurationSeconds
                ?: ((System.currentTimeMillis() - startTime) / 1000)
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            delay(1000L)
        }
    }

    val stats = listOf(
        "Average HR" to (monitoringState.averageHr?.let { "$it bpm" } ?: "--"),
        "Duration" to (if (startTime != null || monitoringState.finalDurationSeconds != null) formatDurationLong(elapsedSeconds) else "--"),
        "Distance" to (monitoringState.distanceMeters?.let { "${formatKilometers(it)} km" } ?: "--"),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
