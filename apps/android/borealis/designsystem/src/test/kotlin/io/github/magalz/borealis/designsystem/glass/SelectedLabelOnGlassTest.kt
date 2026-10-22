package io.github.magalz.borealis.designsystem.glass

import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Test

/**
 * The label of a selected item is accent-soft over the accent tint over the glass (ADR 0058,
 * decision 5): it keeps 4.5:1 for every accent, where the accent that it replaces is 3.21:1 at
 * worst for violet. `contrast.py accents` measured the brightest pixel behind the label, which is
 * the glass with the tint of the accent laid on it, over every frame of both devices (Table V of
 * `docs/ux/accessibility.md`); [LAID], in `SelectedBackdrops.kt`, pins those luminances, rounded
 * up, and this is the check of the app against them. The white indicator bar is the accent over the
 * same backdrop, and keeps 3:1 as a non-text indicator.
 */
class SelectedLabelOnGlassTest {
    private fun spec(accent: AccentChoice, style: BackgroundStyle, transparency: Transparency) =
        glassSpec(BorealisLook(accent = accent, transparency = transparency), style, SDK)

    private fun forEachGlass(
        check: (accent: AccentChoice, style: BackgroundStyle, laid: Double, plain: Double) -> Unit
    ) {
        for ((accent, styles) in LAID) {
            for ((style, laid) in styles) {
                for ((level, measured) in
                    listOf(
                        Transparency.Standard to laid.standard,
                        Transparency.Reduced to laid.reduced,
                    )) {
                    val glass = spec(accent, style, level)
                    val plain = backdropLuminance(style, glass.blurDp, glass.tintPercent)
                    check(accent, style, measured, plain)
                }
            }
        }
    }

    @Test
    fun `accent-soft keeps 4_5 to 1 over its tint on the glass of every style for every accent`() {
        forEachGlass { accent, style, laid, _ ->
            val soft = borealisColors(BorealisLook(accent = accent)).accentSoft

            assertWithMessage("$accent over $style").that(contrast(soft, laid)).isAtLeast(AA_RATIO)
        }
    }

    @Test
    fun `the accent that it replaces fell short over Flowing light, where violet had 3_21 to 1`() {
        // The check can fail: the same backdrops with the accent as the label.
        val laid = LAID.getValue(AccentChoice.Violet).getValue(BackgroundStyle.FlowingLight)
        val accent = borealisColors(BorealisLook()).accent

        assertThat(contrast(accent, laid.standard)).isWithin(0.01).of(3.21)
        assertThat(contrast(accent, laid.standard)).isLessThan(AA_RATIO)
    }

    @Test
    fun `the white bar keeps 3 to 1 over its tint on the glass of every style`() {
        val white = borealisColors(BorealisLook(accent = AccentChoice.White)).accent
        val styles = LAID.getValue(AccentChoice.White)

        for ((style, laid) in styles) {
            assertWithMessage("$style default").that(contrast(white, laid.standard)).isAtLeast(3.0)
            assertWithMessage("$style reduced").that(contrast(white, laid.reduced)).isAtLeast(3.0)
        }
    }

    @Test
    fun `the measured backdrops are the glass of the app with a tint laid on it, so they are brighter`() {
        // A table that fell behind the backgrounds would be darker than the glass table of the app.
        forEachGlass { accent, style, laid, plain ->
            assertWithMessage("$accent over $style").that(laid).isAtLeast(plain)
        }
    }

    private companion object {
        const val SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
