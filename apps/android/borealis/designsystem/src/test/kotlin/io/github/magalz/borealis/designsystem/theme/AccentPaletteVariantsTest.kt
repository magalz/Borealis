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
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Test

/**
 * The accent under a colour-blind palette (ADR 0058, decision 5, and issue #50). A palette changes
 * the status colours and leaves the accent, so an accent can be one that a viewer of the palette
 * does not tell from a status colour: violet is the blue of the success colour to a protanope and a
 * deuteranope. `design/accessibility/accents.json` (`palette_variants`) has the variants that the
 * owner chose on 2026-10-22, and `contrast.py accents --palette` measures them; this test is the
 * check that the app has the values that were measured, and that every accent keeps 20 of CIEDE2000
 * from the four status colours under every palette, as the palettes keep it among themselves
 * (`ColourVisionPaletteTest`).
 */
class AccentPaletteVariantsTest {
    private fun colorsOf(
        accent: AccentChoice,
        vision: ColourVision,
        highContrast: Boolean = false,
    ) =
        borealisColors(
            BorealisLook(accent = accent, colourVision = vision, highContrast = highContrast)
        )

    /** The roles of a set and its high contrast roles. */
    private data class Variant(
        val accent: Color,
        val accentSoft: Color,
        val accentStrong: Color,
        val onAccent: Color,
        val accentTint: Color,
        val accentLine: Color,
        val inverse: Color,
        val highAccent: Color,
        val highSoft: Color,
        val highLine: Color,
        val highInverse: Color,
    )

    private val violetRedGreen =
        Variant(
            accent = Color(0xFFF4BCBC),
            accentSoft = Color(0xFFFCC3C8),
            accentStrong = Color(0xFFB88484),
            onAccent = Color(0xFF200E0D),
            accentTint = Color(0x21F4BCBC),
            accentLine = Color(0x66F4BCBC),
            inverse = Color(0xFF8A5A5A),
            highAccent = Color(0xFFF4BCBC),
            highSoft = Color(0xFFFCC3C8),
            highLine = Color(0xFFF4BCBC),
            highInverse = Color(0xFF774849),
        )
    private val violetBlueYellow =
        Variant(
            accent = Color(0xFFC688FE),
            accentSoft = Color(0xFFC8BBFE),
            accentStrong = Color(0xFF934BBA),
            onAccent = Color(0xFF1D092E),
            accentTint = Color(0x21C688FE),
            accentLine = Color(0x66C688FE),
            inverse = Color(0xFF834BBA),
            highAccent = Color(0xFFC7B9FC),
            highSoft = Color(0xFFCAC4FE),
            highLine = Color(0xFFC7B9FC),
            highInverse = Color(0xFF6F38A6),
        )
    private val whiteBlueYellow =
        Variant(
            accent = Color(0xFFEFF3FF),
            accentSoft = Color(0xFFF3FCFF),
            accentStrong = Color(0xFFB4B8C4),
            onAccent = Color(0xFF0E1013),
            accentTint = Color(0x21EFF3FF),
            accentLine = Color(0x66EFF3FF),
            inverse = Color(0xFF636671),
            highAccent = Color(0xFFEFF3FF),
            highSoft = Color(0xFFF3FCFF),
            highLine = Color(0xFFEFF3FF),
            highInverse = Color(0xFF51555F),
        )

    private val variants =
        listOf(
            Triple(AccentChoice.Violet, ColourVision.RedGreen, violetRedGreen),
            Triple(AccentChoice.Violet, ColourVision.BlueYellow, violetBlueYellow),
            Triple(AccentChoice.White, ColourVision.BlueYellow, whiteBlueYellow),
        )

    /** Every accent under every palette, as a viewer sets it: plain, then under High contrast. */
    private val looks = buildList {
        for (accent in AccentChoice.entries) {
            for (vision in ColourVision.entries) {
                for (high in listOf(false, true)) add(Triple(accent, vision, high))
            }
        }
    }

    private fun BorealisColors.statusFills() =
        mapOf("success" to success, "warning" to warning, "danger" to danger, "offline" to offline)

    // The role sets

    @Test
    fun `each variant gives its role set under its palette`() {
        for ((accent, vision, set) in variants) {
            val colors = colorsOf(accent, vision)

            assertWithMessage("$accent $vision").that(colors.accent).isEqualTo(set.accent)
            assertThat(colors.accentSoft).isEqualTo(set.accentSoft)
            assertThat(colors.accentStrong).isEqualTo(set.accentStrong)
            assertThat(colors.onAccent).isEqualTo(set.onAccent)
            assertThat(colors.accentTint).isEqualTo(set.accentTint)
            assertThat(colors.accentLine).isEqualTo(set.accentLine)
            assertThat(borealisColorScheme(colors).inversePrimary).isEqualTo(set.inverse)
        }
    }

