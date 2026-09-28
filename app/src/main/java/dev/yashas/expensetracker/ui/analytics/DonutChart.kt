package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.categoryColor
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * C3 v2 — pseudo-3D extruded donut. Stacked layers give depth, the top face carries
 * a vertical light gradient (light source above), and the selected slice lifts off
 * the ring on a spring. Entrance sweeps the ring in once per data change.
 * Tap slice → lift + center morph; tap-away → back to total.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(-1) }
    val total = slices.sumOf { it.amountPaise }.coerceAtLeast(1)

    // entrance sweep 0→1 (re-runs when data changes)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }
    // per-slice lift factor 0..1 on a spring
    val lifts: List<Float> = slices.mapIndexed { i, _ ->
        animateFloatAsState(
            targetValue = if (i == selected) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "lift$i",
        ).value
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val side = minOf(size.width, size.height)
                        val radius = side / 2f
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > radius || dist < radius * 0.4f) {
                            selected = -1
                            return@detectTapGestures
                        }
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble())
                        val rel = ((deg + 90.0) + 360.0) % 360.0
                        var start = 0.0
                        var hit = -1
                        slices.forEachIndexed { i, slice ->
                            val sweep = slice.amountPaise * 360.0 / total
                            if (rel >= start && rel < start + sweep) hit = i
                            start += sweep
                        }
                        selected = if (hit == selected) -1 else hit
                    }
                },
        ) {
            // 3D parameters scale with the canvas
            val strokeW = size.minDimension * 0.13f
            val layers = 10
            val depth = size.minDimension * 0.045f
            val layerDy = depth / layers
            val side = minOf(size.width, size.height) - strokeW - depth
            val cx = size.width / 2f
            val cy = size.height / 2f + depth / 2f
            val topLeft = Offset(cx - side / 2f, cy - side / 2f)

            fun shade(base: Color, depthFactor: Float): Color {
                // deepen toward the bottom layers; top face gets the lightest pass below
                return if (depthFactor >= 1f) {
                    base.copy(
                        red = base.red + (1f - base.red) * 0.35f,
                        green = base.green + (1f - base.green) * 0.35f,
                        blue = base.blue + (1f - base.blue) * 0.35f,
                    )
                } else {
                    val k = 0.55f + 0.45f * depthFactor // 0.55 at bottom → 1.0 at top
                    Color(base.red * k, base.green * k, base.blue * k, base.alpha)
                }
            }

            fun faceTop(base: Color): Color = if (base.luminance() > 0.5f) {
                base
            } else {
                base.copy(
                    red = base.red + (1f - base.red) * 0.30f,
                    green = base.green + (1f - base.green) * 0.30f,
                    blue = base.blue + (1f - base.blue) * 0.30f,
                )
            }

            var startAngle = -90f
            data class Geo(val index: Int, val start: Float, val sweep: Float)
            val geos = slices.mapIndexed { i, slice ->
                val full = slice.amountPaise * 360f / total * reveal.value
                val gap = if (slices.size > 1) 2.4f else 0f
                val geo = Geo(i, startAngle - gap / 2f, (full - gap).coerceAtLeast(0.8f))
                startAngle += full
                geo
            }

            // selected slice drawn last so its lifted body overlaps neighbours
            val ordered = geos.sortedBy { if (it.index == selected) 1 else 0 }
            for (geo in ordered) {
                val base = categoryColor(slices[geo.index].colorToken)
                val liftPx = lifts[geo.index] * depth * 1.35f
                val stroke = Stroke(width = strokeW, cap = StrokeCap.Butt)
                for (layer in 0 until layers) {
                    val t = layer / (layers - 1).coerceAtLeast(1).toFloat()
                    val dy = -liftPx + (1f - t) * depth // layer 0 = deepest
                    drawArc(
                        color = shade(base, t),
                        startAngle = geo.start,
                        sweepAngle = geo.sweep,
                        useCenter = false,
                        topLeft = Offset(topLeft.x, topLeft.y + dy),
                        size = arcSize(topLeft, side),
                        style = stroke,
                    )
                }
                // top face: vertical light gradient
                drawArc(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceTop(base), base),
                        startY = topLeft.y - liftPx,
                        endY = topLeft.y - liftPx + side,
                    ),
                    startAngle = geo.start,
                    sweepAngle = geo.sweep,
                    useCenter = false,
                    topLeft = Offset(topLeft.x, topLeft.y - liftPx),
                    size = arcSize(topLeft, side),
                    style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                )
                if (lifts[geo.index] > 0.05f) {
                    // rim highlight while lifted
                    drawArc(
                        color = Color.White.copy(alpha = 0.35f * lifts[geo.index]),
                        startAngle = geo.start,
                        sweepAngle = geo.sweep,
                        useCenter = false,
                        topLeft = Offset(topLeft.x, topLeft.y - liftPx),
                        size = arcSize(topLeft, side),
                        style = Stroke(width = strokeW * 0.12f, cap = StrokeCap.Butt),
                    )
                }
            }
        }

        // interactive center readout with animated morph
        AnimatedContent(
            targetState = if (selected >= 0 && selected < slices.size) selected else -1,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutVertically { -it / 3 } + fadeOut(tween(140)))
            },
            label = "center",
        ) { sel ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (sel >= 0) {
                    val s = slices[sel]
                    Text(s.label.uppercase(), style = MaterialTheme.typography.labelMedium)
                    Text(
                        MoneyFormat.formatPaise(s.amountPaise),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${s.amountPaise * 100 / total}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        MoneyFormat.formatPaise(total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "TOTAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun arcSize(topLeft: Offset, side: Float) = Size(side, side)

data class DonutSlice(
    val label: String,
    val amountPaise: Long,
    val colorToken: String,
)
