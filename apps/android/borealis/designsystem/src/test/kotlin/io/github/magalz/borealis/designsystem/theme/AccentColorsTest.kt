package io.github.magalz.borealis.designsystem.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.backdropLuminance
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.glassSpec
import org.junit.Test

/**
 * The accent colour (ADR 0058, decision 5): each [AccentChoice] gives the role set of
 * `design/accessibility/accents.json`, High contrast uses the variant of that accent, and every
 * accent keeps the contrast that the roles are for. The measurements of `contrast.py accents` are
 * the evidence; this test is the check that the app has the values that were measured.
 */
class AccentColorsTest {
    private val base = BorealisColors()

    private fun colorsOf(
        accent: AccentChoice,
        highContrast: Boolean = false,
        colourVision: ColourVision = ColourVision.Off,
    ) =
        borealisColors(
            BorealisLook(accent = accent, highContrast = highContrast, colourVision = colourVision)
        )

    /** The seven roles of a set. */
    private data class Roles(
        val accent: Color,
        val accentSoft: Color,
        val accentStrong: Color,
        val onAccent: Color,
        val accentTint: Color,
        val accentLine: Color,
        val inverse: Color,
    )

    private val teal =
        Roles(
            accent = Color(0xFF4BECD0),
            accentSoft = Color(0xFFA2FCE8),
            accentStrong = Color(0xFF06AF97),
            onAccent = Color(0xFF001713),
            accentTint = Color(0x214BECD0),
            accentLine = Color(0x664BECD0),
            inverse = Color(0xFF027464),
        )
    private val white =
        Roles(
            accent = Color(0xFFEFF3FF),
            accentSoft = Color(0xFFFFFFFF),
            accentStrong = Color(0xFFB4B8C4),
            onAccent = Color(0xFF0E1013),
            accentTint = Color(0x21EFF3FF),
            accentLine = Color(0x66EFF3FF),
            inverse = Color(0xFF636671),
        )

    private fun assertRoles(accent: AccentChoice, roles: Roles) {
        val colors = colorsOf(accent)

        assertThat(colors.accent).isEqualTo(roles.accent)
        assertThat(colors.accentSoft).isEqualTo(roles.accentSoft)
        assertThat(colors.accentStrong).isEqualTo(roles.accentStrong)
        assertThat(colors.onAccent).isEqualTo(roles.onAccent)
        assertThat(colors.accentTint).isEqualTo(roles.accentTint)
        assertThat(colors.accentLine).isEqualTo(roles.accentLine)
        assertThat(borealisColorScheme(colors).inversePrimary).isEqualTo(roles.inverse)
    }

    // The role sets

    @Test
    fun `violet is the accent of the tokens, with the strong accent for the snackbar action`() {
        assertThat(colorsOf(AccentChoice.Violet)).isEqualTo(base)
        assertThat(borealisColorScheme(base).inversePrimary).isEqualTo(Color(0xFF7C3AED))
    }

    @Test fun `teal gives the teal role set`() = assertRoles(AccentChoice.Teal, teal)

    @Test fun `white gives the white role set`() = assertRoles(AccentChoice.White, white)

    private fun roles(colors: BorealisColors) =
        listOf(
            colors.page,
            colors.chrome,
            colors.surface,
            colors.surfaceRaised,
            colors.line,
            colors.lineStrong,
            colors.hover,
            colors.scrim,
            colors.ink,
            colors.ink2,
            colors.ink3,
            colors.ink4,
            colors.success,
            colors.successInk,
            colors.warning,
            colors.warningInk,
            colors.danger,
            colors.dangerInk,
            colors.offline,
        )

    @Test
    fun `an accent changes the accent roles and nothing else`() {
        for (accent in AccentChoice.entries) {
            assertWithMessage("$accent").that(roles(colorsOf(accent))).isEqualTo(roles(base))
            assertWithMessage("$accent high contrast")
                .that(roles(colorsOf(accent, highContrast = true)))
                .isEqualTo(roles(colorsOf(AccentChoice.Violet, highContrast = true)))
        }
    }

    @Test
    fun `the accent composes with the colour-blind palettes`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            val colors = colorsOf(AccentChoice.Teal, colourVision = vision)