    @Test
    fun `under High contrast a variant gives its high contrast roles and keeps its other roles`() {
        for ((accent, vision, set) in variants) {
            val colors = colorsOf(accent, vision, highContrast = true)

            assertWithMessage("$accent $vision").that(colors.accent).isEqualTo(set.highAccent)
            assertThat(colors.accentSoft).isEqualTo(set.highSoft)
            assertThat(colors.accentLine).isEqualTo(set.highLine)
            assertThat(borealisColorScheme(colors).inversePrimary).isEqualTo(set.highInverse)
            assertThat(colors.onAccent).isEqualTo(set.onAccent)
            assertThat(colors.accentTint).isEqualTo(set.accentTint)
            assertThat(colors.accentStrong).isEqualTo(set.accentStrong)
        }
    }

    @Test
    fun `a variant is the colour of its accent as the palette is, so the status roles are the palette's`() {
        val statuses =
            mapOf(
                ColourVision.RedGreen to BorealisTokens.ColourVision.RedGreen.Success,
                ColourVision.BlueYellow to BorealisTokens.ColourVision.BlueYellow.Success,
            )
        for ((accent, vision, _) in variants) {
            for (high in listOf(false, true)) {
                val colors = colorsOf(accent, vision, high)

                assertWithMessage("$accent $vision")
                    .that(colors.success)
                    .isEqualTo(statuses[vision])
                assertThat(colors.ink).isEqualTo(colorsOf(accent, ColourVision.Off, high).ink)
            }
        }
    }

    // The accents that need no variant

    @Test
    fun `teal is the same under both palettes, plain and under High contrast`() {
        for (vision in listOf(ColourVision.RedGreen, ColourVision.BlueYellow)) {
            for (high in listOf(false, true)) {
                val colors = colorsOf(AccentChoice.Teal, vision, high)
                val plain = colorsOf(AccentChoice.Teal, ColourVision.Off, high)

                assertWithMessage("$vision high contrast $high")
                    .that(colors.accent)
                    .isEqualTo(plain.accent)
                assertThat(colors.accentSoft).isEqualTo(plain.accentSoft)
                assertThat(colors.accentStrong).isEqualTo(plain.accentStrong)
                assertThat(colors.onAccent).isEqualTo(plain.onAccent)
                assertThat(colors.accentTint).isEqualTo(plain.accentTint)
                assertThat(colors.accentLine).isEqualTo(plain.accentLine)
                assertThat(colors.accentInverse).isEqualTo(plain.accentInverse)
            }
        }
    }

    @Test
    fun `white keeps its set under the Red-green palette, where it keeps the 20`() {
        for (high in listOf(false, true)) {
            val colors = colorsOf(AccentChoice.White, ColourVision.RedGreen, high)
            val plain = colorsOf(AccentChoice.White, ColourVision.Off, high)

            assertThat(colors.accent).isEqualTo(plain.accent)
            assertThat(colors.accentSoft).isEqualTo(plain.accentSoft)
            assertThat(colors.accentLine).isEqualTo(plain.accentLine)
            assertThat(colors.accentInverse).isEqualTo(plain.accentInverse)
        }
    }

    @Test
    fun `no palette gives every accent as it is`() {
        for (accent in AccentChoice.entries) {
            for (high in listOf(false, true)) {
                val colors = colorsOf(accent, ColourVision.Off, high)
                val withoutAPalette =
                    BorealisColors().withAccent(accent).let {
                        if (high) it.withHighContrast(accent) else it
                    }

                assertWithMessage("$accent high contrast $high")
                    .that(colors)
                    .isEqualTo(withoutAPalette)
            }
        }
    }

    // The 20 of CIEDE2000 from the status colours

    /** The dichromacies that a palette is for, with `null` for normal vision, which is held too. */
    private fun visionsOf(vision: ColourVision): List<Dichromacy?> =
        when (vision) {
            ColourVision.RedGreen -> listOf(null, Dichromacy.Protanopia, Dichromacy.Deuteranopia)
            ColourVision.BlueYellow -> listOf(null, Dichromacy.Tritanopia)
            ColourVision.Off -> emptyList()
        }

    private fun Color.asSeenBy(kind: Dichromacy?) = if (kind == null) this else seenBy(kind)

    private fun heldRoles(colors: BorealisColors, highContrast: Boolean) = buildMap {
        put("accent", colors.accent)
        put("accent-soft", colors.accentSoft)
        // The line is the accent at 40 % and is measured, not held; high contrast draws it opaque.
        if (highContrast) put("accent-line", colors.accentLine)
    }

    @Test
    fun `the accent and the soft accent keep 20 from the status fills of every palette, under its vision`() {
        for ((accent, vision, high) in looks) {
            val colors = colorsOf(accent, vision, high)
            for (kind in visionsOf(vision)) {
                for ((role, color) in heldRoles(colors, high)) {
                    for ((status, fill) in colors.statusFills()) {
                        val apart = deltaE2000(color.asSeenBy(kind), fill.asSeenBy(kind))

                        assertWithMessage(
                                "$accent $vision high contrast $high $role from $status as $kind"
                            )
                            .that(apart)
                            .isAtLeast(20.0)
                    }
                }
            }
        }
    }

