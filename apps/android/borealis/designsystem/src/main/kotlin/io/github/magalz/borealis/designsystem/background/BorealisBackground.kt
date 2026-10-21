package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved
import io.github.magalz.borealis.designsystem.theme.rememberPowerSaveMode
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** Tags of the pieces of a video background, for the tests. */
internal object BackgroundTags {
    const val STILL = "background:still"
    const val VIDEO = "background:video"
}

/**
 * The animated background of the Library: [style] fills the bounds of [modifier]. It is meant to
 * lie behind the screens of a window and fill it, so a video can be cut from the middle to fit.
 *
 * - [BackgroundStyle.Off] is the plain page colour.
 * - The two Compose styles redraw about [framesPerSecond] times a second and recompose nothing.
 * - A video style plays its clip while the screen is on, and holds no decoder when it is not.
 * - When the system removes animations, the battery saver is on or the background is [frozen], a
 *   Compose style is one still frame and a video style is the still of its first frame: nothing
 *   plays and no player is made.
 * - With high contrast the page colour lies over the style at [BorealisTokens.HighContrast.Dim], so
 *   that text straight on the background keeps 7:1 over its brightest pixel.
 *
 * [frozen] is how a [GlassHost] holds the background still while a dialog or a sheet is open over
 * it. [framesPerSecond] is the pace of the Compose styles; a clip plays at its own.
 */
@Composable
internal fun BorealisBackground(
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    frozen: Boolean = false,
    framesPerSecond: Int = DRAWN_FRAMES_PER_SECOND,
) {
    val animationsRemoved = rememberAnimationsRemoved()
    val powerSaving = rememberPowerSaveMode()
    val still = animationsRemoved || powerSaving || frozen
    val clip = style.clip
    val page = LocalBorealisColors.current.page
    // The box and the veil are always there, so that a change of look moves nothing in the tree: a
    // video player under it is not made again.
    Box(modifier) {
        val fill = Modifier.fillMaxSize()
        when {
            style == BackgroundStyle.Off -> Box(fill.background(page))
            clip != null -> VideoBackground(clip, stillOnly = still, modifier = fill)
            else ->
                DrawnBackground(
                    style,
                    animated = !still,
                    modifier = fill,
                    framesPerSecond = framesPerSecond,
                )
        }
        val dim =
            if (LocalBorealisLook.current.highContrast) BorealisTokens.HighContrast.Dim else 0f
        if (dim > 0f && style != BackgroundStyle.Off) {
            Box(fill.background(page.copy(alpha = dim)))
        }
    }
}
