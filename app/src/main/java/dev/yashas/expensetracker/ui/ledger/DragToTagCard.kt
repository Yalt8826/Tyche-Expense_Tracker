package dev.yashas.expensetracker.ui.ledger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.ui.components.categoryColor
import kotlin.math.roundToInt

/** One drop target orbiting the card during drag-to-tag. */
data class TagTarget(
    val key: String,
    val label: String,
    val colorToken: String,
)

/**
 * Geometry + hit-testing for the drag-to-tag interaction (pure, tested).
 * All coordinates are relative to the CARD CENTER in pixels.
 */
object DragTagMath {

    /**
     * Bubble centers for [count] targets around a card of [width]×[height]:
     * two along the top edge, one above-center, two along the bottom edge.
     */
    fun bubbleCenters(width: Float, height: Float, bubbleRadius: Float, count: Int = 5): List<Offset> {
        val insetX = width * 0.16f
        val topY = -height / 2f - bubbleRadius * 0.9f
        val midY = -height / 2f - bubbleRadius * 1.7f
        val bottomY = height / 2f + bubbleRadius * 0.9f
        return when {
            count >= 5 -> listOf(
                Offset(-width / 2 + insetX, topY),
                Offset(width / 2 - insetX, topY),
                Offset(0f, midY),
                Offset(-width / 2 + insetX, bottomY),
                Offset(width / 2 - insetX, bottomY),
            )
            else -> (0 until count).map { i ->
                val fx = (i + 1f) / (count + 1f)
                Offset((fx - 0.5f) * width, if (i % 2 == 0) topY else bottomY)
            }
        }
    }

    /**
     * Index of the target under the finger ([drag] offset relative to card center), or
     * -1 when outside every bubble's generous capture radius (2.2× — forgiving hover).
     */
    fun pickTarget(drag: Offset, centers: List<Offset>, bubbleRadius: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        centers.forEachIndexed { i, c ->
            val d = (drag - c).getDistance()
            if (d <= bubbleRadius * 2.2f && d < bestDist) {
                best = i
                bestDist = d
            }
        }
        return best
    }
}

/**
 * Drag-to-tag card (review flow): long-press → card morphs toward a square tile and
 * lifts → tag bubbles bloom around it → drag the card onto a bubble → onDropTarget.
 * Dropping anywhere else springs back. Geometry lives in DragTagMath (tested).
 */
@Composable
fun DragToTagCard(
    targets: List<TagTarget>,
    onDropTarget: (String) -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    /** Finger position in CARD coordinates (long-press point + accumulated deltas). */
    var finger by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val bubbleR = with(density) { 26.dp.toPx() }
    val morph = remember { Animatable(0f) }

    LaunchedEffect(dragging) {
        morph.animateTo(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f),
        )
    }

    fun endDrag() {
        if (dragging && hovered in targets.indices) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDropTarget(targets[hovered].key)
        }
        dragging = false
        finger = Offset.Zero
        hovered = -1
    }

    Box(modifier = modifier) {
        // tag bubbles bloom around the card's resting position while dragging
        if (dragging && cardSize != IntSize.Zero) {
            val centers = DragTagMath.bubbleCenters(
                cardSize.width.toFloat(),
                cardSize.height.toFloat(),
                bubbleR,
                targets.size,
            )
            targets.forEachIndexed { i, t ->
                val c = centers.getOrElse(i) { Offset.Zero }
                val isHover = hovered == i
                val d = with(density) { (bubbleR * 2).toDp() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset {
                        IntOffset(
                            (cardSize.width / 2f + c.x - bubbleR).roundToInt(),
                            (cardSize.height / 2f + c.y - bubbleR * 1.15f).roundToInt(),
                        )
                    },
                ) {
                    Canvas(modifier = Modifier.size(d)) {
                        drawCircle(color = categoryColor(t.colorToken).copy(alpha = if (isHover) 1f else 0.88f))
                        if (isHover) {
                            drawCircle(
                                color = Color.White,
                                radius = size.minDimension / 2f * 0.55f,
                                style = Stroke(width = 2.5.dp.toPx()),
                            )
                        }
                    }
                    Text(
                        text = t.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isHover) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHover) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        androidx.compose.material3.Surface(
            modifier = Modifier
                .onSizeChanged { cardSize = it }
                // rectangle → square-ish tile morph while dragging
                .layout { measurable, constraints ->
                    val target = (constraints.maxWidth * 0.62f).roundToInt()
                    val w = androidx.compose.ui.util.lerp(constraints.maxWidth, target, morph.value)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
                .graphicsLayer {
                    translationX = finger.x
                    translationY = finger.y
                    val s = if (dragging) 1.04f else 1f
                    scaleX = s
                    scaleY = s
                    shadowElevation = if (dragging) 18f else 2f
                }
                .pointerInput(targets) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragging = true
                            finger = start
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            finger += amount
                            if (cardSize != IntSize.Zero) {
                                val rel = finger - Offset(cardSize.width / 2f, cardSize.height / 2f)
                                val centers = DragTagMath.bubbleCenters(
                                    cardSize.width.toFloat(),
                                    cardSize.height.toFloat(),
                                    bubbleR,
                                    targets.size,
                                )
                                val h = DragTagMath.pickTarget(rel, centers, bubbleR)
                                if (h != hovered) {
                                    hovered = h
                                    if (h >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                }
                .clickable { onOpenDetail() },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Box(modifier = Modifier.padding(14.dp)) { content() }
        }
    }
}
