package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val CONTENT_GAP = 12.dp
private val TRACK_AREA_HEIGHT = 32.dp
private val TRACK_HEIGHT = 4.dp
private val THUMB_RADIUS = 9.dp
private const val STEP_TOLERANCE = 1e-4f

/**
 * A row with a [title], the formatted value [valueLabel] and a slider under them for [value] in
 * [valueRange]. The value moves in multiples of [step] from the start of the range.
 *
 * With the D-pad Right and Left move the value to the next or previous step and stop at the ends of
 * the range. At an end the key is not used, so the focus goes on: Left from a slider at its lowest
 * value returns to the group list of a [SettingsTwoPane]. A touch on the track or a drag along it
 * sets the nearest step. A screen reader reads the value as progress and can set it.
 *
 * A disabled row is dimmed and ignores input, but it stays a focus stop. The outline of a focused
 * row reaches 5dp outside it, so a list of rows needs 8dp of padding around it and 10dp between
 * rows.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dim = if (enabled) 1f else DISABLED_ALPHA
    val change: (Float) -> Boolean = { target ->
        val changed = target != value
        if (changed) onValueChange(target)
        changed
    }
    val steps = (((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1)
    Column(
        modifier
            .settingsRowSurface()
            .onKeyEvent { event ->
                enabled && stepOnKey(event, value, valueRange, step, change)
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo =
                    ProgressBarRangeInfo(value, valueRange, steps.coerceAtLeast(0))
                stateDescription = valueLabel
                if (enabled) {
                    setProgress { target ->
                        change(nearestStep(target, valueRange, step))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .settingsRowSize()
    ) {
        SliderHeader(title, valueLabel, dim)
        SliderTrack(
            fraction = fractionOf(value, valueRange),
            enabled = enabled,
            onFraction = { fraction ->
                change(
                    nearestStep(
                        valueRange.start + fraction * (valueRange.endInclusive - valueRange.start),
                        valueRange,
                        step,
                    )
                )
            },
            modifier = Modifier.alpha(dim),
        )
    }
}

@Composable
private fun SliderHeader(title: String, valueLabel: String, dim: Float) {
    val colors = LocalBorealisColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f).alpha(dim),
            color = colors.ink,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            valueLabel,
            modifier = Modifier.alpha(dim),
            color = colors.ink3,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Right and Left move the value by a step; [change] says whether it did, and so whether the key was
 * used.
 */
private fun stepOnKey(
    event: KeyEvent,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    change: (Float) -> Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionRight -> change(adjacentStep(value, range, step, 1))
        Key.DirectionLeft -> change(adjacentStep(value, range, step, -1))
        else -> false
    }
}

/** The track and thumb of a slider; a touch or a drag reports where along the track it is. */
@Composable
private fun SliderTrack(
    fraction: Float,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    val report = rememberUpdatedState(onFraction)
    Box(
        modifier
            .fillMaxWidth()
            .height(TRACK_AREA_HEIGHT)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                            val radius = THUMB_RADIUS.toPx()
                            detectTapGestures { report.value(fractionAt(it.x, size.width, radius)) }
                        }
                        .pointerInput(Unit) {
                            val radius = THUMB_RADIUS.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { report.value(fractionAt(it.x, size.width, radius)) }
                            ) { change, _ ->
                                report.value(fractionAt(change.position.x, size.width, radius))
                                change.consume()
                            }
                        }
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val radius = THUMB_RADIUS.toPx()
                val stroke = TRACK_HEIGHT.toPx()
                val y = size.height / 2
                val thumbX = radius + fraction * (size.width - 2 * radius)
                drawLine(
                    colors.lineStrong,
                    Offset(radius, y),
                    Offset(size.width - radius, y),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    colors.accent,
                    Offset(radius, y),
                    Offset(thumbX, y),
                    stroke,
                    StrokeCap.Round,
                )
                drawCircle(colors.accent, radius, Offset(thumbX, y))
            }
    )
}

/** Where along a track [width] wide, with a thumb of [radius] at each end, a touch at [x] is. */
private fun fractionAt(x: Float, width: Int, radius: Float): Float {
    val usable = width - 2 * radius
    return if (usable <= 0f) 0f else ((x - radius) / usable).coerceIn(0f, 1f)
}

private fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
}

/** The step of [range] nearest to [target]. */
private fun nearestStep(
    target: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    val index = ((target - range.start) / step).roundToInt()
    return (range.start + index * step).coerceIn(range.start, range.endInclusive)
}

/**
 * The step above ([direction] 1) or below (-1) [value], clamped to [range]. A value between two
 * steps goes to the step in that direction, not past it.
 */
private fun adjacentStep(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    direction: Int,
): Float {
    val position = (value - range.start) / step
    val index =
        if (direction > 0) floor(position + STEP_TOLERANCE) + 1
        else ceil(position - STEP_TOLERANCE) - 1
    return (range.start + index * step).coerceIn(range.start, range.endInclusive)
}
