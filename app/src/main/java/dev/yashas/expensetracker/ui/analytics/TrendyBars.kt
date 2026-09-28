package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.ui.theme.SeriesCyan
import dev.yashas.expensetracker.ui.theme.SeriesViolet
import java.time.YearMonth

/**
 * C4 v2 — animated 3D-flavoured column pair per month: income (cyan) behind,
 * expense (violet) front, rounded caps, vertical light gradient, glass floor line.
 * Bars race up with a per-column stagger on every data change (arrival motion only).
 */
@Composable
fun TrendyBars(
    bars: List<MonthBarUi>,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }
    val floor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            if (bars.isEmpty()) return@Canvas
            val maxVal = bars.maxOf { maxOf(it.incomeRupees, it.expenseRupees) }.coerceAtLeast(1f)
            val chartH = size.height * 0.86f
            val baseY = size.height * 0.90f
            val groupW = size.width / bars.size.coerceAtLeast(1)
            val barW = groupW * 0.26f
            val depth = barW * 0.28f

            // glass floor
            drawLine(floor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.5f)

            bars.forEachIndexed { gi, bar ->
                val groupStart = gi * groupW
                val cx = groupStart + groupW / 2f
                val stagger = (gi * 0.9f).coerceAtMost(3f)
                val p = ((progress.value - stagger * 0.08f) / 0.7f).coerceIn(0f, 1f)

                // income bar (back-left, cyan)
                val ih = (bar.incomeRupees / maxVal) * chartH * p
                extrudedBar(
                    left = cx - barW - 2.dp.toPx() / 2f,
                    top = baseY - ih,
                    width = barW,
                    height = ih,
                    depth = depth,
                    base = SeriesCyan,
                    dark = SeriesCyan.copy(red = SeriesCyan.red * 0.55f, green = SeriesCyan.green * 0.55f, blue = SeriesCyan.blue * 0.55f),
                )
                // expense bar (front-right, violet)
                val eh = (bar.expenseRupees / maxVal) * chartH * p
                extrudedBar(
                    left = cx + 2.dp.toPx() / 2f,
                    top = baseY - eh,
                    width = barW,
                    height = eh,
                    depth = depth,
                    base = SeriesViolet,
                    dark = SeriesViolet.copy(red = SeriesViolet.red * 0.55f, green = SeriesViolet.green * 0.55f, blue = SeriesViolet.blue * 0.55f),
                )
            }
        }
        // month labels under the columns (static text, never animated)
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row {
            LegendDot(SeriesCyan, "Income")
            Spacer(Modifier.width(14.dp))
            LegendDot(SeriesViolet, "Spending")
        }
    }
}

private fun DrawScope.extrudedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    depth: Float,
    base: Color,
    dark: Color,
) {
    if (height <= 0f) return
    val r = width / 2f
    // top face gradient (light from above)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(base.copy(red = base.red + (1 - base.red) * 0.35f, green = base.green + (1 - base.green) * 0.35f, blue = base.blue + (1 - base.blue) * 0.35f), base), startY = top, endY = top + height),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Fill,
    )
    // right-side extrusion hint (darker, skewed panel)
    val side = Path().apply {
        moveTo(left + width, top + r)
        lineTo(left + width + depth, top + r - depth)
        lineTo(left + width + depth, top + height - r - depth + r)
        lineTo(left + width, top + height)
        close()
    }
    drawPath(side, dark.copy(alpha = 0.85f), style = Fill)
    // top cap parallelogram
    val cap = Path().apply {
        moveTo(left, top + r)
        lineTo(left + r, top)
        lineTo(left + width - r, top)
        lineTo(left + width, top + r)
        lineTo(left + width - r + depth, top + r - depth)
        lineTo(left + r + depth, top + r - depth)
        close()
    }
    drawPath(cap, base.copy(red = base.red + (1 - base.red) * 0.5f, green = base.green + (1 - base.green) * 0.5f, blue = base.blue + (1 - base.blue) * 0.5f), style = Fill)
}

@Composable
private fun LegendDot(color: Color, label: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(8.dp).height(8.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class MonthBarUi(val label: String, val incomeRupees: Float, val expenseRupees: Float)
