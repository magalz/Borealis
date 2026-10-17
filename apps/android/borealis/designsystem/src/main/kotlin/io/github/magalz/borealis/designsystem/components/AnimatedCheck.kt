package io.github.magalz.borealis.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved

private const val GRID = 24f
private const val STROKE = 2f
private const val RING_RADIUS = 11f
private const val DRAW_MILLIS = 400

/** Where the ring ends and the check starts, as fractions of the drawing time of a circled one. */
private const val RING_END = 0.55f
private const val CHECK_START = 0.45f

/** A check inside a ring: smaller than the plain one, so it clears the ring. */
private const val CIRCLED_CHECK = "M7.5 12.5l3 3 6-7"

/**
 * A check mark that draws itself once, from its start to its end, over about 400 ms, and stays: for
 * something that has just succeeded, such as Paired. With [circled] a ring draws first and the
 * check follows inside it. When the system removes animations the check is shown complete. The size
 * is the one the caller gives it, 24dp by default; it is drawn in [color] on the 24-unit grid with
 * strokes of 2 and round caps.
 */
@Composable
fun AnimatedCheck(
    modifier: Modifier = Modifier,
    circled: Boolean = false,
    color: Color = LocalBorealisColors.current.success,
) {
    val removed = rememberAnimationsRemoved()
    val progress = remember { Animatable(if (removed) 1f else 0f) }
    LaunchedEffect(removed) {
        if (removed) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(DRAW_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val measure =
        remember(circled) {
            val path =
                PathParser()
                    .parsePathString(if (circled) CIRCLED_CHECK else BorealisIcons.CHECK)
                    .toPath()
            PathMeasure().apply { setPath(path, false) }
        }
    Canvas(modifier.size(GRID.dp)) {
        val stroke = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val drawn = progress.value
        scale(size.width / GRID, pivot = Offset.Zero) {
            val checkProgress =
                if (circled) ((drawn - CHECK_START) / (1f - CHECK_START)).coerceIn(0f, 1f)
                else drawn
            if (circled) {
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = 360f * (drawn / RING_END).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(GRID / 2 - RING_RADIUS, GRID / 2 - RING_RADIUS),
                    size = Size(2 * RING_RADIUS, 2 * RING_RADIUS),
                    style = stroke,
                )
            }
            if (checkProgress > 0f) {
                val part = Path()
                measure.getSegment(0f, measure.length * checkProgress, part, true)
                drawPath(part, color, style = stroke)
            }
        }
    }
}
