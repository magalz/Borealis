package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import kotlin.math.max

/** The tint and the blur of [Transparency.Reduced] (ADR 0057), which are the same in the stream. */
private const val REDUCED_TINT_PERCENT = 85
private const val REDUCED_BLUR_DP = 48
private const val PERCENT = 100

/** WCAG 2.x: the contrast that text must keep, and the near black added to each luminance. */
private const val AA_RATIO = 4.5f
private const val NEAR_BLACK = 0.05f

/** The text is aimed at 4.5:1 with 2 percent to spare, as the glass of the Library does. */
private const val AA_MARGIN = 1.02f

/** The walk from a secondary ink towards ink goes one percent of the way at a time. */
private const val WALK_STEPS = 100

/**
 * What the glass over the stream video is: [tintPercent] of the `surface` colour over the video
 * blurred by [blurDp], with the text [colors] that keep 4.5:1 over it.
 */
@Immutable
internal class VideoGlassSpec(
    val tintPercent: Int,
    val blurDp: Int,
    val colors: BorealisColors,
    val surfaceColors: BorealisColors = colors,
)

/**
 * The glass over the stream video for a viewer's [look], or null where the surface is solid (ADR
 * 0061, decision 3): below API 31 (nothing blurs), with the transparency Off, with high contrast,
 * with Reduce motion ([animationsRemoved], the setting of the app resolved with the system's) and
 * while the [batterySaver] is on.
 *
 * Standard takes the tint and the blur of the look, Reduced 85 percent at 48 dp. The tint is then
 * raised to the floor of [tintFloor], and [VideoGlassSpec.colors] are [colors] with the secondary
 * inks that keep 4.5:1 over what the tint leaves of the whitest frame.
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
    val effective = max(tint, tintFloor(colors))
    return VideoGlassSpec(effective, blur, colors.overWhitestFrame(effective))
}

private fun isSolid(look: BorealisLook, sdk: Int, batterySaver: Boolean, removed: Boolean) =
    sdk < Build.VERSION_CODES.S ||
        look.highContrast ||
        look.transparency == Transparency.Off ||
        batterySaver ||
        removed

/**
 * The lowest tint, in percent, at which the text of the menu and the popup keeps 4.5:1 as it is
 * over the whitest frame: ink, ink-2, accent-soft and danger-ink (the Quit row), and accent-soft
 * over the tint of a selected row. A video is any frame, so there is no table of what is behind the
 * text, as the Library has for its backgrounds. A blur mixes pixels and never makes one lighter
 * than the lightest, and no frame is lighter than white, so white is the worst case of every frame
 * and of every blur, and a tint that keeps the text over it keeps it over any video. It is 100
 * where no tint does.
 */
private fun tintFloor(colors: BorealisColors): Int =
    (0..PERCENT).firstOrNull { keepsTextOverWhite(colors, it) } ?: PERCENT

private fun keepsTextOverWhite(colors: BorealisColors, tintPercent: Int): Boolean {
    val glass = whitestGlass(colors, tintPercent)
    val laid = colors.accentTint.compositeOver(glass)
    return with(colors) {
        listOf(ink, ink2, accentSoft, dangerInk).all { keeps(it, glass.luminance()) } &&
            keeps(accentSoft, laid.luminance())
    }
}

/** The `surface` colour at [tintPercent] over a white frame. */
private fun whitestGlass(colors: BorealisColors, tintPercent: Int): Color =
    colors.surface.copy(alpha = tintPercent.toFloat() / PERCENT).compositeOver(Color.White)

private fun keeps(text: Color, backdrop: Float): Boolean =
    (text.luminance() + NEAR_BLACK) / (backdrop + NEAR_BLACK) >= AA_RATIO * AA_MARGIN

/**
 * These colours with ink-3 and ink-4 each the colour nearest to itself, on the way to ink, that
 * keeps 4.5:1 with the margin over the whitest frame under a glass of [tintPercent]. Ink and ink-2
 * keep it as they are, by the floor.
 */
private fun BorealisColors.overWhitestFrame(tintPercent: Int): BorealisColors {
    val backdrop = whitestGlass(this, tintPercent).luminance()
    val target = AA_RATIO * AA_MARGIN * (backdrop + NEAR_BLACK) - NEAR_BLACK
    return copy(ink3 = walk(ink3, ink, target), ink4 = walk(ink4, ink, target))
}

/**
 * The first colour on the way from [from] to [towards], in sRGB, one percent at a time, whose
 * luminance reaches [target]; [from] itself if that already does.
 */
private fun walk(from: Color, towards: Color, target: Float): Color {
    for (step in 0..WALK_STEPS) {
        val mixed =
            Color(
                red = from.red + (towards.red - from.red) * step / WALK_STEPS,
                green = from.green + (towards.green - from.green) * step / WALK_STEPS,
                blue = from.blue + (towards.blue - from.blue) * step / WALK_STEPS,
            )
        if (mixed.luminance() >= target) return if (step == 0) from else mixed
    }
    return towards
}
