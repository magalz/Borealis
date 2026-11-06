package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.zIndex

/**
 * Draws the element over its siblings while it has focus. The focus outline reaches 5dp outside an
 * element, 7dp when it is the thicker one
 * ([io.github.magalz.borealis.designsystem.focus.borealisFocusIndication]), and the keys of a
 * keyboard and the segments of a control sit closer than that, so a neighbour drawn later would
 * cover part of the outline. Put this modifier before the focus indication.
 *
 * Drawing an element last also moves it to the end of the semantics children of its parent, so a
 * screen reader may read it after its siblings. The row of lifted elements has [readingOrderGroup]
 * and each element has its [readingOrderIndex], as [SegmentedControl] does.
 */
@Composable
internal fun Modifier.liftedWhenFocused(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.zIndex(if (focused) 1f else 0f).onFocusChanged { focused = it.hasFocus }
}

/**
 * Makes the row a traversal group: a screen reader sorts the children of the group by their
 * [readingOrderIndex] and reads them in that order. Put it on the row of elements that have
 * [liftedWhenFocused] and give each element its [readingOrderIndex]: the lift moves the focused
 * element to the end of the semantics children of the row (BORE-14), and without the pair TalkBack
 * would read it after the others. A test reads the children the way TalkBack sorts them
 * (`SegmentedControlTest`, `StreamKeyboardTest`).
 */
fun Modifier.readingOrderGroup(): Modifier = semantics { isTraversalGroup = true }

/**
 * Gives the element its place in the [readingOrderGroup] of its row: the [index] it has on the
 * screen, which the lift of [liftedWhenFocused] does not change.
 */
fun Modifier.readingOrderIndex(index: Int): Modifier = semantics {
    traversalIndex = index.toFloat()
}
