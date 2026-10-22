package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The colour-blind palettes of ADR 0057 (decision 11). A palette replaces the status colours, and
 * is measured with the model of colour vision of [Dichromacy] in `ColourMeasures.kt`: Machado,
 * Oliveira and Fernandes (2009) at severity 1.0. This test is the check of the palettes.
 *
 * The measure of "distinct" is the colour difference CIEDE2000 of 20 or more, between every pair of
 * the four status colours as the viewer with that dichromacy sees them. The status colours are also
 * indicators, so each keeps 3:1 on the page and the surfaces (WCAG 1.4.11).
 */
class ColourVisionPaletteTest {
    private val base = BorealisColors()

    /**
     * The colours of [base] under [vision], as teal has them: teal keeps the 20 of CIEDE2000 from
     * the status colours under both palettes and has no variant, so only the status roles change.
     * What the palettes do to the accents that have a variant is in `AccentPaletteVariantsTest`.
     */
    private fun BorealisColors.under(vision: ColourVision) =
        withColourVision(vision, AccentChoice.Teal, highContrast = false)

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
        val palette = base.under(ColourVision.RedGreen)

        assertThat(leastDifference(palette, Dichromacy.Protanopia)).isAtLeast(20.0)
        assertThat(leastDifference(palette, Dichromacy.Deuteranopia)).isAtLeast(20.0)
    }

    @Test
    fun `the blue-yellow palette keeps every pair of status colours distinct to a tritanope`() {
        val palette = base.under(ColourVision.BlueYellow)

        assertThat(leastDifference(palette, Dichromacy.Tritanopia)).isAtLeast(20.0)
    }

    @Test
    fun `each palette differs from the colours of today`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val palette = base.under(vision)

            assertWithMessage("$vision").that(fills(palette)).isNotEqualTo(fills(base))
        }
        assertThat(base.under(ColourVision.RedGreen).success)
            .isNotEqualTo(base.under(ColourVision.BlueYellow).success)
    }

    @Test
    fun `every status colour of a palette keeps 3 to 1 on the page and the surfaces`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val palette = base.under(vision)
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
            val palette = base.under(vision)
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
    fun `a palette changes the status roles and keeps every other role of an accent with no variant`() {
        val palette = base.under(ColourVision.RedGreen)

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
        for (accent in AccentChoice.entries) {
            for (high in listOf(false, true)) {
                assertWithMessage("$accent high contrast $high")
                    .that(base.withColourVision(ColourVision.Off, accent, high))
                    .isEqualTo(base)
            }
        }
    }

    @Test
    fun `a palette composes with high contrast, which keeps the 7 to 1 of the text`() {
        val both =
            base
                .withHighContrast(AccentChoice.Teal)
                .withColourVision(ColourVision.RedGreen, AccentChoice.Teal, highContrast = true)

        assertThat(both.success).isEqualTo(base.under(ColourVision.RedGreen).success)
        assertThat(both.success).isNotEqualTo(base.success)
        assertThat(both.ink).isEqualTo(base.withHighContrast(AccentChoice.Teal).ink)
        assertThat(both.ink).isNotEqualTo(base.ink)
        for (ink in listOf(both.successInk, both.warningInk, both.dangerInk)) {
            assertThat(contrastRatio(ink, both.surfaceRaised)).isAtLeast(7.0)
        }
    }

    @Test
    fun `the colours of a look are those of its high contrast and its palette`() {
        val teal = AccentChoice.Teal
        val high = BorealisLook(accent = teal, highContrast = true)
        val blueYellow = BorealisLook(accent = teal, colourVision = ColourVision.BlueYellow)
        val both =
            BorealisLook(accent = teal, highContrast = true, colourVision = ColourVision.BlueYellow)
        val tealBase = base.withAccent(teal)

        assertThat(borealisColors(BorealisLook())).isEqualTo(base)
        assertThat(borealisColors(high)).isEqualTo(tealBase.withHighContrast(teal))
        assertThat(borealisColors(high)).isNotEqualTo(tealBase)
        assertThat(borealisColors(blueYellow))
            .isEqualTo(tealBase.withColourVision(ColourVision.BlueYellow, teal, false))
        assertThat(borealisColors(blueYellow)).isNotEqualTo(tealBase)
        assertThat(borealisColors(both))
            .isEqualTo(
                tealBase
                    .withHighContrast(teal)
                    .withColourVision(ColourVision.BlueYellow, teal, highContrast = true)
            )
        assertThat(borealisColors(both)).isNotEqualTo(borealisColors(high))
    }

    @Test
    fun `the difference of two chromatic colours is the one that an independent implementation gives`() {
        // The two pairs of the palettes that are nearest and furthest; the figures are from a
        // second implementation of CIEDE2000 that reproduces the test data of Sharma, Wu and Dalal.
        assertThat(deltaE2000(Color(0xFF009E73), Color(0xFF808080))).isWithin(0.01).of(24.975)
        assertThat(deltaE2000(Color(0xFF56B4E9), Color(0xFFF0E442))).isWithin(0.01).of(57.529)
    }

    @Test
    fun `the difference of two colours is zero for the same colour and large for black and white`() {
        // A check of the measure: CIEDE2000 of black against white is 100.
        assertThat(deltaE2000(Color.Red, Color.Red)).isEqualTo(0.0)
        assertThat(deltaE2000(Color.Black, Color.White)).isWithin(0.01).of(100.0)
    }

    @Test
    fun `each row of a dichromacy matrix adds up to 1, so white and the greys keep their light`() {
        // The published matrices have six places, and two of their rows are off by one in the last.
        for (kind in Dichromacy.entries) {
            for (row in kind.matrix) {
                assertWithMessage("$kind $row").that(row.sum()).isWithin(2e-6).of(1.0)
            }
        }
    }

    @Test
    fun `white and a mid grey are seen as they are by every kind of dichromat`() {
        val grey = Color(128, 128, 128)

        for (kind in Dichromacy.entries) {
            assertWithMessage("$kind white").that(Color.White.seenBy(kind)).isEqualTo(Color.White)
            assertWithMessage("$kind grey").that(grey.seenBy(kind)).isEqualTo(grey)
        }
    }

    @Test
    fun `red is seen by a protanope as the first column of its matrix, encoded`() {
        // The linear red (1, 0, 0) becomes (0.152286, 0.114503, -0.003882); the last is clipped to
        // 0, and the three are encoded to (0.42661, 0.37265, 0), here to 8 bits a channel.
        val seen = Color.Red.seenBy(Dichromacy.Protanopia)

        assertThat(seen.red).isWithin(0.002f).of(0.42661f)
        assertThat(seen.green).isWithin(0.002f).of(0.37265f)
        assertThat(seen.blue).isEqualTo(0f)
    }
}