    @Test
    fun `the check can fail, as violet of the tokens is 5 from the sky blue as a deuteranope sees it`() {
        val violet = borealisColors(BorealisLook()).accent.seenBy(Dichromacy.Deuteranopia)
        val success = BorealisTokens.ColourVision.RedGreen.Success.seenBy(Dichromacy.Deuteranopia)

        assertThat(deltaE2000(violet, success)).isWithin(0.1).of(5.0)
    }

    @Test
    fun `every accent is told from the ink under every palette, white aside`() {
        for ((accent, vision, high) in looks.filter { it.first != AccentChoice.White }) {
            val colors = colorsOf(accent, vision, high)
            for (ink in listOf(colors.ink, colors.ink2)) {
                assertWithMessage("$accent $vision high contrast $high")
                    .that(deltaE2000(colors.accent, ink))
                    .isAtLeast(20.0)
            }
        }
    }

    // The contrast of every accent, under every palette

    @Test
    fun `the label on an accent fill keeps 4_5 to 1, and 7 to 1 under High contrast`() {
        for ((accent, vision, high) in looks) {
            val colors = colorsOf(accent, vision, high)

            assertWithMessage("$accent $vision high contrast $high")
                .that(contrastRatio(colors.onAccent, colors.accent))
                .isAtLeast(if (high) 7.0 else 4.5)
        }
    }

    @Test
    fun `accent and accent-soft keep 4_5 to 1 as text on the page, the surfaces and the selected rows`() {
        for ((accent, vision, high) in looks) {
            val colors = colorsOf(accent, vision, high)
            // High contrast holds the hover too, as `AccentColorsTest` does.
            val fills = textBackdrops(colors).filterKeys { high || it != "hover on surface-raised" }
            for ((backdrop, fill) in fills) {
                for ((role, color) in
                    listOf("accent" to colors.accent, "accent-soft" to colors.accentSoft)) {
                    assertWithMessage("$accent $vision high contrast $high $role on $backdrop")
                        .that(contrastRatio(color, fill))
                        .isAtLeast(if (high) 7.0 else 4.5)
                }
            }
        }
    }

    @Test
    fun `the selected label, accent-soft, keeps 4_5 to 1 on its tint over the page and the surfaces`() {
        for ((accent, vision, high) in looks) {
            val colors = colorsOf(accent, vision, high)
            for (surface in listOf(colors.page, colors.surface, colors.surfaceRaised)) {
                val tint = colors.accentTint.compositeOver(surface)

                assertWithMessage("$accent $vision high contrast $high")
                    .that(contrastRatio(colors.accentSoft, tint))
                    .isAtLeast(4.5)
            }
        }
    }

    @Test
    fun `the snackbar action keeps 4_5 to 1 on the ink, and 7 to 1 under High contrast`() {
        for ((accent, vision, high) in looks) {
            val scheme = borealisColorScheme(colorsOf(accent, vision, high))

            assertWithMessage("$accent $vision high contrast $high")
                .that(contrastRatio(scheme.inversePrimary, scheme.inverseSurface))
                .isAtLeast(if (high) 7.0 else 4.5)
        }
    }

    @Test
    fun `High contrast keeps an opaque accent border at 3 to 1 on every fill under every palette`() {
        for ((accent, vision, _) in looks.filter { it.third }) {
            val colors = colorsOf(accent, vision, highContrast = true)

            assertThat(colors.accentLine.alpha).isEqualTo(1f)
            for ((backdrop, fill) in textBackdrops(colors)) {
                assertWithMessage("$accent $vision on $backdrop")
                    .that(contrastRatio(colors.accentLine, fill))
                    .isAtLeast(3.0)
            }
        }
    }

    @Test
    fun `the focus outline of every accent keeps 3 to 1 over the glass of every style under every palette`() {
        for (accent in AccentChoice.entries) {
            for (vision in ColourVision.entries) assertOutlineOverGlass(accent, vision)
        }
    }

    private fun assertOutlineOverGlass(accent: AccentChoice, vision: ColourVision) {
        val soft = colorsOf(accent, vision).accentSoft
        for (style in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
            for (sdk in listOf(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Build.VERSION_CODES.R)) {
                for (level in listOf(Transparency.Standard, Transparency.Reduced)) {
                    val look =
                        BorealisLook(accent = accent, colourVision = vision, transparency = level)
                    val glass = glassSpec(look, style, sdk)
                    val backdrop = backdropLuminance(style, glass.blurDp, glass.tintPercent)

                    assertWithMessage("$accent $vision over $style, $level, API $sdk")
                        .that(contrast(soft, backdrop))
                        .isAtLeast(3.0)
                }
            }
        }
    }

    @Test
    fun `the glass of a variant has the tint of the accent that it replaces`() {
        // The soft accent is one of the colours that the floor of a style keeps at 4.5:1: the
        // variants keep the floors of the tool, which are those of the accents they replace.
        for ((accent, vision, _) in variants) {
            for (style in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
                val standard = glassSpec(BorealisLook(accent = accent), style, SDK)
                val variant =
                    glassSpec(BorealisLook(accent = accent, colourVision = vision), style, SDK)

                assertWithMessage("$accent $vision over $style")
                    .that(variant.tintPercent)
                    .isEqualTo(standard.tintPercent)
            }
        }
    }

    private companion object {
        const val SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
