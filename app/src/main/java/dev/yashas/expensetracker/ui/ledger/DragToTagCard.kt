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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.yashas.expensetracker.ui.components.categoryColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One drop target in the ring around the lifted card. */
data class TagTarget(
    val key: String,
    val label: String,
    val colorToken: String,
)

/**
 * Geometry + hit-testing for the drag-to-tag interaction (pure, tested).
 *
 * Coordinate space is the app WINDOW in pixels, same space as
 * [androidx.compose.ui.layout.LayoutCoordinates.positionInWindow]. The overlay Popup is
 * pinned to the window origin (see [WindowOriginPositionProvider]) so offsets inside it
 * are window coordinates too — mixing the two spaces was what made the drag feel offset.
 */
object DragTagMath {

    /** Gap between the dragged circle's edge and a bubble's edge. */
    private const val RING_GAP = 18f

    /** Radius of the ring the bubbles sit on, measured from the spawn anchor. */
    fun ringRadius(circleRadius: Float, bubbleRadius: Float): Float =
        circleRadius + bubbleRadius + RING_GAP

    /**
     * Bubble centers RELATIVE TO THE SPAWN ANCHOR: [count] bubbles evenly spread on a
     * circle of [ringRadius], the first one straight above and the rest fanning around
     * clockwise. Fixed for the whole gesture — the bubbles never follow the finger.
     */
    fun ringOffsets(count: Int, ringRadius: Float): List<Offset> =
        (0 until count).map { i ->
            val rad = Math.toRadians(-90.0 + 360.0 * i / count)
            Offset((ringRadius * cos(rad)).toFloat(), (ringRadius * sin(rad)).toFloat())
        }

    /**
     * Nudge the spawn anchor so the WHOLE ring (bubbles + their labels) lands on screen.
     * Returns [anchor] untouched when it already fits.
     */
    fun clampRingAnchor(
        anchor: Offset,
        screen: IntSize,
        ringRadius: Float,
        bubbleRadius: Float,
        labelPad: Float,
    ): Offset {
        if (screen.width == 0 || screen.height == 0) return anchor
        val marginX = ringRadius + bubbleRadius + labelPad
        val marginY = ringRadius + bubbleRadius + labelPad
        return Offset(
            x = anchor.x.coerceIn(marginX, (screen.width - marginX).coerceAtLeast(marginX)),
            y = anchor.y.coerceIn(marginY, (screen.height - marginY).coerceAtLeast(marginY)),
        )
    }

    /**
     * Index of the bubble under the dragged circle's center, or -1 when the center is
     * outside every bubble's generous capture radius (2.2× — forgiving hover).
     */
    fun pickTarget(circleCenter: Offset, centers: List<Offset>, bubbleRadius: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        centers.forEachIndexed { i, c ->
            val d = (circleCenter - c).getDistance()
            if (d <= bubbleRadius * 2.2f && d < bestDist) {
                best = i
                bestDist = d
            }
        }
        return best
    }

    /** Keep the dragged circle itself fully on screen. */
    fun clampCircleCenter(center: Offset, screen: IntSize, circleRadius: Float): Offset {
        if (screen.width == 0 || screen.height == 0) return center
        return Offset(
            x = center.x.coerceIn(circleRadius, (screen.width - circleRadius).coerceAtLeast(circleRadius)),
            y = center.y.coerceIn(circleRadius, (screen.height - circleRadius).coerceAtLeast(circleRadius)),
        )
    }
}

/**
 * Pins the overlay Popup to the app window's top-left instead of the anchor card, so the
 * Popup's own coordinate space IS the window space every offset here is computed in.
 */
