package com.x.heartbeep.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x.heartbeep.data.SessionRecord
import com.x.heartbeep.formatDuration
import com.x.heartbeep.formatKilometers
import com.x.heartbeep.formatPace
import com.x.heartbeep.formatSessionDate
import com.x.heartbeep.ui.CardBackground
import com.x.heartbeep.ui.NeonRed
import com.x.heartbeep.ui.SubCardBackground
import com.x.heartbeep.ui.monitoring.HrGraph
import com.x.heartbeep.ui.monitoring.hrGraphVisibleRange

@Composable
internal fun HistoryTab(
    modifier: Modifier = Modifier,
    sessions: List<SessionRecord>,
    onDelete: (Long) -> Unit,
    onExport: () -> Unit,
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = "No sessions recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Export sessions as CSV",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(sessions, key = { it.id }) { session ->
                SessionCard(session = session, onDelete = { onDelete(session.id) })
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionRecord, onDelete: () -> Unit) {
    val cardShape = RoundedCornerShape(14.dp)
    val hrHistoryList = remember(session.hrHistory) { session.hrHistoryList() }
    val visibleRange = remember(hrHistoryList) { hrGraphVisibleRange(hrHistoryList) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header: date + delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatSessionDate(session.startTimeMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete session",
                    tint = NeonRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Stats grid: 2x2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryStatBox(
                label = "Duration",
                value = formatDuration(session.durationSeconds),
                modifier = Modifier.weight(1f),
            )
            HistoryStatBox(
                label = "Avg BPM",
                value = session.averageHr?.let { "$it" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryStatBox(
                label = "Distance",
                value = session.distanceMeters?.let { "${formatKilometers(it)} km" } ?: "--",
                modifier = Modifier.weight(1f),
            )
            HistoryStatBox(
                label = "Pace",
                value = session.paceSecondsPerKm?.let { "${formatPace(it)} min/km" } ?: "--",
                modifier = Modifier.weight(1f),
            )
        }

        // Full-width HR graph
        if (hrHistoryList.size >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                HrGraph(
                    hrHistory = hrHistoryList,
                    isMonitoring = true,
                    upperBound = session.upperBound,
                    lowerBound = session.lowerBound,
                    showCenterMask = false,
                    modifier = Modifier.fillMaxSize(),
                    lineWidth = with(LocalDensity.current) { 1.5.dp.toPx() },
                )
                if (visibleRange != null) {
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "${visibleRange.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
                        Text(
                            text = "${visibleRange.first}",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(SubCardBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
