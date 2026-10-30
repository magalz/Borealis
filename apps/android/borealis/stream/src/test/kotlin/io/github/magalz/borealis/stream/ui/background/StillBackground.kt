package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHost

/**
 * The background that the chrome puts behind a screen outside the game, held on one frame of the
 * default style, so that a screenshot of the screen is the same every run. The host is the one that
 * [OutsideGameBackground] makes, held still by its owner, which the stream itself never does.
 */
@Composable
internal fun OnStillBackground(content: @Composable () -> Unit) {
    GlassHost(
        BackgroundStyle.Default,
        Modifier.testTag(OUTSIDE_GAME_BACKGROUND_TAG),
        frozen = true,
        content = content,
    )
}
