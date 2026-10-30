package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

private val MAX_WIDTH = 360.dp
private val BORDER_WIDTH = 1.dp
private const val WIDTH_FRACTION = 0.52f

/**
 * The alpha of the page colour that dims what is behind a panel or a popup over the stream: lighter
 * than the dialog scrim (`scrim` in the tokens, the page at 72%), so the game stays visible. The
 * side panel takes it, and so do the popups that the stream chrome draws over the game.
 */
const val STREAM_SCRIM_ALPHA = 0.35f

/**
 * A panel over the right of its parent: it fills the parent, with a scrim over the part the panel
 * leaves free, and the panel is the smaller of 360dp and 52% of the parent wide. [content] scrolls
 * when it is taller than the panel. A tap on the scrim calls [onDismiss]; a tap inside the panel
 * stays in it. Back is not handled here: the screen that hosts the panel routes it, since on the
 * stream Back must close the panel before it does anything else.
 *
 * The panel has a hairline on its left edge only. The scrim is the page colour at 35%, lighter than
 * the dialog scrim so the stream stays visible.
 *
 * The panel is the `surface` colour, or [panelBackground] where a screen draws something else
 * behind it: the in-game menu draws the glass over the video there. A background is what the panel
 * is drawn on, it takes the size of the panel and no more.
 *
 * The panel clips and scrolls its content, and the focus outline of an item reaches 5dp outside it,
 * 7dp when it is the thicker one (see
 * [io.github.magalz.borealis.designsystem.focus.borealisFocusIndication]). Put at least 8dp of
 * horizontal padding around rows and other focusable items, and 10dp between them, or the outline
 * is cut at the edge of the panel.
 */
@Composable
fun SidePanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    panelBackground: Modifier? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBorealisColors.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.page.copy(alpha = STREAM_SCRIM_ALPHA))
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(minOf(MAX_WIDTH, maxWidth * WIDTH_FRACTION))
                .then(panelBackground ?: Modifier.background(colors.surface))
                .drawBehind { drawRect(colors.line, size = Size(BORDER_WIDTH.toPx(), size.height)) }
                // Taps that no child took stop here, so they do not reach the scrim.
                .pointerInput(Unit) { detectTapGestures() }
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}
