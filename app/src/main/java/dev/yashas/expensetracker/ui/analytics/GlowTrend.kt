package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashas.expensetracker.data.repo.DayPointUi
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.theme.SeriesAmber
import java.time.LocalDate

/**
 * C2 v3 — pannable, tappable glow trend over a densified daily series (repo caps the
 * window at 60 days; 30 visible at a time). Smoothed curve, glow underlay, gradient
 * fill, dashed average, breathing live dot on the newest day, x-axis date ticks that
 * pan with the data, and tap-a-day info (amount + vs-average multiple).
 */
@Composable
fun GlowTrend(
    points: List<DayPointUi>,
    modifier: Modifier = Modifier,
) {
    val visible = VISIBLE_DAYS
    var pan by remember { mutableFloatStateOf(0f) } // days panned into the past, 0 = newest right
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableIntStateOf(-1) }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseT by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulseT",
    )

    val maxPan = (points.size - visible).coerceAtLeast(0)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val avgColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    }
    LaunchedEffect(labelColor) { labelPaint.color = labelColor.toArgb() }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(points, maxPan) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = { dragAccum = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        val stepX = size.width / visible
                        dragAccum += dragAmount
                        val days = dragAccum / stepX
                        if (kotlin.math.abs(days) >= 0.02f) {
                            dragAccum = 0f
                            pan = (pan - days).coerceIn(0f, maxPan.toFloat())
                        }
                    }
                }
                .pointerInput(points, maxPan) {
                    detectTapGestures { offset ->
                        val stepX = size.width / visible
                        val rightFloat = points.size - 1 - pan
                        val gi = ((size.width - offset.x) / stepX).toInt()
                        val abs = (rightFloat.toInt() - gi).coerceIn(0, points.size - 1)
                        selected = if (selected == abs) -1 else abs
                    }
                },
        ) {
            if (points.size < 2) return@Canvas
            val maxV = points.maxOf { it.rupees }.coerceAtLeast(1f)
            val avg = points.map { it.rupees }.average().toFloat()
            val stepX = size.width / visible
            val chartH = size.height * 0.78f
            val baseY = size.height * 0.90f
            labelPaint.textSize = 9.sp.toPx()

            fun xFor(abs: Int): Float = size.width - stepX / 2f - (points.size - 1 - abs - pan) * stepX
            fun yFor(abs: Int): Float = baseY - (points[abs].rupees / maxV) * chartH * progress.value

            // x-axis baseline + date ticks (labels pan with the data)
            drawLine(axisColor.copy(alpha = 0.4f), Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.2f)
            val today = LocalDate.now()
            for (abs in 0 until points.size) {
                val x = xFor(abs)
                if (x < -stepX || x > size.width + stepX) continue
                val d = LocalDate.ofEpochDay(points[abs].day)
                val isTick = d.dayOfWeek.value == 1 || abs == points.size - 1 // Mondays + today
                if (isTick) {
                    drawLine(axisColor.copy(alpha = 0.35f), Offset(x, baseY), Offset(x, baseY + 5f), strokeWidth = 1.2f)
                    val mon = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                    val label = if (d.year == today.year) "${d.dayOfMonth} $mon" else "${d.dayOfMonth} $mon ${d.year}"
                    drawContext.canvas.nativeCanvas.drawText(label, x, baseY + 15.sp.toPx(), labelPaint)
                }
            }

            // visible window only, Catmull-Rom smoothed
            val lastVis = (points.size - 1 - pan).toInt().coerceIn(0, points.size - 1)
            val from = (lastVis - visible - 1).coerceAtLeast(0)
            val to = (lastVis + 1).coerceAtMost(points.size - 1)
            if (to - from < 1) return@Canvas

            fun pt(abs: Int) = Offset(xFor(abs), yFor(abs))

            val line = Path()
            val p0 = pt(from)
            line.moveTo(p0.x, p0.y)
            for (i in from until to) {
                val p1 = pt(i)
                val p2 = pt(i + 1)
                val m1 = pt((i - 1).coerceAtLeast(from))
                val m2 = pt((i + 2).coerceAtMost(to))
                val c1 = Offset(p1.x + (p2.x - m1.x) / 6f, p1.y + (p2.y - m1.y) / 6f)
                val c2 = Offset(p2.x - (m2.x - p1.x) / 6f, p2.y - (m2.y - p1.y) / 6f)
                line.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
            }

            // glow underlay + line + gradient fill
            drawPath(line, SeriesAmber.copy(alpha = 0.16f), style = Stroke(width = 16f))
            drawPath(line, SeriesAmber, style = Stroke(width = 4.5f))
            val fill = Path().apply {
                addPath(line)
                lineTo(xFor(to), baseY)
                lineTo(xFor(from), baseY)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(SeriesAmber.copy(alpha = 0.26f), Color.Transparent),
                    startY = 0f,
                    endY = baseY,
                ),
            )

            // dashed average across the whole width
            val avgY = baseY - (avg / maxV) * chartH * progress.value
            drawLine(
                color = avgColor,
                start = Offset(0f, avgY),
                end = Offset(size.width, avgY),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
            )

            // selected day marker
            if (selected >= 0) {
                val sx = xFor(selected)
                if (sx > -stepX && sx < size.width + stepX) {
                    val sy = yFor(selected)
                    drawCircle(SeriesAmber.copy(alpha = 0.25f), radius = 14f, center = Offset(sx, sy))
                    drawCircle(SeriesAmber, radius = 6.5f, center = Offset(sx, sy))
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.6f, center = Offset(sx, sy))
                }
            }

            // breathing live dot at the newest day (only when on screen)
            val liveX = xFor(points.size - 1)
            if (liveX > -stepX && liveX < size.width + stepX) {
                val live = Offset(liveX, yFor(points.size - 1))
                drawCircle(SeriesAmber.copy(alpha = 0.22f * (1f - pulseT)), radius = 8f + 14f * pulseT, center = live)
                drawCircle(SeriesAmber, radius = 6f, center = live)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.4f, center = live)
            }
        }

        // tap info line
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(120))) },
            label = "trendinfo",
        ) { sel ->
            if (sel in points.indices && points[sel].rupees >= 0f) {
                val p = points[sel]
                val d = LocalDate.ofEpochDay(p.day)
                val mon = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                val avgF = avgOf(points)
                val mult = if (avgF > 0f) p.rupees / avgF else 0f
                Text(
                    "${d.dayOfMonth} $mon  ·  ${MoneyFormat.formatPaise((p.rupees * 100).toLong())}  ·  ${"%.1f".format(mult)}× daily average",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (maxPan > 0) "Tap a day for details · drag left for older days" else "Tap a day for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private const val VISIBLE_DAYS = 30

private fun avgOf(points: List<DayPointUi>): Float =
    if (points.isEmpty()) 0f else points.map { it.rupees }.average().toFloat()
