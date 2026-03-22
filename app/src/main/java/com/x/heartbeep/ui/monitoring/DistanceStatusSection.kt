package com.x.heartbeep.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.x.heartbeep.ui.NeonCyan
import com.x.heartbeep.ui.NeonOrange

@Composable
internal fun DistanceStatusSection(
    isMonitoring: Boolean,
    isDistanceTrackingEnabled: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onGrantLocationPermission: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                !hasLocationPermission -> "GPS distance tracking is off."
                !gpsEnabled -> "Turn GPS on for distance tracking."
                isMonitoring && !isDistanceTrackingEnabled -> "GPS unavailable."
                isMonitoring -> "Distance tracking active."
                else -> "Distance tracking auto-starts with GPS."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!hasLocationPermission || !gpsEnabled) {
                NeonOrange.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (!hasLocationPermission) {
            TextButton(onClick = onGrantLocationPermission) {
                Text("Allow", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
