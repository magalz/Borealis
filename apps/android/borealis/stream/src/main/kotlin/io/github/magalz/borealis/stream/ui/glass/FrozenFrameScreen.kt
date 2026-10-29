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
 * The screen of the failed stream that has a frame: [frame], the last one of the video, frozen
 * behind [content], which is the error popup. Where the device can blur and the settings allow it
 * the frame is blurred with the glass of the menu ([frozenFrameLook]) and sharp otherwise, and the
 * popup is the glass over it where [menuGlass], the menu glass option, is Blur. The scrim of the
 * popup stays over the frame.
 *
 * The glass is [LocalVideoGlass] for [content], as the menu provides it for its Quit popup, so the
 * popup is drawn with the one code. What is blurred is one copy that never changes, so the glass
 * records it once and draws it as it is; no clock and no receiver for the battery saver runs.
 */
@Composable
internal fun OverFrozenFrame(
    frozen: FrozenFrame,
    frame: VideoFrame,
    menuGlass: Boolean,
    content: @Composable () -> Unit,
) {
    val look = LocalBorealisLook.current
    val colors = LocalBorealisColors.current
    val rules =
        remember(look, colors, menuGlass) {
            frozenFrameLook(look, colors, Build.VERSION.SDK_INT, menuGlass)
        }
    val glass = rules.blur?.let { rememberFrozenGlass(frozen, it) }
    Box(Modifier.fillMaxSize().frozenFrameBackdrop(frame, glass)) {
        CompositionLocalProvider(
            LocalVideoGlass provides glass.takeIf { rules.panelIsGlass },
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
 * sharp frame where there is no glass.
 */
private fun Modifier.frozenFrameBackdrop(frame: VideoFrame, glass: VideoGlass?): Modifier =
    drawBehind {
        if (glass != null) {
            glass.recordBackdrop(this)
            glass.drawBlurred(this)
        } else {
            drawSharp(frame)
        }
    }

/** The frame as it is, over black where the video is not, at the place and scale of the video. */
private fun DrawScope.drawSharp(frame: VideoFrame) {
    drawRect(Color.Black)
    translate(frame.left, frame.top) {
        scale(frame.scaleX, frame.scaleY, Offset.Zero) { drawImage(frame.image) }
    }
}
