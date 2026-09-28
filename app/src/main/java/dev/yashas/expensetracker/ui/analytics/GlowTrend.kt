package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.ui.theme.SeriesAmber

/**
 * C2 v2 — smoothed (Catmull-Rom) daily spending curve: soft glow underlay, gradient
 * fill to the floor, dashed average baseline, and a breathing "today" dot at the end.
 * The dot pulse is the view's single ambient motion.
 */
@Composable
fun GlowTrend(
    valuesRupees: List<Float>,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(valuesRupees) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
            pulse.snapTo(0f)
        }
    }
    val avgColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        if (valuesRupees.size < 2) return@Canvas
        val maxV = valuesRupees.max().coerceAtLeast(1f)
        val stepX = size.width / (valuesRupees.size - 1)
        val chartH = size.height * 0.82f
        val baseY = size.height * 0.92f

        fun pt(i: Int): Offset {
            val x = i * stepX
            val y = baseY - (valuesRupees[i] / maxV) * chartH * progress.value
            return Offset(x, y)
        }

        // Catmull-Rom → cubic Bezier smooth path
        val line = Path()
        val p0 = pt(0)
        line.moveTo(p0.x, p0.y)
        for (i in 0 until valuesRupees.size - 1) {
            val p1 = pt(i)
            val p2 = pt(i + 1)
            val m1 = pt((i - 1).coerceAtLeast(0))
            val m2 = pt((i + 2).coerceAtMost(valuesRupees.size - 1))
            val c1 = Offset(p1.x + (p2.x - m1.x) / 6f, p1.y + (p2.y - m1.y) / 6f)
            val c2 = Offset(p2.x - (m2.x - p1.x) / 6f, p2.y - (m2.y - p1.y) / 6f)
            line.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }

        // glow underlay: same path, wide + translucent
        drawPath(line, SeriesAmber.copy(alpha = 0.16f), style = Stroke(width = 16f))
        // main line
        drawPath(line, SeriesAmber, style = Stroke(width = 4.5f))

        // gradient fill under the curve
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, baseY)
            lineTo(0f, baseY)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(SeriesAmber.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = baseY,
            ),
        )

        // dashed average baseline
        val avg = valuesRupees.average().toFloat()
        val avgY = baseY - (avg / maxV) * chartH * progress.value
        drawLine(
            color = avgColor,
            start = Offset(0f, avgY),
            end = Offset(size.width, avgY),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
        )

        // breathing live dot at the latest point
        val last = pt(valuesRupees.size - 1)
        drawCircle(SeriesAmber.copy(alpha = 0.20f + 0.25f * (1f - pulse.value)), radius = 8f + 14f * pulse.value, center = last)
        drawCircle(SeriesAmber, radius = 6f, center = last)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.4f, center = last)
    }
}
