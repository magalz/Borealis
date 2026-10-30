package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook

/**
 * What stands behind [content], the error popup of a stream that failed, over the glass host of the
 * screens outside the game, from the moment the copy of the last frame is asked for:
 * - while the copy is pending, the black that the frame is drawn over, so the background of the
 *   viewer does not show for the few frames that the copy takes;
 * - the frame of [frozen], frozen, once it has arrived: blurred with the glass of the menu where
 *   the device can blur and the settings allow it ([frozenFrameLook]), sharp otherwise, and the
 *   popup is the glass over it where [menuGlass], the menu glass option, is Blur;
 * - nothing where no copy was asked for or the copy failed: the background of the host shows.
 *
 * The one call site of [content] is the same in all three, so the popup is composed once, and keeps
 * its focus and what a screen reader has said of it, when the frame arrives or the copy fails. The
 * popup is over the game while the frame is there or pending ([LocalPopupOverGame]): its scrim is
 * the page at 35 percent over the frame, and its glass panel is firmer than the menu's. With no
 * copy it is a popup over the background, and the glass of the background decides.
 *
 * The glass is [LocalVideoGlass] for [content], as the menu provides it for its Quit popup, so the
 * popup is drawn with the one code. The copy never changes, and the glass records it again only
 * when the layer is drawn, which is when something on the popup changes, a focus outline say; no
 * clock runs for it, as it does for the live copies of the menu.
 */
@Composable
internal fun OverFrozenFrame(
    frozen: FrozenFrame,
    menuGlass: Boolean,
    content: @Composable () -> Unit,
) {
    val look = LocalBorealisLook.current
    val colors = LocalBorealisColors.current
    val rules =
        remember(look, colors, menuGlass) {
            frozenFrameLook(look, colors, Build.VERSION.SDK_INT, menuGlass)
        }
    val frame = frozen.frame
    val glass = if (frame != null) rules.blur?.let { rememberFrozenGlass(frozen, it) } else null
    Box(Modifier.fillMaxSize().frozenFrameBackdrop(frame, frozen.pending, glass)) {
        CompositionLocalProvider(
            LocalVideoGlass provides glass.takeIf { rules.panelIsGlass },
            LocalPopupOverGame provides (frame != null || frozen.pending),
            content = content,
        )
    }
}

@Composable
private fun rememberFrozenGlass(frozen: FrozenFrame, spec: VideoGlassSpec): VideoGlass {
    val blurred = rememberGraphicsLayer()
    val small = rememberGraphicsLayer()
    return remember(spec, frozen, blurred, small) {
        VideoGlass(spec, { frozen.frame }, blurred, small)
    }
}

/**
 * Draws [frame] behind what is inside: the blurred copy of [glass], which records it first, or the
 * sharp frame where there is no glass; the black alone while the copy is [pending]; nothing else.
 */
private fun Modifier.frozenFrameBackdrop(
    frame: VideoFrame?,
    pending: Boolean,
    glass: VideoGlass?,
): Modifier = drawBehind {
    when {
        frame != null && glass != null -> {
            glass.recordBackdrop(this)
            glass.drawBlurred(this)
        }
        frame != null -> drawSharp(frame)
        pending -> drawRect(Color.Black)
    }
}

/** The frame as it is, over black where the video is not, at the place and scale of the video. */
private fun DrawScope.drawSharp(frame: VideoFrame) {
    drawRect(Color.Black)
    translate(frame.left, frame.top) {
        scale(frame.scaleX, frame.scaleY, Offset.Zero) { drawImage(frame.image) }
    }
}
