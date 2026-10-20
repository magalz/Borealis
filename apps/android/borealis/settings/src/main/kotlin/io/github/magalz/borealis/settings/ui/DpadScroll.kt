package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val DPAD_SCROLL_STEP = 96.dp

/**
 * Scrolls [scroll] by a step with the D-pad Up and Down. A long text is one focus stop with no row
 * to move to, so the keys have nothing else to do. At an end of the text the key is not taken, and
 * the focus search moves on from there: Down at the end goes to the button below.
 */
@Composable
internal fun Modifier.dpadScroll(scroll: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { DPAD_SCROLL_STEP.toPx() }
    return onKeyEvent { event ->
        val delta =
            when (event.key) {
                Key.DirectionDown -> stepPx
                Key.DirectionUp -> -stepPx
                else -> 0f
            }
        val canScroll = if (delta > 0) scroll.canScrollForward else scroll.canScrollBackward
        val scrolls = delta != 0f && canScroll
        if (scrolls && event.type == KeyEventType.KeyDown) {
            scope.launch { scroll.animateScrollBy(delta) }
        }
        scrolls
    }
}