            assertThat(colors.accent).isEqualTo(teal.accent)
            assertThat(colors.success)
                .isEqualTo(
                    base.withColourVision(vision, AccentChoice.Teal, highContrast = false).success
                )
        }
    }

    // High contrast

    @Test
    fun `High contrast uses the variant of each accent`() {
        val violet = colorsOf(AccentChoice.Violet, highContrast = true)
        assertThat(violet.accent).isEqualTo(Color(0xFFC8B7FC))
        assertThat(violet.accentSoft).isEqualTo(Color(0xFFD6CCFE))
        assertThat(violet.accentLine).isEqualTo(Color(0xFFC8B7FC))

        val tealHigh = colorsOf(AccentChoice.Teal, highContrast = true)
        assertThat(tealHigh.accent).isEqualTo(Color(0xFF4BECD0))
        assertThat(tealHigh.accentSoft).isEqualTo(Color(0xFFA2FCE8))
        assertThat(tealHigh.accentLine).isEqualTo(Color(0xFF4BECD0))

        val whiteHigh = colorsOf(AccentChoice.White, highContrast = true)
        assertThat(whiteHigh.accent).isEqualTo(Color(0xFFEFF3FF))
        assertThat(whiteHigh.accentSoft).isEqualTo(Color(0xFFFFFFFF))
        assertThat(whiteHigh.accentLine).isEqualTo(Color(0xFFEFF3FF))
    }

    @Test
    fun `High contrast gives the snackbar action the variant of each accent`() {
        val action =
            mapOf(
                AccentChoice.Violet to Color(0xFF5744A9),
                AccentChoice.Teal to Color(0xFF026053),
                AccentChoice.White to Color(0xFF51555F),
            )

        for ((accent, expected) in action) {
            val scheme = borealisColorScheme(colorsOf(accent, highContrast = true))

            assertWithMessage("$accent").that(scheme.inversePrimary).isEqualTo(expected)
        }
    }

    @Test
    fun `the snackbar action keeps 7 to 1 on the ink of High contrast for every accent`() {
        for (accent in AccentChoice.entries) {
            val scheme = borealisColorScheme(colorsOf(accent, highContrast = true))

            assertWithMessage("$accent")
                .that(contrastRatio(scheme.inversePrimary, scheme.inverseSurface))
                .isAtLeast(7.0)
        }
    }

    @Test
    fun `High contrast keeps the roles of its accent that it does not name`() {
        for (accent in AccentChoice.entries) {
            val plain = colorsOf(accent)
            val high = colorsOf(accent, highContrast = true)

            assertWithMessage("$accent").that(high.onAccent).isEqualTo(plain.onAccent)
            assertWithMessage("$accent").that(high.accentTint).isEqualTo(plain.accentTint)
            assertWithMessage("$accent").that(high.accentStrong).isEqualTo(plain.accentStrong)
        }
    }

    @Test
    fun `High contrast keeps 7 to 1 for the accent text on every fill that text lies on`() {
        for (accent in AccentChoice.entries) {
            val high = colorsOf(accent, highContrast = true)
            for ((backdrop, fill) in textBackdrops(high)) {
                for ((role, color) in
                    listOf("accent" to high.accent, "accent-soft" to high.accentSoft)) {
                    assertWithMessage("$accent $role on $backdrop")
                        .that(contrastRatio(color, fill))
                        .isAtLeast(7.0)
                }
            }
            assertWithMessage("$accent on-accent on accent")
                .that(contrastRatio(high.onAccent, high.accent))
                .isAtLeast(7.0)
        }
    }

    @Test
    fun `High contrast keeps an opaque accent border at 3 to 1 on every fill`() {
        for (accent in AccentChoice.entries) {
            val high = colorsOf(accent, highContrast = true)

            assertWithMessage("$accent accent-line is opaque")
                .that(high.accentLine.alpha)
                .isEqualTo(1f)
            for ((backdrop, fill) in textBackdrops(high)) {
                assertWithMessage("$accent accent-line on $backdrop")
                    .that(contrastRatio(high.accentLine, fill))
                    .isAtLeast(3.0)
            }
        }
    }

    // The contrast of every accent

    @Test
    fun `the label on an accent fill keeps 4_5 to 1 for every accent`() {
        for (accent in AccentChoice.entries) {
            val colors = colorsOf(accent)

            assertWithMessage("$accent")
                .that(contrastRatio(colors.onAccent, colors.accent))
                .isAtLeast(4.5)
        }
    }

    @Test
    fun `accent and accent-soft keep 4_5 to 1 as text on the page, the surfaces and the selected rows`() {
        for (accent in AccentChoice.entries) {
            val colors = colorsOf(accent)
            val fills = textBackdrops(colors).filterKeys { it != "hover on surface-raised" }
            for ((backdrop, fill) in fills) {
                for ((role, color) in
                    listOf("accent" to colors.accent, "accent-soft" to colors.accentSoft)) {
                    assertWithMessage("$accent $role on $backdrop")
                        .that(contrastRatio(color, fill))
                        .isAtLeast(4.5)
                }
            }
        }
    }

    @Test
    fun `the selected label, accent-soft, keeps 4_5 to 1 on its tint over the page and the surfaces`() {
        for (accent in AccentChoice.entries) {
            val colors = colorsOf(accent)
            for ((name, surface) in
                listOf(
                    "page" to colors.page,
                    "surface" to colors.surface,
                    "surface-raised" to colors.surfaceRaised,
                )) {
                val tint = colors.accentTint.compositeOver(surface)

                assertWithMessage("$accent on the tint over $name")
                    .that(contrastRatio(colors.accentSoft, tint))
                    .isAtLeast(4.5)
            }
        }
    }

    @Test
    fun `the snackbar action keeps 4_5 to 1 on the ink of the inverse surface for every accent`() {
        for (accent in AccentChoice.entries) {
            val scheme = borealisColorScheme(colorsOf(accent))

            assertWithMessage("$accent")
                .that(contrastRatio(scheme.inversePrimary, scheme.inverseSurface))
                .isAtLeast(4.5)
        }
    }

    @Test
    fun `the strong accent is not the snackbar action of teal and white, where it is under 4_5 to 1`() {
        // The measure can fail: accent-strong is 2.30 on ink for teal and 1.64 for white.
        for (accent in listOf(AccentChoice.Teal, AccentChoice.White)) {
            val colors = colorsOf(accent)

            assertWithMessage("$accent")
                .that(contrastRatio(colors.accentStrong, colors.ink))
                .isLessThan(4.5)
        }
    }

    @Test
    fun `the focus outline of every accent keeps 3 to 1 on the page, the surfaces and the selected rows`() {
        for (accent in AccentChoice.entries) {
            val colors = colorsOf(accent)
            for ((backdrop, fill) in textBackdrops(colors)) {
                assertWithMessage("$accent accent-soft on $backdrop")
                    .that(contrastRatio(colors.accentSoft, fill))
                    .isAtLeast(3.0)
            }
        }
    }

    @Test
    fun `the focus outline of every accent keeps 3 to 1 over the glass of every style`() {
        for (accent in AccentChoice.entries) {
            val soft = colorsOf(accent).accentSoft
            for (style in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
                // The glass of Standard and of Reduced, and of an Android that cannot blur.
                for (sdk in listOf(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Build.VERSION_CODES.R)) {
                    for (level in listOf(Transparency.Standard, Transparency.Reduced)) {
                        val look = BorealisLook(accent = accent, transparency = level)
                        val glass = glassSpec(look, style, sdk)
                        val backdrop = backdropLuminance(style, glass.blurDp, glass.tintPercent)

                        assertWithMessage("$accent over $style, $level, API $sdk")
                            .that(contrast(soft, backdrop))
                            .isAtLeast(3.0)
                    }
                }
            }
        }
    }

    @Test
    fun `the indicator bar of white keeps 3 to 1 on the page, the surfaces and the selected rows`() {
        val colors = colorsOf(AccentChoice.White)
        for ((backdrop, fill) in textBackdrops(colors)) {
            assertWithMessage("white accent on $backdrop")
                .that(contrastRatio(colors.accent, fill))
                .isAtLeast(3.0)
        }
    }
}
