package io.github.magalz.borealis.designsystem.glass

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.DRAWN_FRAMES_PER_SECOND
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import kotlin.math.max

/**
 * The tint and the blur of [Transparency.Reduced], and of the glass of a device that cannot blur.
 */
private const val REDUCED_TINT_PERCENT = 85
private const val REDUCED_BLUR_DP = 48
private const val NO_BLUR_DP = 0
private const val PERCENT = 100

/** The pace of the drawn background on a TV while glass is drawn: the blur fits it (ADR 0057). */
internal const val TV_GLASS_FRAMES_PER_SECOND = 24

/**
 * The lightest the secondary text of a glass is allowed to get: `#A8ACB1`, which is what Flowing
 * light needs at 40 percent and 8 dp. Beyond it ink-3 would stand too near ink-2 to be a step.
 */
private val SECONDARY_BOUND = luminance(Color(0xFFA8ACB1))

/**
 * What a glass surface is: [solid], or [tintPercent] of the `surface` colour over a backdrop that
 * is blurred by [blurDp] (0 is no blur).
 */
@Immutable
internal data class GlassSpec(val solid: Boolean, val tintPercent: Int, val blurDp: Int) {
    companion object {
        /** Today's opaque `surface`. */
        val Solid = GlassSpec(solid = true, tintPercent = PERCENT, blurDp = NO_BLUR_DP)
    }
}

/**
 * The glass that a viewer's [look] makes over the background [style] on an Android of level [sdk]
 * (ADR 0057):
 *
 * - High contrast, the transparency Off and the plain page are solid.
 * - Reduced is 85 percent at 48 dp.
 * - Standard is the tint and the blur of the look, with the tint raised to the floor of the
 *   background.
 * - Below API 31 there is no blur to draw, so every level but Off is 85 percent with none.
 */
internal fun glassSpec(look: BorealisLook, style: BackgroundStyle, sdk: Int): GlassSpec {
    if (
        look.highContrast || look.transparency == Transparency.Off || style == BackgroundStyle.Off
    ) {
        return GlassSpec.Solid
    }
    val canBlur = sdk >= Build.VERSION_CODES.S
    return if (look.transparency == Transparency.Reduced || !canBlur) {
        GlassSpec(false, REDUCED_TINT_PERCENT, if (canBlur) REDUCED_BLUR_DP else NO_BLUR_DP)
    } else {
        val tint = look.glassTintPercent.coerceIn(0, PERCENT)
        GlassSpec(false, max(tint, tintFloor(style, look.glassBlurDp)), look.glassBlurDp)
    }
}

/**
 * The lowest tint of the grid at which a glass of [blurDp] over [style] keeps `ink`, `ink-2` and
 * `accent-soft` at 4.5:1 as they are, and keeps the adjusted `ink-3` no lighter than `#A8ACB1`. The
 * glass is never more transparent than this. It is 100 where no tint of the grid does.
 */
internal fun tintFloor(style: BackgroundStyle, blurDp: Int): Int {
    val tokens = BorealisColors()
    return GlassContrastTable.tintsPercent.firstOrNull { tint ->
        val backdrop = backdropLuminance(style, blurDp, tint)
        val unchanged = listOf(tokens.ink, tokens.ink2, tokens.accentSoft)
        unchanged.all { contrast(it, backdrop) >= AA_RATIO } &&
            luminance(walk(tokens.ink3, tokens.ink, secondaryTarget(backdrop))) <= SECONDARY_BOUND
    } ?: PERCENT
}

/**
 * The luminance of the brightest pixel behind the text of a glass of [style], [blurDp] and
 * [tintPercent]. A blur or a tint between two of the grid is read as the next one below it, which
 * is the brighter backdrop, so the text colours err on the side of contrast.
 */
internal fun backdropLuminance(style: BackgroundStyle, blurDp: Int, tintPercent: Int): Double {
    val row = GlassContrastTable.blursDp.indexOfLast { it <= blurDp }.coerceAtLeast(0)
    val column = GlassContrastTable.tintsPercent.indexOfLast { it <= tintPercent }.coerceAtLeast(0)
    return GlassContrastTable.backdropLuminance.getValue(style)[row][column]
}

/**
 * These colours as the content of a glass of [spec] over [style] sees them: ink-3 and ink-4 are
 * each the colour nearest to themselves, on the way to ink, that keeps 4.5:1 with a 2 percent
 * margin over the brightest pixel behind the glass. A solid glass changes nothing.
 */
internal fun BorealisColors.onGlass(style: BackgroundStyle, spec: GlassSpec): BorealisColors {
    if (spec.solid) return this
    val target = secondaryTarget(backdropLuminance(style, spec.blurDp, spec.tintPercent))
    return withSecondaryInk(walk(ink3, ink, target), walk(ink4, ink, target))
}

/** How fast the drawn background runs: 24 frames a second on a TV while glass is drawn, else 30. */
internal fun glassFrameRate(spec: GlassSpec, formFactor: FormFactor): Int =
    if (!spec.solid && formFactor == FormFactor.Tv) TV_GLASS_FRAMES_PER_SECOND
    else DRAWN_FRAMES_PER_SECOND
