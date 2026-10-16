package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex

/**
 * Draws the element over its siblings while it has focus. The focus outline reaches 5dp outside an
 * element ([io.github.magalz.borealis.designsystem.focus.borealisFocusIndication]), and the keys of
 * a keyboard and the segments of a control sit closer than that, so a neighbour drawn later would
 * cover part of the outline. Put this modifier before the focus indication.
 */
@Composable
internal fun Modifier.liftedWhenFocused(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.zIndex(if (focused) 1f else 0f).onFocusChanged { focused = it.hasFocus }
}
