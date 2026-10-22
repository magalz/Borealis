package io.github.magalz.borealis.library.hosts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * The heights that the host cards need, so that the cards of a row are as high as the tallest of
 * them. A grid sizes each of its items on its own, and the only place where a card can learn what
 * its neighbours need is here: each card tells what it needs for its width ([need]), and asks for
 * the tallest of its row ([tallest]). The state is read when a card is laid out, so a card that is
 * laid out before its neighbours are known is laid out again, in the next frame, with their
 * heights. The intrinsic height of a card is not what it needs (the tags that wrap are not in it),
 * so a card is measured as it is first.
 */
internal class RowHeights {
    private val needed = mutableStateMapOf<String, Int>()

    fun need(id: String, height: Int) {
        if (needed[id] != height) needed[id] = height
    }

    fun tallest(row: List<String>): Int = row.maxOf { needed[it] ?: 0 }
}

/**
 * Makes the card [id] as high as the tallest of the cards of its [row] ([heights]), and no lower
 * than what it needs itself, which keeps the height token of a TV as its least.
 */
internal fun Modifier.asHighAsItsRow(heights: RowHeights, id: String, row: List<String>): Modifier =
    layout { measurable, constraints ->
        val own = measurable.measure(constraints)
        heights.need(id, own.height)
        val tallest = heights.tallest(row)
        // A card that is the tallest, or alone in its row, is measured once.
        val placeable =
            if (own.height >= tallest) {
                own
            } else {
                measurable.measure(
                    constraints.copy(
                        minHeight = tallest.coerceIn(constraints.minHeight, constraints.maxHeight)
                    )
                )
            }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The rows that the host cards make in a grid of [columns]. On every form factor each card is as
 * high as the tallest of its row ([asHighAsItsRow]); a grid of one column, such as a phone has, has
 * no row to share a height in, and a card is as high as its content.
 */
internal class HostRows(
    val heights: RowHeights,
    val columns: Int,
    val ids: List<List<String>>,
)

/**
 * Gives the host card [id], the [index]th of the grid, the height of its row where a row has more
 * than one card.
 */
internal fun Modifier.inHostRow(rows: HostRows, index: Int, id: String): Modifier =
    if (rows.columns > 1) asHighAsItsRow(rows.heights, id, rows.ids[index / rows.columns]) else this

@Composable
internal fun rememberHostRows(items: List<HostItemUiState>, columns: Int): HostRows {
    val heights = remember { RowHeights() }
    return remember(items, columns) {
        HostRows(heights, columns, items.map { it.id }.chunked(columns))
    }
}