private val WindowOriginPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** Short vibration via the platform Vibrator (Compose TextHandleMove is too subtle). */
internal fun buzz(context: Context, millis: Long) {
    // Defensive: a vibration failure must never take the app down.
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

private const val CIRCLE_DP = 150
private const val BUBBLE_R_DP = 26

/**
 * Drag-to-tag card (review flow): long-press → scrim dims the list, the card lifts into a
 * screen-level Popup as a CIRCLE showing the payee initial + amount that sits exactly under
 * the finger and tracks it 1:1, while tag bubbles pop out in a fixed ring around the spawn
 * point → carry the circle onto a bubble to tag the whole payee group. Dropping anywhere
 * else springs back (state resets).
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
    val bubbleR = with(density) { BUBBLE_R_DP.dp.toPx() }
    val circlePx = with(density) { CIRCLE_DP.dp.toPx() }
    val labelPad = with(density) { 28.dp.toPx() }
    val ringR = DragTagMath.ringRadius(circlePx / 2f, bubbleR)

    var originTopLeft by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    /** Where the finger went down, in window coords — the bubbles' fixed anchor. */
    var spawnPoint by remember { mutableStateOf(Offset.Zero) }
    /** [spawnPoint] nudged so the whole ring fits on screen. Bubbles hang off this. */
    var anchor by remember { mutableStateOf(Offset.Zero) }
    /** Circle CENTER in window coords. Starts exactly under the finger, tracks it 1:1. */
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf(-1) }
    val scrim = remember { Animatable(0f) }
    // one pop-in animation per bubble, replayed on every long-press
    val pops = remember(targets.size) { List(targets.size) { Animatable(0f) } }

    LaunchedEffect(dragging) {
        scrim.animateTo(
            targetValue = if (dragging) 0.6f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
    }

    // bubbles pop OUT from the anchor, staggered, instead of blinking into existence
    LaunchedEffect(dragging) {
        if (!dragging) {
            pops.forEach { it.snapTo(0f) }
            return@LaunchedEffect
        }
        pops.forEachIndexed { i, a ->
            launch {
                delay(i * 35L)
                a.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }
    }

    // ring anchor depends on the overlay size, which only exists once the Popup is up
    LaunchedEffect(dragging, spawnPoint, boxSize) {
        if (dragging) {
            anchor = DragTagMath.clampRingAnchor(spawnPoint, boxSize, ringR, bubbleR, labelPad)
        }
    }

    fun bubbleCenters(): List<Offset> =
        DragTagMath.ringOffsets(targets.size, ringR).map { anchor + it }

    fun endDrag() {
        if (dragging && hovered in targets.indices) {
            buzz(context, 45)
            onDropTarget(targets[hovered].key)
        }
        dragging = false
        hovered = -1
    }

    Box(modifier = modifier) {
        // placeholder card (keeps list layout; dims while the circle overlay is up)
        Surface(
            modifier = Modifier
                .onGloballyPositioned { originTopLeft = it.positionInWindow() }
                .pointerInput(targets) {
                    // the card owns the whole gesture: Android keeps delivering this touch
                    // stream here even once the Popup is on top, so hover is resolved here
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val finger = originTopLeft + start
                            spawnPoint = finger
                            anchor = finger
                            circleCenter = finger
                            dragging = true
                            hovered = -1
                            buzz(context, 20)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            circleCenter = DragTagMath.clampCircleCenter(
                                center = circleCenter + amount,
                                screen = boxSize,
                                circleRadius = circlePx / 2f,
                            )
                            val h = DragTagMath.pickTarget(circleCenter, bubbleCenters(), bubbleR)
                            if (h != hovered) {
                                hovered = h
                                if (h >= 0) buzz(context, 18)
                            }
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
            // screen-level overlay pinned to the window origin: scrim + fixed bubble ring + circle
            Popup(
                popupPositionProvider = WindowOriginPositionProvider,
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrim.value)),
                    )

                    val offsets = DragTagMath.ringOffsets(targets.size, ringR)
                    targets.forEachIndexed { i, t ->
                        val pop = pops.getOrNull(i)?.value ?: 1f
                        // travel out from the anchor as it pops in; final spot never moves
                        val c = anchor + offsets[i] * pop
                        val isHover = hovered == i
                        val scale = (pop * (if (isHover) 1.25f else 1f)).coerceAtLeast(0.001f)
                        val r = bubbleR * scale
                        val d = with(density) { (r * 2).toDp() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset {
                                IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt())
                            },
                        ) {
                            Canvas(modifier = Modifier.size(d)) {
                                drawCircle(
                                    color = categoryColor(t.colorToken)
                                        .copy(alpha = (if (isHover) 1f else 0.92f) * pop.coerceIn(0f, 1f)),
                                )
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
                                color = if (isHover) Color.White else Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (circleCenter.x - circlePx / 2f).roundToInt(),
                                    (circleCenter.y - circlePx / 2f).roundToInt(),
                                )
                            }
                            .size(CIRCLE_DP.dp),
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
