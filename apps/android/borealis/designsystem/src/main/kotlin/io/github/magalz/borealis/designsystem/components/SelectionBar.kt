package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook

private val BAR_THICKNESS = 3.dp

/** The part of the height of an item that a vertical bar fills; it is centred. */
private const val VERTICAL_LENGTH = 0.5f

/** How far a horizontal bar stops short of each side, and of the bottom edge. */
private val HORIZONTAL_INSET = 8.dp
private val BOTTOM_GAP = 2.dp

/**
 * The indicator bar of a selected item, drawn in the `accent` inside the item, over its tint: the
 * white accent is too near the text colour for its colour alone to show a selection, so with it a
 * selected segment, rail item and Settings group also have a bar (ADR 0058, decision 5). The other
 * accents have none, and so has an item that is not selected. The bar keeps 3:1 as a non-text
 * indicator against the tint over the page, the surfaces and the glass.
 *
 * A [vertical] bar is on the leading edge and half the height of the item, for a rail item or a
 * group of a list. A horizontal one is along the bottom edge, for a segment. A screen reader hears
 * nothing of it: the selected state is already in the semantics of the item. Put it after the
 * background and the clip of the item, so that it lies on the tint and inside the shape.
 */
@Composable
internal fun Modifier.selectionBar(selected: Boolean, vertical: Boolean): Modifier {
    if (!selected || LocalBorealisLook.current.accent != AccentChoice.White) return this
    val color = LocalBorealisColors.current.accent
    return drawBehind {
        val thickness = BAR_THICKNESS.toPx()
        val corner = CornerRadius(thickness / 2)
        if (vertical) {
            val length = size.height * VERTICAL_LENGTH
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - thickness else 0f
            drawRoundRect(
                color,
                Offset(x, (size.height - length) / 2),
                Size(thickness, length),
                corner,
            )
        } else {
            val inset = HORIZONTAL_INSET.toPx()
            val y = size.height - thickness - BOTTOM_GAP.toPx()
            drawRoundRect(color, Offset(inset, y), Size(size.width - 2 * inset, thickness), corner)
        }
    }
}
