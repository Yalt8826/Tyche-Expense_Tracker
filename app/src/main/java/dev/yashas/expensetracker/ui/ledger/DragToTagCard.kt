package dev.yashas.expensetracker.ui.ledger

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

/** Short vibration via the platform Vibrator (Compose TextHandleMove is too subtle). */
internal fun buzz(context: Context, millis: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
}

private const val CIRCLE_DP = 150

/**
 * Drag-to-tag card (review flow): long-press → scrim dims the list, the card lifts into
 * a screen-level Popup as a CIRCLE showing the payee initial + amount, tag bubbles orbit
 * it above everything → drop the circle on a bubble to tag the whole payee group.
 * Dropping anywhere else springs back (state resets).
 */
@Composable
fun DragToTagCard(
    targets: List<TagTarget>,
    amountText: String,
    initial: String,
    onDropTarget: (String) -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bubbleR = with(density) { 26.dp.toPx() }
    val circlePx = with(density) { CIRCLE_DP.dp.toPx() }

    var restingSize by remember { mutableStateOf(IntSize.Zero) }
    var originTopLeft by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    /** Circle-center delta from its origin position. */
    var finger by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf(-1) }
    val scrim = remember { Animatable(0f) }

    LaunchedEffect(dragging) {
        scrim.animateTo(
            targetValue = if (dragging) 0.6f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
    }

    fun bubbleCentersScreen(): List<Offset> {
        val local = DragTagMath.bubbleCenters(
            restingSize.width.toFloat(),
            restingSize.height.toFloat(),
            bubbleR,
            targets.size,
        )
        return local.map { originTopLeft + it }
    }

    fun circleCenter(): Offset = originTopLeft + finger +
        Offset(restingSize.width / 2f, restingSize.height / 2f)

    fun endDrag() {
        if (dragging && hovered in targets.indices) {
            buzz(context, 45)
            onDropTarget(targets[hovered].key)
        }
        dragging = false
        finger = Offset.Zero
        hovered = -1
    }

    Box(modifier = modifier) {
        // placeholder card (keeps list layout; dims while the circle overlay is up)
        Surface(
            modifier = Modifier
                .onSizeChanged { restingSize = it }
                .onGloballyPositioned { originTopLeft = Offset(it.positionInWindow().x, it.positionInWindow().y) }
                .pointerInput(targets) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            dragging = true
                            finger = Offset.Zero
                            hovered = -1
                            buzz(context, 20)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            finger += amount
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                }
                .clickable { onOpenDetail() },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = if (dragging) 0.25f else 1f),
            tonalElevation = 2.dp,
        ) {
            Box(modifier = Modifier.padding(14.dp)) { content() }
        }

        if (dragging) {
            // screen-level overlay: scrim + orbiting bubbles + dragged circle
            Popup(
                alignment = Alignment.TopStart,
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrim.value)),
                    )

                    val centers = bubbleCentersScreen()
                    targets.forEachIndexed { i, t ->
                        val c = centers[i]
                        val isHover = hovered == i
                        val d = with(density) { (bubbleR * 2).toDp() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset {
                                IntOffset((c.x - bubbleR).roundToInt(), (c.y - bubbleR * 1.1f).roundToInt())
                            },
                        ) {
                            Canvas(modifier = Modifier.size(d)) {
                                drawCircle(color = categoryColor(t.colorToken).copy(alpha = if (isHover) 1f else 0.92f))
                                if (isHover) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = size.minDimension / 2f * 0.5f,
                                        style = Stroke(width = 3.dp.toPx()),
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

                    val circleTopLeft = circleCenter() - Offset(circlePx / 2f, circlePx / 2f)
                    Surface(
                        modifier = Modifier
                            .offset {
                                IntOffset(circleTopLeft.x.roundToInt(), circleTopLeft.y.roundToInt())
                            }
                            .size(CIRCLE_DP.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { _ -> finger = Offset.Zero },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        finger += amount
                                        val rel = targets.indices.map { i ->
                                            circleCenter() - centers.getOrElse(i) { Offset.Zero }
                                        }
                                        val h = DragTagMath.pickTarget(Offset.Zero, rel, bubbleR)
                                        if (h != hovered) {
                                            hovered = h
                                            if (h >= 0) buzz(context, 18)
                                        }
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() },
                                )
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 24.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = amountText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
