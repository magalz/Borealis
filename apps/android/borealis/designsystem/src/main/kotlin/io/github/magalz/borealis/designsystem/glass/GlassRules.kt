package io.github.magalz.borealis.designsystem.glass

import androidx.compose.runtime.Immutable
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor

/**
 * What a glass surface is: [solid], or [tintPercent] of the surface colour over [blurDp] of blur.
 */
@Immutable
internal data class GlassSpec(val solid: Boolean, val tintPercent: Int, val blurDp: Int) {
    companion object {
        val Solid = GlassSpec(solid = true, tintPercent = 100, blurDp = 0)
    }
}

internal fun glassSpec(look: BorealisLook, style: BackgroundStyle, sdk: Int): GlassSpec =
    GlassSpec.Solid

internal fun tintFloor(style: BackgroundStyle, blurDp: Int): Int = 0

internal fun backdropLuminance(style: BackgroundStyle, blurDp: Int, tintPercent: Int): Double = 1.0

internal fun BorealisColors.onGlass(style: BackgroundStyle, spec: GlassSpec): BorealisColors = this

internal fun glassFrameRate(spec: GlassSpec, formFactor: FormFactor): Int = 30
