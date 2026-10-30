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
import kotlin.math.max

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
 * - The page colour lies over the style at the veil of the Background intensity of the look: 0 at
 *   100 percent, and 0.25, 0.5 and 0.75 at 75, 50 and 25 percent. With high contrast it lies at
 *   [BorealisTokens.HighContrast.Dim] if that is more, so that text straight on the background
 *   keeps 7:1 over its brightest pixel. The veil is part of the background, so the glass blurs it.
 *
 * [frozen] is how a [GlassHost] holds the background still while a dialog or a sheet is open over
 * it, or while its owner holds it. [clipsPlay] off is how the owner takes the decoder from a clip
 * style while another decoder is up (the stream's): the player is released and the still shows, and
 * a Compose style goes on moving. [framesPerSecond] is the pace of the Compose styles; a clip plays
 * at its own.
 */
@Composable
internal fun BorealisBackground(
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    frozen: Boolean = false,
    framesPerSecond: Int = DRAWN_FRAMES_PER_SECOND,
    clipsPlay: Boolean = true,
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
            clip != null -> VideoBackground(clip, stillOnly = still || !clipsPlay, modifier = fill)
            else ->
                DrawnBackground(
                    style,
                    animated = !still,
                    modifier = fill,
                    framesPerSecond = framesPerSecond,
                )
        }
        val look = LocalBorealisLook.current
        val dim = if (look.highContrast) BorealisTokens.HighContrast.Dim else 0f
        val veil = max(look.backgroundIntensity.veil, dim)
        if (veil > 0f && style != BackgroundStyle.Off) {
            Box(fill.background(page.copy(alpha = veil)))
        }
    }
}
