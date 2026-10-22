package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

private val STACK_GAP = 4.dp

/** What a Row centres its children with, so that a child is placed on the same pixel as in one. */
private val centre = Alignment.CenterVertically

/**
 * The [text] of a row of settings and what the row ends in ([trailing]: a value and a chevron). The
 * trailing parts are beside the text, each centred on the height of the row, for as long as the
 * longest word of the text fits in the width that they leave. When no word fits (the text at the
 * largest size, with the letters and words spaced out, in a row of a phone), they go under the
 * text, at the end of the row, so that the text has the whole width and no word is broken.
 *
 * With nothing to end it, the text is the row. A [gap] of room is left between the text and the
 * trailing parts, and between the parts. Beside the text the row is laid out as a Row with the text
 * as its weighted child would lay it out.
 */
@Composable
internal fun RowTextWithTrailing(
    gap: Dp,
    modifier: Modifier = Modifier,
    trailing: List<@Composable () -> Unit> = emptyList(),
    text: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(propagateMinConstraints = true) { text() }
            for (part in trailing) Box { part() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        arrange(measurables, constraints, gap.roundToPx())
    }
}

/** The parts that end a row, measured, and the room that they and the gaps between them take. */
private class Ending(val parts: List<Placeable>, val used: Int)

/** The [parts] of a row's end, each measured with what is left of [width], as a Row measures. */
private fun measureEnding(parts: List<Measurable>, width: Int, gap: Int): Ending {
    var used = 0
    val measured = parts.map {
        val available =
            if (width == Constraints.Infinity) width else (width - used).coerceAtLeast(0)
        it.measure(Constraints(maxWidth = available)).also { part -> used += part.width + gap }
    }
    return Ending(measured, used)
}

private fun MeasureScope.arrange(
    measurables: List<Measurable>,
    constraints: Constraints,
    gap: Int,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    val ending = measureEnding(measurables.drop(1), width, gap)
    val room = if (width == Constraints.Infinity) width else width - ending.used
    val beside = fitsBeside(measurables[0], ending, width, room)
    val body = measurables[0].measure(bodyConstraints(width, room, beside))
    val rowWidth =
        constraints.constrainWidth(
            if (width == Constraints.Infinity) body.width + ending.used else width
        )
    val tallest = ending.parts.maxOfOrNull { it.height } ?: 0
    val stack = if (beside) 0 else STACK_GAP.roundToPx()
    val block = if (beside) maxOf(body.height, tallest) else body.height + stack + tallest
    val height = constraints.constrainHeight(block)
    return layout(rowWidth, height) {
        val top = centre.align(block, height)
        body.place(0, if (beside) centre.align(body.height, height) else top)
        var x = rowWidth
        for (part in ending.parts.asReversed()) {
            x -= part.width
            val y =
                if (beside) centre.align(part.height, height)
                else top + body.height + stack + centre.align(part.height, tallest)
            part.place(x, y)
            x -= gap
        }
    }
}

/** Whether the longest word of [text] fits in [room], beside the [ending] of a row [width] wide. */
private fun fitsBeside(text: Measurable, ending: Ending, width: Int, room: Int): Boolean =
    ending.parts.isEmpty() ||
        width == Constraints.Infinity ||
        text.minIntrinsicWidth(Constraints.Infinity) <= room

/** The text fills [room] beside the ending, as a weighted child of a Row does, or the row. */
private fun bodyConstraints(width: Int, room: Int, beside: Boolean): Constraints =
    when {
        width == Constraints.Infinity -> Constraints()
        beside -> Constraints(room, room)
        else -> Constraints(width, width)
    }
