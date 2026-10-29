package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.stream.BackgroundMode
import io.github.magalz.borealis.stream.StreamVisuals

/**
 * The background that the chrome puts behind a screen outside the game, held on one frame of the
 * default style, so that a screenshot of the screen is the same every run.
 */
@Composable
internal fun OnStillBackground(content: @Composable () -> Unit) {
    OutsideGameBackground(StreamVisuals(background = BackgroundMode.STILL), false, content)
}
