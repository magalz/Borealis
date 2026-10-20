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
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * How many times a second the position of a drawn background moves where nothing slows it. A third
 * of a second is a whole number of vsyncs at 30, 60, 90 and 120 Hz (1, 2, 3 and 4), so each update
 * falls on a vsync.
 */
internal const val DRAWN_FRAMES_PER_SECOND = 30

private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * How long before an update is due the thread wakes up and asks for the frame, so that a wake-up
 * that is late still makes it: it absorbs about 4 ms of lateness. It is less than the vsync period
 * of the highest rate the loop is tuned for, 120 Hz (8.3 ms), or the frame asked for would be the
 * one before.
 */
private const val WAKE_EARLY_NANOS = 4_000_000L

private const val NANOS_PER_MILLI = 1_000_000L

private const val TAU = (2 * PI).toFloat()

/** The widest the layer is drawn: a wider screen scales it up. */
private const val RENDER_WIDTH = 480

/** The number of strips a curtain is drawn in, whatever the width of the screen. */
private const val COLUMNS = 160

/**
 * The four colours of the aurora: the page and three hues, the strong accent (`#7C3AED`), the
 * accent (`#A78BFA`) and the teal. The page and the accents are taken from the tokens of [colors];
 * the teal has none. It holds the brush of each curtain and each glow too.
 *
 * A brush is made once, for a unit length, and a frame moves and stretches it with the canvas: a
 * brush made for every strip of every frame would also make a shader for each. The gradient of a
 * strip runs from nothing at its top to the curtain's brightness a quarter of the way down and back
 * to nothing at its bottom; that of a glow from its brightness at the centre, over a shoulder, to
 * nothing at its edge.
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

    /** One brush for each of [BANDS], across the unit height of a strip. */
    val curtains: List<Brush> = BANDS.map { band ->
        val color = of(band.hue)
        Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            CURTAIN_PEAK to color.copy(alpha = band.alpha),
            1f to color.copy(alpha = 0f),
            startY = 0f,
            endY = 1f,
        )
    }

    /** One brush for each of [SPOTS], across the unit radius of a glow centred on the origin. */
    val glows: List<Brush> = SPOTS.map { spot ->
        val color = of(spot.hue)
        Brush.radialGradient(
            0f to color.copy(alpha = spot.alpha),
            GLOW_SHOULDER to color.copy(alpha = spot.alpha * GLOW_SHOULDER_ALPHA),
            1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = 1f,
        )
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
internal fun DrawnBackground(
    style: BackgroundStyle,
    animated: Boolean,
    modifier: Modifier,
    framesPerSecond: Int = DRAWN_FRAMES_PER_SECOND,
) {
    DrawnBackground(style, rememberCycle(animated, framesPerSecond), modifier)
}

/** The same, at the position in the loop that [cycle] says: a test sets it to any point. */
@Composable
internal fun DrawnBackground(style: BackgroundStyle, cycle: FloatState, modifier: Modifier) {
    val colors = LocalBorealisColors.current
    val hues = remember(colors) { Hues(colors) }
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
 * composition; the sleep between them keeps the rate at about [framesPerSecond].
 */
@Composable
private fun rememberCycle(animated: Boolean, framesPerSecond: Int): FloatState {
    val cycle = remember { mutableFloatStateOf(STILL_CYCLE) }
    LaunchedEffect(animated, framesPerSecond) {
        if (!animated) {
            cycle.floatValue = STILL_CYCLE
            return@LaunchedEffect
        }
        runCycleLoop(framesPerSecond) { cycle.floatValue = it }
    }
    return cycle
}

/**
 * The frames of a running loop: [onCycle] gets the position in the loop, from 0 until 1, about
 * [framesPerSecond] times a second, 30 in general and 24 on a TV with glass. [nanoTime] is the
 * clock of the frame times, which are in the `System.nanoTime` timebase.
 *
 * An update falls due every gap of a [framesPerSecond]th of a second, counted from the one that
 * fell due before it, not from the frame that was drawn for it, and it is drawn at the first vsync
 * after it falls due. Where the gap is a whole number of vsyncs that is every gap, as 30 fps is at
 * 30, 60, 90 and 120 Hz. Where it is not, the vsyncs between two updates are now the fewer and now
 * the more that bracket the gap, and the rate holds on average: 24 fps at 60 Hz is 2 vsyncs and 3
 * vsyncs in turn, where the first vsync after each gap would give 20, and 30 fps at 50 and 75 Hz is
 * 30, where it would give 25. A loop that has fallen behind, as after the display stopped, does not
 * catch up in a burst: the next update is due no sooner than half a gap after the frame drawn.
 *
 * The thread sleeps between two frames and asks for one only to draw it. The sleep ends
 * [WAKE_EARLY_NANOS] before the update is due, so the frame asked for is that vsync's. It is
 * clamped to between none and one gap.
 */
internal suspend fun runCycleLoop(
    framesPerSecond: Int = DRAWN_FRAMES_PER_SECOND,
    nanoTime: () -> Long = System::nanoTime,
    onCycle: (Float) -> Unit,
) {
    val gap = NANOS_PER_SECOND / framesPerSecond
    var sleepMillis = 0L
    var due: Long? = null
    while (true) {
        delay(sleepMillis)
        val frame = withInfiniteFrameNanos {
            onCycle(cycleAt(it))
            it
        }
        due = max((due ?: frame) + gap, frame + gap / 2)
        // Clamped to none or one gap: with a clock that is not the one of the frame times the sleep
        // is wrong, but never an endless one.
        val sleepNanos = due - WAKE_EARLY_NANOS - nanoTime()
        sleepMillis = sleepNanos.coerceIn(0L, gap) / NANOS_PER_MILLI
    }
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
 * waves; the strips add up where curtains cross. A strip is the brush of its curtain, moved to the
 * strip and stretched to its length, and dimmed by how much the curtain breathes there.
 */
@Suppress("MagicNumber") // the choreography of the approved prototype, one literal per curve
private fun DrawScope.drawCurtains(cycle: Float, hues: Hues) {
    val stripWidth = size.width / COLUMNS
    for (index in BANDS.indices) {
        val band = BANDS[index]
        val brush = hues.curtains[index]
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
            withTransform({
                translate(column * stripWidth, top)
                scale(1f, length * 1.25f, Offset.Zero)
            }) {
                drawRect(
                    brush = brush,
                    size = Size(stripWidth, 1f),
                    alpha = breathe,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

/** Three soft lights that drift and breathe, adding up where they overlap. */
@Suppress("MagicNumber") // the choreography of the approved prototype, one literal per curve
private fun DrawScope.drawGlows(cycle: Float, hues: Hues) {
    val longer = max(size.width, size.height)
    for (index in SPOTS.indices) {
        val spot = SPOTS[index]
        val x = (spot.x + spot.driftX * sin(TAU * spot.cyclesX * cycle + spot.phase)) * size.width
        val y = (spot.y + spot.driftY * cos(TAU * spot.cyclesY * cycle + spot.phase)) * size.height
        val radius = spot.radius * longer * (0.8f + 0.2f * sin(TAU * cycle + spot.phase))
        withTransform({
            translate(x, y)
            scale(radius, radius, Offset.Zero)
        }) {
            drawCircle(
                brush = hues.glows[index],
                radius = 1f,
                center = Offset.Zero,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Where a strip reaches its brightness, as a part of its length. */
private const val CURTAIN_PEAK = 0.25f

/** The ripple of a curtain's edge, and the waves it makes along the width. */
private const val CURTAIN_RIPPLE = 0.02f
private const val CURTAIN_RIPPLE_WAVES = 3.1f

/** The waves in the length of the strips along the width. */
private const val LENGTH_WAVES = 1.9f

/** Where a glow is still at [GLOW_SHOULDER_ALPHA] of its brightness, as a part of its radius. */
private const val GLOW_SHOULDER = 0.45f
private const val GLOW_SHOULDER_ALPHA = 0.35f
