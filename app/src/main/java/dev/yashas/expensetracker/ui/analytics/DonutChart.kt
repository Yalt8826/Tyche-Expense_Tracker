package dev.yashas.expensetracker.ui.analytics

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.categoryColor
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * C3 interactive donut (signature interaction): tap slice → expands + center morphs
 * to `CATEGORY ₹x · n%`; tap-away returns to total. Sub-breakdown rendering is the
 * caller's job (list under the chart), keeping this pure draw + selection.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(-1) }
    val total = slices.sumOf { it.amountPaise }.coerceAtLeast(1)

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = minOf(size.width, size.height) / 2f
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > radius || dist < radius * 0.45f) {
                            selected = -1 // tap-away outside ring = back to total
                            return@detectTapGestures
                        }
                        // screen y is down → atan2 gives clockwise-from-+x; ring starts at top (-90°)
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
            val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Butt)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            var startAngle = -90f
            slices.forEachIndexed { i, slice ->
                val sweep = slice.amountPaise * 360f / total
                val expanded = i == selected
                val extra = if (expanded) 6f else 0f
                drawArc(
                    color = categoryColor(slice.colorToken),
                    startAngle = startAngle - extra / 2,
                    sweepAngle = (sweep - if (slices.size > 1) 2f else 0f).coerceAtLeast(1f) + extra,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }

        // interactive center readout (S12a: the center is an information surface)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selected >= 0 && selected < slices.size) {
                val s = slices[selected]
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

data class DonutSlice(
    val label: String,
    val amountPaise: Long,
    val colorToken: String,
)
