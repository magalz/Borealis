package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.unit.Constraints
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The deep teal of the aurora. It has no design token: the Library's palette is the page, the
 * violet accent and a violet that is stronger, and only the aurora wants a third hue.
 */
private val TEAL = Color(0xFF1F8A8A)

/** The length of a loop: every motion makes a whole number of cycles in it, so it has no seam. */
private const val PERIOD_NANOS = 20_000_000_000L

/** Where in the loop a system without animations stops, a tenth of the way in. */
private const val STILL_CYCLE = 0.1f

/**
 * The least time between two frames. The next frame is the first vsync after it, which is every
 * second one at 60 Hz: about 30 fps, and the thread sleeps in between.
 */
private const val FRAME_GAP_MILLIS = 25L

private const val TAU = (2 * PI).toFloat()

/** The widest the layer is drawn: a wider screen scales it up. */
private const val RENDER_WIDTH = 480

/** The number of strips a curtain is drawn in, whatever the width of the screen. */
private const val COLUMNS = 160

/**
 * The three hues of the aurora, taken from the tokens of [colors]: the page, the strong accent
 * (`#7C3AED`), the accent (`#A78BFA`) and the teal.
 */
private class Hues(colors: BorealisColors) {
    val page = colors.page
    private val deep = colors.accentStrong
    private val violet = colors.accent

    fun of(hue: Hue): Color =
        when (hue) {
            Hue.DEEP -> deep
            Hue.VIOLET -> violet
            Hue.TEAL -> TEAL
        }
}

private enum class Hue {
    DEEP,
    VIOLET,
    TEAL,
}

/**
 * A curtain: a band that waves across the screen, [thick] of the height, [alpha] at its brightest.
 */
@Suppress("LongParameterList") // a row of the table of the prototype's curtains
private class Band(
    val y: Float,
    val amplitude: Float,
    val waves: Float,
    val cycles: Int,
    val hue: Hue,
    val alpha: Float,
    val thick: Float,
    val phase: Float,
)

private val BANDS =
    listOf(
        Band(
            y = 0.16f,
            amplitude = 0.05f,
            waves = 1.3f,
            cycles = 1,
            hue = Hue.DEEP,
            alpha = 0.22f,
            thick = 0.30f,
            phase = 0.0f,
        ),
        Band(
            y = 0.24f,
            amplitude = 0.06f,
            waves = 2.1f,
            cycles = -1,
            hue = Hue.VIOLET,
            alpha = 0.12f,
            thick = 0.22f,
            phase = 1.7f,
        ),
        Band(
            y = 0.30f,
            amplitude = 0.04f,
            waves = 1.7f,
            cycles = 2,
            hue = Hue.TEAL,
            alpha = 0.10f,
            thick = 0.26f,
            phase = 3.1f,
        ),
    )

/**
 * A glow: a radial light of [radius] (of the longer side) at [x], [y] that drifts by [driftX] and
 * [driftY] [cyclesX] and [cyclesY] times in a loop and breathes once.
 */
@Suppress("LongParameterList") // a row of the table of the prototype's glows
private class Spot(
    val x: Float,
    val y: Float,
    val driftX: Float,
    val driftY: Float,
    val cyclesX: Int,
    val cyclesY: Int,
    val radius: Float,
    val hue: Hue,
    val alpha: Float,
    val phase: Float,
)

private val SPOTS =
    listOf(
        Spot(
            x = 0.28f,
            y = 0.22f,
            driftX = 0.10f,
            driftY = 0.06f,
            cyclesX = 1,
            cyclesY = 1,
            radius = 0.55f,
            hue = Hue.DEEP,
            alpha = 0.26f,
            phase = 0.0f,
        ),
        Spot(
            x = 0.72f,
            y = 0.30f,
            driftX = 0.08f,
            driftY = 0.07f,
            cyclesX = 1,
            cyclesY = 2,
            radius = 0.50f,
            hue = Hue.VIOLET,
            alpha = 0.12f,
            phase = 2.1f,
        ),
        Spot(
            x = 0.50f,
            y = 0.78f,
            driftX = 0.12f,
            driftY = 0.05f,
            cyclesX = 2,
            cyclesY = 1,
            radius = 0.60f,
            hue = Hue.TEAL,
            alpha = 0.10f,
            phase = 4.2f,
        ),
    )

/**
 * The background of the two styles drawn in Compose: [BackgroundStyle.AuroraCurtains] and
 * [BackgroundStyle.SoftGlow]. [animated] says whether the loop runs; without it, one still frame is
 * drawn.
 *
 * The position in the loop is read in the draw lambda only, inside a layer of its own, so a frame
 * redraws this layer and nothing is recomposed. The layer is at most 480 pixels wide and scaled up
 * to the screen: the light is soft, so the smoothing of the scale hides the strips of a curtain,
 * and a frame costs a sixteenth of the fill on a TV.
 */
@Composable
internal fun DrawnBackground(style: BackgroundStyle, animated: Boolean, modifier: Modifier) {
    DrawnBackground(style, rememberCycle(animated), modifier)
}

