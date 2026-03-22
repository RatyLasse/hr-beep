package com.x.heartbeep.ui.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.x.heartbeep.ui.NeonCyan
import com.x.heartbeep.ui.NeonGreen
import com.x.heartbeep.ui.NeonRed

/**
 * Returns the visible Y-axis range (min, max) used by the graph after trimming
 * the top/bottom 5% outliers. Returns null if fewer than 2 data points.
 */
internal fun hrGraphVisibleRange(hrHistory: List<Int>): Pair<Int, Int>? {
    if (hrHistory.size < 2) return null
    val sorted = hrHistory.sorted()
    val lowerIdx = (sorted.size * 0.05f).toInt().coerceIn(0, sorted.lastIndex)
    val upperIdx = (sorted.size * 0.95f).toInt().coerceIn(0, sorted.lastIndex)
    val minHr = (sorted[lowerIdx] - 5).coerceAtLeast(30)
    val maxHr = (sorted[upperIdx] + 5).coerceAtMost(300)
    return minHr to maxHr
}

@Composable
internal fun HrGraph(
    hrHistory: List<Int>,
    isMonitoring: Boolean,
    upperBound: Int?,
    lowerBound: Int?,
    modifier: Modifier = Modifier,
    showCenterMask: Boolean = true,
    lineWidth: Float? = null,
) {
    val idleLineColor = NeonCyan.copy(alpha = 0.6f)
    Canvas(modifier = modifier) {
        val n = hrHistory.size
        if (n < 2) return@Canvas

        val (minHr, maxHr) = hrGraphVisibleRange(hrHistory) ?: return@Canvas
        val range = (maxHr - minHr).toFloat().coerceAtLeast(10f)

        fun hrToY(hr: Int): Float = size.height - (hr - minHr) / range * size.height
        fun indexToX(i: Int): Float = i.toFloat() / (n - 1) * size.width

        val xs = FloatArray(n) { indexToX(it) }
        val ys = FloatArray(n) { hrToY(hrHistory[it]) }
        val strokeWidth = lineWidth ?: 3.dp.toPx()
        val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())

        var currentColor: Color? = null
        var currentPath: Path? = null

        fun flushPath() {
            val p = currentPath ?: return
            val c = currentColor ?: return
            drawPath(path = p, color = c, style = strokeStyle)
            currentPath = null
            currentColor = null
        }

        for (i in 0 until n - 1) {
            val color = when {
                !isMonitoring -> idleLineColor
                isHrOutOfBounds(hrHistory[i], upperBound, lowerBound) ||
                    isHrOutOfBounds(hrHistory[i + 1], upperBound, lowerBound) -> NeonRed
                else -> NeonGreen
            }

            if (color != currentColor) {
                flushPath()
                currentColor = color
                currentPath = Path().apply { moveTo(xs[i], ys[i]) }
            }

            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 2).coerceAtMost(n - 1)
            val cp1x = xs[i] + (xs[i + 1] - xs[prev]) / 6f
            val cp1y = ys[i] + (ys[i + 1] - ys[prev]) / 6f
            val cp2x = xs[i + 1] - (xs[next] - xs[i]) / 6f
            val cp2y = ys[i + 1] - (ys[next] - ys[i]) / 6f
            currentPath!!.cubicTo(cp1x, cp1y, cp2x, cp2y, xs[i + 1], ys[i + 1])
        }

        flushPath()

        if (showCenterMask) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.15f to Color.Transparent,
                    0.38f to Color.Black.copy(alpha = 0.80f),
                    0.62f to Color.Black.copy(alpha = 0.80f),
                    0.85f to Color.Transparent,
                ),
                blendMode = BlendMode.DstOut,
            )
        }

        drawContext.canvas.restore()
    }
}

internal fun isHrOutOfBounds(hr: Int, upperBound: Int?, lowerBound: Int?): Boolean =
    (upperBound != null && hr > upperBound) || (lowerBound != null && hr < lowerBound)
