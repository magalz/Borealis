package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The colour-blind palettes of ADR 0057 (decision 11). A palette replaces the status colours, and
 * is measured the way the contrast tool of `design/accessibility/` simulates colour vision: the
 * model of Machado, Oliveira and Fernandes (2009) at severity 1.0.
 *
 * The measure of "distinct" is the colour difference CIEDE2000 of 20 or more, between every pair of
 * the four status colours as the viewer with that dichromacy sees them. The status colours are also
 * indicators, so each keeps 3:1 on the page and the surfaces (WCAG 1.4.11).
 */
class ColourVisionPaletteTest {
    private val base = BorealisColors()

    private fun fills(colors: BorealisColors) =
        mapOf(
            "success" to colors.success,
            "warning" to colors.warning,
            "danger" to colors.danger,
            "offline" to colors.offline,
        )

    private fun surfaces(colors: BorealisColors) =
        mapOf(
            "page" to colors.page,
            "surface" to colors.surface,
            "surface-raised" to colors.surfaceRaised,
        )

    /** The least difference between two of the status colours of [colors], as [kind] sees them. */
    private fun leastDifference(colors: BorealisColors, kind: Dichromacy): Double {
        val seen = fills(colors).mapValues { it.value.seenBy(kind) }.values.toList()
        return seen.indices.minOf { i ->
            (i + 1 until seen.size).minOfOrNull { j -> deltaE2000(seen[i], seen[j]) }
                ?: Double.MAX_VALUE
        }
    }

    @Test
    fun `the status colours of today are not distinct to a red-green dichromat`() {
        // The measure can fail: today's green and red are too near for a protanope and a
        // deuteranope.
        assertThat(leastDifference(base, Dichromacy.Protanopia)).isLessThan(20.0)
        assertThat(leastDifference(base, Dichromacy.Deuteranopia)).isLessThan(20.0)
    }

    @Test
    fun `the red-green palette keeps every pair of status colours distinct to a protanope and a deuteranope`() {
        val palette = base.withColourVision(ColourVision.RedGreen)

        assertThat(leastDifference(palette, Dichromacy.Protanopia)).isAtLeast(20.0)
        assertThat(leastDifference(palette, Dichromacy.Deuteranopia)).isAtLeast(20.0)
    }

    @Test
    fun `the blue-yellow palette keeps every pair of status colours distinct to a tritanope`() {
        val palette = base.withColourVision(ColourVision.BlueYellow)

        assertThat(leastDifference(palette, Dichromacy.Tritanopia)).isAtLeast(20.0)
    }

    @Test
    fun `each palette differs from the colours of today`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val palette = base.withColourVision(vision)

            assertWithMessage("$vision").that(fills(palette)).isNotEqualTo(fills(base))
        }
        assertThat(base.withColourVision(ColourVision.RedGreen).success)
            .isNotEqualTo(base.withColourVision(ColourVision.BlueYellow).success)
    }

    @Test
    fun `every status colour of a palette keeps 3 to 1 on the page and the surfaces`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val palette = base.withColourVision(vision)
            for ((status, color) in fills(palette)) {
                for ((surface, fill) in surfaces(palette)) {
                    assertWithMessage("$vision $status on $surface")
                        .that(contrastRatio(color, fill))
                        .isAtLeast(3.0)
                }
            }
        }
    }

    @Test
    fun `the text of a status keeps 7 to 1 in a palette, so high contrast can keep the palette`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val palette = base.withColourVision(vision)
            val inks =
                mapOf(
                    "success-ink" to palette.successInk,
                    "warning-ink" to palette.warningInk,
                    "danger-ink" to palette.dangerInk,
                )
            for ((role, color) in inks) {
                for ((surface, fill) in surfaces(palette)) {
                    assertWithMessage("$vision $role on $surface")
                        .that(contrastRatio(color, fill))
                        .isAtLeast(7.0)
                }
            }
        }
    }

    @Test
    fun `a palette changes the status roles and keeps every other role`() {
        val palette = base.withColourVision(ColourVision.RedGreen)

        val kept =
            palette.copy(
                success = base.success,
                successInk = base.successInk,
                warning = base.warning,
                warningInk = base.warningInk,
                danger = base.danger,
                dangerInk = base.dangerInk,
                offline = base.offline,
            )
        assertThat(kept).isEqualTo(base)
    }

    @Test
    fun `no palette is the colours as they are`() {
        assertThat(base.withColourVision(ColourVision.Off)).isEqualTo(base)
    }

    @Test
    fun `a palette composes with high contrast, which keeps the 7 to 1 of the text`() {
        val both = base.withHighContrast().withColourVision(ColourVision.RedGreen)

        assertThat(both.success).isEqualTo(base.withColourVision(ColourVision.RedGreen).success)
        assertThat(both.success).isNotEqualTo(base.success)
        assertThat(both.ink).isEqualTo(base.withHighContrast().ink)
        assertThat(both.ink).isNotEqualTo(base.ink)
        for (ink in listOf(both.successInk, both.warningInk, both.dangerInk)) {
            assertThat(contrastRatio(ink, both.surfaceRaised)).isAtLeast(7.0)
        }
    }

    @Test
    fun `the colours of a look are those of its high contrast and its palette`() {
        val high = BorealisLook(highContrast = true)
        val blueYellow = BorealisLook(colourVision = ColourVision.BlueYellow)
        val both = BorealisLook(highContrast = true, colourVision = ColourVision.BlueYellow)

        assertThat(borealisColors(BorealisLook())).isEqualTo(base)
        assertThat(borealisColors(high)).isEqualTo(base.withHighContrast())
        assertThat(borealisColors(high)).isNotEqualTo(base)
        assertThat(borealisColors(blueYellow))
            .isEqualTo(base.withColourVision(ColourVision.BlueYellow))
        assertThat(borealisColors(blueYellow)).isNotEqualTo(base)
        assertThat(borealisColors(both))
            .isEqualTo(base.withHighContrast().withColourVision(ColourVision.BlueYellow))
        assertThat(borealisColors(both)).isNotEqualTo(borealisColors(high))
    }

    @Test
    fun `the difference of two colours is zero for the same colour and large for black and white`() {
        // A check of the measure: CIEDE2000 of black against white is 100.
        assertThat(deltaE2000(Color.Red, Color.Red)).isEqualTo(0.0)
        assertThat(deltaE2000(Color.Black, Color.White)).isWithin(0.01).of(100.0)
    }
}
