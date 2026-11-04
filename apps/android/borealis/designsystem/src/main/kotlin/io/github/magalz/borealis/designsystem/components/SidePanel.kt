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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

private val MAX_WIDTH = 360.dp
private val BORDER_WIDTH = 1.dp
private const val WIDTH_FRACTION = 0.52f
private const val PHONE_MAX_FRACTION = 3f / 4f

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
 * On a phone the panel grows with the text: when the font scale of the composition (the system's
 * and the Text size setting, as `LocalDensity` holds them) is above 1, both limits above are
 * multiplied by it, up to three quarters of the parent. What the width has to hold is text that
 * does not wrap, such as the segments of an option row (the in-game menu's Touch row has four), and
 * that text grows with the font scale while the padding and the 48dp touch size stay in dp. Three
 * quarters is what that row needs at the largest text size on a 640dp phone, with every font and
 * text spacing, and it leaves a quarter of the stream visible. At a font scale of 1 or less, on a
 * TV and on a handheld, the width is the one above.
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
    val phone = LocalFormFactor.current == FormFactor.Phone
    val fontScale = LocalDensity.current.fontScale
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
                .width(panelWidth(maxWidth, phone, fontScale))
                .then(panelBackground ?: Modifier.background(colors.surface))
                .drawBehind { drawRect(colors.line, size = Size(BORDER_WIDTH.toPx(), size.height)) }
                // Taps that no child took stop here, so they do not reach the scrim.
                .pointerInput(Unit) { detectTapGestures() }
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/** The width of the panel in a parent [parentWidth] wide: the rule in the KDoc of [SidePanel]. */
private fun panelWidth(parentWidth: Dp, phone: Boolean, fontScale: Float): Dp {
    val width = minOf(MAX_WIDTH, parentWidth * WIDTH_FRACTION)
    if (!phone) return width
    return minOf(width * maxOf(1f, fontScale), parentWidth * PHONE_MAX_FRACTION)
}
