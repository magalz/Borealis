package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHost
import io.github.magalz.borealis.stream.StreamVisuals

/** Marks the host of the background behind the screens outside the game, for the tests. */
internal const val OUTSIDE_GAME_BACKGROUND_TAG = "outside_game_background"

/**
 * The background behind every screen outside the game (ADR 0061): Starting, the question about a
 * running app, a failure, Quitting and Disconnecting, in a glass host so that their panels are the
 * glass of the Library. It is the style that the viewer chose, as the Library shows it ([visuals]
 * carry it; Off is the plain page): the host is the Library's, so the intensity, Transparency,
 * Reduce motion, High contrast and the battery saver, which hold it still or dim it, are those of
 * the look and of the device, and an open dialog blurs it and holds it still as it does there.
 *
 * [decoderHeld] says that a decoder of the stream is up: a clip then shows its still and its player
 * is released, so the two never compete, and a drawn style keeps moving. The screens are [content];
 * they paint no page of their own.
 *
 * Where [showsBackground] is false, something else covers the background: the frozen last frame of
 * the stream behind the error popup, or its black while the copy is pending. The host stays, so
 * that the screen in [content] is not made again when the background comes or goes, but it draws
 * the plain page, which makes the glass solid, and it does not carry the tag of the background.
 */
@Composable
internal fun OutsideGameBackground(
    visuals: StreamVisuals,
    decoderHeld: Boolean,
    showsBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassHost(
        if (showsBackground) visuals.backgroundStyle else BackgroundStyle.Off,
        if (showsBackground) Modifier.testTag(OUTSIDE_GAME_BACKGROUND_TAG) else Modifier,
        clipsPlay = !decoderHeld,
        content = content,
    )
}
