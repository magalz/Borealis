package io.github.magalz.borealis.designsystem.glass

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BackgroundIntensity
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Test

/**
 * The glass floors and the contrast table of the glass (ADR 0057) are worked out over the
 * background at full strength. The veil of the Background intensity (ADR 0058, decision 8) lies
 * under the glass and pulls each pixel toward the page colour: a pixel brighter than the page gets
 * darker, and one darker than the page, as some pixels of a clip are, is lifted toward it. So the
 * floors stay valid because the veil never raises the brightest pixel of the background: a colour
 * as bright as a backdrop of the table is never brighter once the page colour is veiled over it, at
 * any veil.
 */
class BackgroundVeilFloorsTest {
    private val page = BorealisTokens.Color.Page

    private val veils = BackgroundIntensity.entries.map { it.veil }

    /**
     * The dimmest of the brightest backdrops of the table: no pixel behind the text is below it.
     */
    private val dimmestBrightest =
        GlassContrastTable.backdropLuminance.values.minOf { rows -> rows.minOf { it.min() } }

    @Test
    fun `the page is darker than the brightest backdrop behind the text, for every cell of the table`() {
        assertThat(luminance(page)).isLessThan(dimmestBrightest)
    }

    @Test
    fun `a veil of the page colour never brightens a colour that is as bright as a backdrop`() {
        var tried = 0
        for (red in LEVELS) for (green in LEVELS) for (blue in LEVELS) {
            val colour = Color(red, green, blue)
            if (luminance(colour) < dimmestBrightest) continue
            tried++
            for (veil in veils) {
                assertWithMessage("$colour under a veil of $veil")
                    .that(luminance(veiled(colour, veil)))
                    .isAtMost(luminance(colour))
            }
        }
        assertThat(tried).isGreaterThan(MANY)
    }

    @Test
    fun `the floors of the glass do not move with the intensity`() {
        BackgroundStyle.entries
            .filter { it != BackgroundStyle.Off }
            .forEach { style ->
                val full = glassSpec(BorealisLook(), style, SDK)
                BackgroundIntensity.entries.forEach { level ->
                    val look = BorealisLook(backgroundIntensity = level)

                    assertWithMessage("$style at $level")
                        .that(glassSpec(look, style, SDK))
                        .isEqualTo(full)
                }
            }
    }

    /** [colour] with the page colour over it at [veil], the way an 8 bit frame blends them. */
    private fun veiled(colour: Color, veil: Float) =
        Color(
            red = mix(colour.red, page.red, veil),
            green = mix(colour.green, page.green, veil),
            blue = mix(colour.blue, page.blue, veil),
        )

    private fun mix(colour: Float, over: Float, veil: Float) = colour * (1f - veil) + over * veil

    private companion object {
        /** Every 5th level of a channel, from 0 to 255. */
        val LEVELS = (0..255 step 5).map { it / 255f }

        const val MANY = 1_000
        const val SDK = 35
    }
}
