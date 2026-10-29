package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import androidx.compose.runtime.Immutable
import io.github.magalz.borealis.designsystem.components.TextHaloStyle
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency

/** The tint and the blur of [Transparency.Reduced] (ADR 0057), which are the same in the stream. */
private const val REDUCED_TINT_PERCENT = 85
private const val REDUCED_BLUR_DP = 48
private const val PERCENT = 100

/**
 * The halo of the text on the glass: 3 dp wide, blurred by 2 dp, 100 percent opaque and filled. The
 * halo of a text on a background is 80 percent and one pass, which keeps ink and ink-2 but leaves
 * the danger ink of the Quit row at 4.40:1 on a TV over the lightest glass in the model of
 * `contrast.py halo`, and in what is drawn the dot of an i or a bullet has a hole in its halo
 * (3.7:1 for the red of Quit at the dot of its i). 100 percent in three passes keeps the four text
 * colours of the menu at 4.5:1 over a white frame in what is drawn (`TextHaloTest`), and in the
 * model (`VideoGlassHaloTest`).
 */
internal val VIDEO_GLASS_HALO = TextHaloStyle(opacity = 1f, strokeDp = 3, filled = true)

/**
 * What the glass over the stream video is: [tintPercent] of the `surface` colour over the video
 * blurred by [blurDp]. The text on it has a halo ([VIDEO_GLASS_HALO]) and the [colors] of a text
 * with a halo; what sits on the solid surface keeps [surfaceColors], those of the look.
 */
@Immutable
internal class VideoGlassSpec(
    val tintPercent: Int,
    val blurDp: Int,
    val colors: BorealisColors,
    val surfaceColors: BorealisColors,
)

/**
 * The glass over the stream video for a viewer's [look], or null where the surface is solid (ADR
 * 0061, decision 3): below API 31 (nothing blurs), with the transparency Off, with high contrast,
 * with Reduce motion ([animationsRemoved], the setting of the app resolved with the system's) and
 * while the [batterySaver] is on.
 *
 * Standard takes the tint and the blur of the look, Reduced 85 percent at 48 dp, and the tint is
 * never raised for the contrast: a video is any frame, so the text on the glass stands on a halo
 * and not on what is behind it (decision 4, as the owner decided on 2026-10-29). As on a background
 * with a halo, ink-3 and ink-4 are ink-2, which the halo keeps at 4.5:1 and which they do not reach
 * on their own.
 */
internal fun videoGlassSpec(
    look: BorealisLook,
    colors: BorealisColors,
    sdk: Int,
    batterySaver: Boolean,
    animationsRemoved: Boolean,
): VideoGlassSpec? {
    if (isSolid(look, sdk, batterySaver, animationsRemoved)) return null
    val reduced = look.transparency == Transparency.Reduced
    val tint = if (reduced) REDUCED_TINT_PERCENT else look.glassTintPercent.coerceIn(0, PERCENT)
    val blur = if (reduced) REDUCED_BLUR_DP else look.glassBlurDp
    return VideoGlassSpec(tint, blur, colors.copy(ink3 = colors.ink2, ink4 = colors.ink2), colors)
}

private fun isSolid(look: BorealisLook, sdk: Int, batterySaver: Boolean, removed: Boolean) =
    sdk < Build.VERSION_CODES.S ||
        look.highContrast ||
        look.transparency == Transparency.Off ||
        batterySaver ||
        removed

/**
 * What the error popup draws over the frozen last frame of the stream: [blur] is the glass that the
 * frame is blurred with, or null where it shows sharp, and [panelIsGlass] says whether the panel of
 * the popup is that glass over the blurred frame, or the solid surface.
 */
internal class FrozenFrameLook(val blur: VideoGlassSpec?, val panelIsGlass: Boolean)

/**
 * The look of the error popup over the frozen last frame for a viewer's [look] (the owner's
 * decision of 2026-10-29). The frame is always behind the popup. It is blurred where the device can
 * blur and the settings allow it, which is where [videoGlassSpec] has a glass (API 31, no
 * Transparency Off, no high contrast) and with that glass's blur; elsewhere it shows sharp, under
 * the scrim. The panel is that glass over the blurred frame where [menuGlass], the menu glass
 * option, is Blur, and the solid surface otherwise. Reduce motion and the battery saver are not
 * asked: the frame is still and copied once.
 */
internal fun frozenFrameLook(
    look: BorealisLook,
    colors: BorealisColors,
    sdk: Int,
    menuGlass: Boolean,
): FrozenFrameLook {
    val blur = videoGlassSpec(look, colors, sdk, batterySaver = false, animationsRemoved = false)
    return FrozenFrameLook(blur, panelIsGlass = blur != null && menuGlass)
}
