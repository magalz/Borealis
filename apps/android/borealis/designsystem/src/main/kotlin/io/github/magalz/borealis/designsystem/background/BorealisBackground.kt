package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved
import io.github.magalz.borealis.designsystem.theme.rememberPowerSaveMode

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
 * - The two Compose styles redraw about 30 times a second and recompose nothing.
 * - A video style plays its clip while the screen is on, and holds no decoder when it is not.
 * - When the system removes animations or the battery saver is on, a Compose style is one still
 *   frame and a video style is the still of its first frame: nothing plays and no player is made.
 */
@Composable
fun BorealisBackground(style: BackgroundStyle, modifier: Modifier = Modifier) {
    val animationsRemoved = rememberAnimationsRemoved()
    val powerSaving = rememberPowerSaveMode()
    val still = animationsRemoved || powerSaving
    val clip = style.clip
    when {
        style == BackgroundStyle.Off -> Box(modifier.background(LocalBorealisColors.current.page))
        clip != null -> VideoBackground(clip, stillOnly = still, modifier = modifier)
        else -> DrawnBackground(style, animated = !still, modifier = modifier)
    }
}
