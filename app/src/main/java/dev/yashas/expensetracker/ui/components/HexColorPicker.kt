package dev.yashas.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Curated hues for the honeycomb — distinct, vivid, legible on the dark theme. */
val HEX_CHOICES = listOf(
    "#8B5CF6", "#A855F7", "#EC4899", "#EF4444", "#F97316", "#F59E0B", "#EAB308",
    "#84CC16", "#22C55E", "#14B8A6", "#06B6D4", "#3B82F6", "#94A3B8",
)

private const val SQ3 = 1.7320508f

/**
 * Compact honeycomb color picker: 13 curated hues in a 4-5-4 pointy-top hexagon
 * cluster. Returns the chosen color as a "#RRGGBB" token. No HSV wheels — a small
 * set of distinct choices, per the Home tag-picker brief.
 */
@Composable
fun HexColorPicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = modifier
            .aspectRatio(SQ3)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val r = hexRadius(size.width.toFloat(), size.height.toFloat())
                    var bestIndex = -1
                    var bestDist = Float.MAX_VALUE
                    hexCenters(size.width.toFloat(), size.height.toFloat()).forEachIndexed { i, c ->
                        val dx = tap.x - c.x
                        val dy = tap.y - c.y
                        val d = dx * dx + dy * dy
                        if (d < bestDist) {
                            bestDist = d
                            bestIndex = i
                        }
                    }
                    if (bestIndex in HEX_CHOICES.indices && bestDist <= r * r * 1.2f) {
                        onSelected(HEX_CHOICES[bestIndex])
                    }
                }
            },
    ) {
        size = IntSize(this.size.width.toInt(), this.size.height.toInt())
        val r = hexRadius(this.size.width, this.size.height)
        val centers = hexCenters(this.size.width, this.size.height)
        HEX_CHOICES.forEachIndexed { i, hexColor ->
            val isSelected = hexColor == selected
            val hexR = r * if (isSelected) 1.02f else 0.9f
            drawPath(
                path = hexPath(centers[i], hexR),
                color = parseHexColor(hexColor),
            )
            if (isSelected) {
                drawPath(
                    path = hexPath(centers[i], r * 1.05f),
                    color = Color.Transparent,
                    style = Stroke(width = r * 0.18f),
                )
                // soft white outline reads as selection on the dark dialog
                drawPath(
                    path = hexPath(centers[i], r * 0.72f),
                    color = Color.White.copy(alpha = 0.9f),
                    style = Stroke(width = r * 0.1f),
                )
            }
        }
    }
}

private fun hexRadius(w: Float, h: Float): Float = min(w / (5f * SQ3), h / 5f)

private fun hexCenters(w: Float, h: Float): List<Offset> {
    val r = hexRadius(w, h)
    val marginX = (w - 5f * SQ3 * r) / 2f
    val marginY = (h - 5f * r) / 2f
    val rows = listOf(4, 5, 4)
    val centers = mutableListOf<Offset>()
    var y = marginY + r
    rows.forEach { count ->
        for (i in 0 until count) {
            val x = marginX + (i + 0.5f + if (count == 4) 0.5f else 0f) * SQ3 * r
            centers += Offset(x, y)
        }
        y += 1.5f * r
    }
    return centers
}

private fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 6) {
        val angle = Math.toRadians(60.0 * k - 90.0)
        val px = c.x + (r * cos(angle)).toFloat()
        val py = c.y + (r * sin(angle)).toFloat()
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/** "#RRGGBB" → Compose Color; falls back to violet on malformed input. */
fun parseHexColor(hex: String): Color =
    runCatching {
        Color((0xFF000000L or hex.drop(1).toLong(16)).toInt())
    }.getOrDefault(Color(0xFF8B5CF6))
