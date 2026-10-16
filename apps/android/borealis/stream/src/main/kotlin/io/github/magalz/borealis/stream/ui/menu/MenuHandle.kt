package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.LocalMenuHandleBounds

/** How far below the top edge of the screen the handle sits, in dp. */
internal const val MENU_HANDLE_TOP_OFFSET_DP = 12

private val TAB_WIDTH = 64.dp
private val TAB_HEIGHT = 20.dp
private val TOUCH_WIDTH = 88.dp
private val TOUCH_HEIGHT = 36.dp
private const val TAB_ALPHA = 0.55f

/**
 * The tab at the top of the stream on a device with a touchscreen: a tap or a downward pull on it
 * calls [onOpen]. The tab is 64x20dp; the area that takes the touch reaches 12dp past it at the
 * sides and 16dp below it, so it is easy to hit and still starts below the edge the system bars
 * swipe from. The caller places it [MENU_HANDLE_TOP_OFFSET_DP] below the top edge.
 *
 * It reports its bounds to `LocalMenuHandleBounds`, which the chrome host reads to pass a touch
 * that starts there to Compose while the stream has the input, and reports null when it leaves the
 * screen.
 */
@Composable
internal fun MenuHandle(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val report = LocalMenuHandleBounds.current
    DisposableEffect(report) { onDispose { report(null) } }
    val open by rememberUpdatedState(onOpen)
    val label = stringResource(R.string.stream_menu_handle)
    Box(
        modifier
            .size(TOUCH_WIDTH, TOUCH_HEIGHT)
            .onGloballyPositioned { report(it.boundsInRoot()) }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    open()
                    true
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { open() }) }
            .pointerInput(Unit) { pullDown { open() } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Tab()
    }
}

/** Calls [onPull] once for a drag that starts moving down. */
private suspend fun PointerInputScope.pullDown(onPull: () -> Unit) {
    var pulled = false
    detectVerticalDragGestures(onDragStart = { pulled = false }) { change, dragAmount ->
        if (dragAmount > 0f && !pulled) {
            pulled = true
            change.consume()
            onPull()
        }
    }
}

@Composable
private fun Tab() {
    val colors = LocalBorealisColors.current
    Box(
        Modifier.size(TAB_WIDTH, TAB_HEIGHT)
            .background(colors.page.copy(alpha = TAB_ALPHA), LocalBorealisShapes.current.pill),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(colors.ink)
    }
}
