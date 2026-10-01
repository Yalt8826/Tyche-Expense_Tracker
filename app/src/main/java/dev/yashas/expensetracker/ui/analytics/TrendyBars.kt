package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashas.expensetracker.ui.theme.SeriesCyan
import dev.yashas.expensetracker.ui.theme.SeriesViolet
import java.time.YearMonth

/**
 * C4 v3 — pannable, tappable extruded columns. A fixed 12-month history is loaded by
 * the repo; the chart shows a [VISIBLE_MONTHS]-month window, drag to pan into the past
 * (clamped), tap a month for its income/spend breakdown. Month labels are drawn inside
 * the Canvas so they always sit under their columns.
 */
@Composable
fun TrendyBars(
    bars: List<MonthBarUi>,
    modifier: Modifier = Modifier,
) {
    val visible = VISIBLE_MONTHS
    var pan by remember { mutableFloatStateOf(0f) } // months panned into the past, 0 = newest right
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableIntStateOf(-1) } // absolute bar index

    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }
    val maxPan = (bars.size - visible).coerceAtLeast(0)
    val floor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val selBandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val labelText = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    LaunchedEffect(labelText) { labelPaint.color = labelText.toArgb() }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(bars, maxPan) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = { dragAccum = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        val groupW = size.width / visible
                        dragAccum += dragAmount
                        val months = dragAccum / groupW
                        if (kotlin.math.abs(months) >= 0.02f) {
                            dragAccum = 0f
                            pan = (pan + months).coerceIn(0f, maxPan.toFloat())
                        }
                    }
                }
                .pointerInput(bars, maxPan) {
                    detectTapGestures { offset ->
                        val groupW = size.width / visible
                        val gi = ((size.width - offset.x) / groupW).toInt() // 0 = rightmost
                        val rightAbs = (bars.size - 1 - pan).toInt()
                        val abs = (rightAbs - gi).coerceIn(0, bars.size - 1)
                        selected = if (selected == abs) -1 else abs
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxVal = bars.maxOf { maxOf(it.incomeRupees, it.expenseRupees) }.coerceAtLeast(1f)
            val chartH = size.height * 0.80f
            val baseY = size.height * 0.90f
            val groupW = size.width / visible
            val barW = groupW * 0.24f
            val depth = barW * 0.28f
            labelPaint.textSize = 10.sp.toPx()

            // selected-month band behind the columns
            if (selected >= 0) {
                val selCx = cxFor(selected, bars.size, pan, groupW, size.width)
                if (selCx > -groupW && selCx < size.width + groupW) {
                    drawRect(selBandColor, topLeft = Offset(selCx - groupW / 2f, 0f), size = Size(groupW, baseY))
                }
            }

            // glass floor
            drawLine(floor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.5f)

            bars.forEachIndexed { abs, bar ->
                val cx = cxFor(abs, bars.size, pan, groupW, size.width)
                if (cx < -groupW || cx > size.width + groupW) return@forEachIndexed
                val gi = abs - (bars.size - 1 - pan.toInt() - (visible - 1))
                val stagger = ((gi.coerceAtLeast(0)) * 0.9f).coerceAtMost(3f)
                val p = ((progress.value - stagger * 0.08f) / 0.7f).coerceIn(0f, 1f)

                val ih = (bar.incomeRupees / maxVal) * chartH * p
                extrudedBar(
                    left = cx - barW - 2.dp.toPx() / 2f,
                    top = baseY - ih,
                    width = barW,
                    height = ih,
                    depth = depth,
                    base = SeriesCyan,
                )
                val eh = (bar.expenseRupees / maxVal) * chartH * p
                extrudedBar(
                    left = cx + 2.dp.toPx() / 2f,
                    top = baseY - eh,
                    width = barW,
                    height = eh,
                    depth = depth,
                    base = SeriesViolet,
                )

                // month label centered under its column group
                drawContext.canvas.nativeCanvas.drawText(bar.label, cx, baseY + 16.sp.toPx(), labelPaint)
            }
        }

        // tap info line (fixed presence so the layout never jumps)
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(120))) },
            label = "barinfo",
        ) { sel ->
            if (sel in bars.indices) {
                val b = bars[sel]
                val net = b.incomeRupees - b.expenseRupees
                Text(
                    "${b.monthTitle}  ·  in ${fmtRu(b.incomeRupees)}  ·  out ${fmtRu(b.expenseRupees)}  ·  net ${fmtRu(net)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (maxPan > 0) "Tap a month for details · drag right for history" else "Tap a month for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        RowLegend()
    }
}

private const val VISIBLE_MONTHS = 5

/** Center-x of absolute bar [abs] given current pan (0 = newest at the right edge). */
private fun cxFor(abs: Int, size: Int, pan: Float, groupW: Float, canvasW: Float): Float {
    val rightPad = groupW / 2f
    return canvasW - rightPad - (size - 1 - abs - pan) * groupW
}

private fun fmtRu(rupees: Float): String {
    val whole = rupees.toLong()
    return "₹$whole"
}

private fun DrawScope.extrudedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    depth: Float,
    base: Color,
) {
    if (height <= 0f) return
    val r = width / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                base.copy(red = base.red + (1 - base.red) * 0.35f, green = base.green + (1 - base.green) * 0.35f, blue = base.blue + (1 - base.blue) * 0.35f),
                base,
            ),
            startY = top,
            endY = top + height,
        ),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(r, r),
        style = Fill,
    )
    // top cap parallelogram (keeps the extruded 3D read)
    val cap = Path().apply {
        moveTo(left, top + r)
        lineTo(left + r, top)
        lineTo(left + width - r, top)
        lineTo(left + width, top + r)
        lineTo(left + width - r + depth, top + r - depth)
        lineTo(left + r + depth, top + r - depth)
        close()
    }
    drawPath(
        cap,
        base.copy(red = base.red + (1 - base.red) * 0.5f, green = base.green + (1 - base.green) * 0.5f, blue = base.blue + (1 - base.blue) * 0.5f),
        style = Fill,
    )
    // glossy glass strip along the right edge (light reflection, no shadow)
    val stripW = width * 0.16f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.04f)),
            startY = top,
            endY = top + height,
        ),
        topLeft = Offset(left + width - stripW - r * 0.35f, top + r * 0.9f),
        size = Size(stripW, (height - r * 1.4f).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(stripW / 2f, stripW / 2f),
        style = Fill,
    )
}

@Composable
private fun RowLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(8.dp).height(8.dp)) { drawCircle(SeriesCyan) }
        Spacer(Modifier.width(5.dp))
        Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Canvas(modifier = Modifier.width(8.dp).height(8.dp)) { drawCircle(SeriesViolet) }
        Spacer(Modifier.width(5.dp))
        Text("Spending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class MonthBarUi(
    val label: String,
    val yearMonth: String,
    val incomeRupees: Float,
    val expenseRupees: Float,
) {
    val monthTitle: String
        get() {
            val ym = YearMonth.parse(yearMonth)
            val m = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
            return "$m ${ym.year}"
        }
}