/** The same, at the position in the loop that [cycle] says: a test sets it to any point. */
@Composable
internal fun DrawnBackground(style: BackgroundStyle, cycle: FloatState, modifier: Modifier) {
    val hues = Hues(LocalBorealisColors.current)
    Layout(
        content = {
            Box(
                Modifier.drawBehind {
                    drawRect(hues.page)
                    val position = cycle.floatValue
                    if (style == BackgroundStyle.AuroraCurtains) {
                        drawCurtains(position, hues)
                    } else {
                        drawGlows(position, hues)
                    }
                }
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val drawnWidth = min(width, RENDER_WIDTH).coerceAtLeast(1)
        val drawnHeight = (height.toLong() * drawnWidth / max(width, 1)).toInt().coerceAtLeast(1)
        val drawn = measurables.single().measure(Constraints.fixed(drawnWidth, drawnHeight))
        layout(width, height) {
            drawn.placeWithLayer(0, 0) {
                scaleX = width.toFloat() / drawnWidth
                scaleY = height.toFloat() / drawnHeight
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}

/**
 * The position in the loop, from 0 until 1, as the frames of a running loop move it. The frames are
 * an infinite animation, so a test cancels them through the `InfiniteAnimationPolicy` of its
 * composition; the sleep between them keeps the rate at about 30 fps.
 */
@Composable
private fun rememberCycle(animated: Boolean): FloatState {
    val cycle = remember { mutableFloatStateOf(STILL_CYCLE) }
    LaunchedEffect(animated) {
        if (!animated) {
            cycle.floatValue = STILL_CYCLE
            return@LaunchedEffect
        }
        while (true) {
            delay(FRAME_GAP_MILLIS)
            withInfiniteFrameNanos { cycle.floatValue = cycleAt(it) }
        }
    }
    return cycle
}

/**
 * [withFrameNanos] as an infinite animation does it: through the [InfiniteAnimationPolicy] of the
 * coroutine, if it has one. A test cancels the loop there; the framework's own helper for it is
 * internal.
 */
private suspend fun <R> withInfiniteFrameNanos(onFrame: (Long) -> R): R {
    val policy = coroutineContext[InfiniteAnimationPolicy]
    return if (policy == null) withFrameNanos(onFrame)
    else policy.onInfiniteOperation { withFrameNanos(onFrame) }
}

/** The position in the loop at [frameNanos], which is the same position at every loop. */
private fun cycleAt(frameNanos: Long): Float = (frameNanos % PERIOD_NANOS).toFloat() / PERIOD_NANOS

/**
 * Three curtains of light, each a row of strips whose height, thickness and brightness follow
 * waves; the strips add up where curtains cross.
 */
@Suppress("MagicNumber") // the choreography of the approved prototype, one literal per curve
private fun DrawScope.drawCurtains(cycle: Float, hues: Hues) {
    val stripWidth = size.width / COLUMNS
    for (band in BANDS) {
        val color = hues.of(band.hue)
        for (column in 0 until COLUMNS) {
            val u = column.toFloat() / COLUMNS
            val centre =
                (band.y +
                    band.amplitude *
                        sin(TAU * (band.waves * u + band.cycles * cycle) + band.phase) +
                    CURTAIN_RIPPLE * sin(TAU * (CURTAIN_RIPPLE_WAVES * u - cycle) + band.phase)) *
                    size.height
            val breathe = 0.75f + 0.25f * sin(TAU * (u * 2 + cycle) + band.phase)
            val length =
                band.thick * size.height * (0.8f + 0.4f * sin(TAU * (LENGTH_WAVES * u + cycle)))
            val top = centre - length * 0.25f
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to color.copy(alpha = 0f),
                        0.25f to color.copy(alpha = band.alpha * breathe),
                        1f to color.copy(alpha = 0f),
                        startY = top,
                        endY = centre + length,
                    ),
                topLeft = Offset(column * stripWidth, top),
                size = Size(stripWidth, length * 1.25f),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Three soft lights that drift and breathe, adding up where they overlap. */
@Suppress("MagicNumber") // the choreography of the approved prototype, one literal per curve
private fun DrawScope.drawGlows(cycle: Float, hues: Hues) {
    val longer = max(size.width, size.height)
    for (spot in SPOTS) {
        val centre =
            Offset(
                (spot.x + spot.driftX * sin(TAU * spot.cyclesX * cycle + spot.phase)) * size.width,
                (spot.y + spot.driftY * cos(TAU * spot.cyclesY * cycle + spot.phase)) * size.height,
            )
        val radius = spot.radius * longer * (0.8f + 0.2f * sin(TAU * cycle + spot.phase))
        val color = hues.of(spot.hue)
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to color.copy(alpha = spot.alpha),
                    GLOW_SHOULDER to color.copy(alpha = spot.alpha * GLOW_SHOULDER_ALPHA),
                    1f to color.copy(alpha = 0f),
                    center = centre,
                    radius = radius,
                ),
            radius = radius,
            center = centre,
            blendMode = BlendMode.Plus,
        )
    }
}

/** The ripple of a curtain's edge, and the waves it makes along the width. */
private const val CURTAIN_RIPPLE = 0.02f
private const val CURTAIN_RIPPLE_WAVES = 3.1f

/** The waves in the length of the strips along the width. */
private const val LENGTH_WAVES = 1.9f

/** Where a glow is still at [GLOW_SHOULDER_ALPHA] of its brightness, as a part of its radius. */
private const val GLOW_SHOULDER = 0.45f
private const val GLOW_SHOULDER_ALPHA = 0.35f
