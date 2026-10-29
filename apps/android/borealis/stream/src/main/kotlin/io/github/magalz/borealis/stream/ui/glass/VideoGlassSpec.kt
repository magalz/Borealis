package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.runtime.Immutable
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook

/**
 * What the glass over the stream video is: [tintPercent] of the `surface` colour over the video
 * blurred by [blurDp], with the text [colors] that keep 4.5:1 over it.
 */
@Immutable
internal class VideoGlassSpec(val tintPercent: Int, val blurDp: Int, val colors: BorealisColors)

/** Not decided yet. */
@Suppress("UnusedParameter")
internal fun videoGlassSpec(
    look: BorealisLook,
    colors: BorealisColors,
    sdk: Int,
    batterySaver: Boolean,
    animationsRemoved: Boolean,
): VideoGlassSpec? = null
