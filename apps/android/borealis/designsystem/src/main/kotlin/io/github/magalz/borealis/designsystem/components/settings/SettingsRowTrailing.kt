package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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
        val bounded = constraints.hasBoundedWidth
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        var used = 0
        val ending =
            measurables.drop(1).map {
                val available =
                    if (bounded) (width - used).coerceAtLeast(0) else Constraints.Infinity
                it.measure(Constraints(maxWidth = available)).also { part ->
                    used += part.width + gapPx
                }
            }
        val room = if (bounded) width - used else Constraints.Infinity
        val beside =
            ending.isEmpty() ||
                !bounded ||
                measurables[0].minIntrinsicWidth(Constraints.Infinity) <= room
        val text =
            measurables[0].measure(
                when {
                    !bounded -> Constraints()
                    beside -> Constraints(room, room)
                    else -> Constraints(width, width)
                }
            )
        val rowWidth = constraints.constrainWidth(if (bounded) width else text.width + used)
        val tallest = ending.maxOfOrNull { it.height } ?: 0
        if (beside) {
            val height = constraints.constrainHeight(maxOf(text.height, tallest))
            layout(rowWidth, height) {
                text.place(0, centre.align(text.height, height))
                var x = rowWidth
                for (part in ending.asReversed()) {
                    x -= part.width
                    part.place(x, centre.align(part.height, height))
                    x -= gapPx
                }
            }
        } else {
            val stack = STACK_GAP.roundToPx()
            val block = text.height + stack + tallest
            val height = constraints.constrainHeight(block)
            val top = centre.align(block, height)
            layout(rowWidth, height) {
                text.place(0, top)
                var x = rowWidth
                for (part in ending.asReversed()) {
                    x -= part.width
                    part.place(x, top + text.height + stack + centre.align(part.height, tallest))
                    x -= gapPx
                }
            }
        }
    }
}
