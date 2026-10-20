package io.github.magalz.borealis.designsystem.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Test

/**
 * The rules of the glass (ADR 0057): the level, the tint floor of a background, the text colours
 * that keep 4.5:1, and the pace of the drawn background on a TV.
 */
class GlassRulesTest {
    private val tokens = BorealisColors()

    private val styles = BackgroundStyle.entries.filter { it != BackgroundStyle.Off }

    private fun spec(
        look: BorealisLook = BorealisLook(),
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        sdk: Int = API_NEWEST,
    ) = glassSpec(look, style, sdk)

    // The levels

    @Test
    fun `Standard is the chosen tint and blur where the background allows them`() {
        val look = BorealisLook(glassTintPercent = 50, glassBlurDp = 16)

        assertThat(spec(look)).isEqualTo(GlassSpec(solid = false, tintPercent = 50, blurDp = 16))
    }

    @Test
    fun `Standard at the defaults is 40 percent at 8 dp over the curtains`() {
        assertThat(spec()).isEqualTo(GlassSpec(solid = false, tintPercent = 40, blurDp = 8))
    }

    @Test
    fun `a background that needs more tint raises the tint to its floor`() {
        val spec = spec(BorealisLook(glassTintPercent = 40), BackgroundStyle.NorthernLights)

        assertThat(spec).isEqualTo(GlassSpec(solid = false, tintPercent = 70, blurDp = 8))
    }

    @Test
    fun `a tint above the floor of the background is left as it is`() {
        val spec = spec(BorealisLook(glassTintPercent = 85), BackgroundStyle.NorthernLights)

        assertThat(spec.tintPercent).isEqualTo(85)
    }

    @Test
    fun `Reduced is 85 percent at 48 dp whatever the tint and the blur chosen`() {
        val look =
            BorealisLook(
                transparency = Transparency.Reduced,
                glassTintPercent = 30,
                glassBlurDp = 8,
            )

        assertThat(spec(look)).isEqualTo(GlassSpec(solid = false, tintPercent = 85, blurDp = 48))
    }

    @Test
    fun `Off is solid`() {
        assertThat(spec(BorealisLook(transparency = Transparency.Off))).isEqualTo(GlassSpec.Solid)
    }

    @Test
    fun `high contrast forces solid over every level`() {
        Transparency.entries.forEach { level ->
            val look = BorealisLook(transparency = level, highContrast = true)

            assertWithMessage("$level").that(spec(look)).isEqualTo(GlassSpec.Solid)
        }
    }

    @Test
    fun `the plain page draws solid whatever the level`() {
        Transparency.entries.forEach { level ->
            val look = BorealisLook(transparency = level)

            assertWithMessage("$level")
                .that(spec(look, BackgroundStyle.Off))
                .isEqualTo(GlassSpec.Solid)
        }
    }

    @Test
    fun `from API 31 the blur is real`() {
        assertThat(spec(sdk = API_BLUR).blurDp).isEqualTo(8)
    }

    @Test
    fun `on API 30 Standard is the Reduced tint with no blur`() {
        val look = BorealisLook(glassTintPercent = 30, glassBlurDp = 32)

        assertThat(spec(look, sdk = API_NO_BLUR))
            .isEqualTo(GlassSpec(solid = false, tintPercent = 85, blurDp = 0))
    }

    @Test
    fun `on API 28 Reduced is the Reduced tint with no blur`() {
        val look = BorealisLook(transparency = Transparency.Reduced)

        assertThat(spec(look, sdk = API_OLDEST))
            .isEqualTo(GlassSpec(solid = false, tintPercent = 85, blurDp = 0))
    }

    @Test
    fun `Off and high contrast are solid on API 30 too`() {
        assertThat(spec(BorealisLook(transparency = Transparency.Off), sdk = API_NO_BLUR))
            .isEqualTo(GlassSpec.Solid)
        assertThat(spec(BorealisLook(highContrast = true), sdk = API_NO_BLUR))
            .isEqualTo(GlassSpec.Solid)
    }

    // The floor of each background

    @Test
    fun `at 8 dp the floor is 70 over Northern lights and 40 or less over the others`() {
        assertThat(tintFloor(BackgroundStyle.NorthernLights, 8)).isEqualTo(70)
        styles
            .filter { it != BackgroundStyle.NorthernLights }
            .forEach { assertWithMessage("$it").that(tintFloor(it, 8)).isAtMost(40) }
    }

    @Test
    fun `the floor of Flowing light is 40, where its secondary text is the colour that bounds it`() {
        assertThat(tintFloor(BackgroundStyle.FlowingLight, 8)).isEqualTo(40)
    }

    @Test
    fun `a bigger blur lowers the floor of Northern lights`() {
        val floors = listOf(8, 16, 24, 32, 48).map { tintFloor(BackgroundStyle.NorthernLights, it) }

        assertThat(floors).containsExactly(70, 60, 50, 50, 40).inOrder()
    }

    @Test
    fun `a blur between two of the grid is read as the smaller, which is the brighter backdrop`() {
        assertThat(tintFloor(BackgroundStyle.NorthernLights, 20))
            .isEqualTo(tintFloor(BackgroundStyle.NorthernLights, 16))
        assertThat(backdropLuminance(BackgroundStyle.NorthernLights, 20, 55))
            .isEqualTo(backdropLuminance(BackgroundStyle.NorthernLights, 16, 50))
    }

    // The text colours

    @Test
    fun `over the curtains at 40 percent and 8 dp ink-3 is 8D9298`() {
        assertThat(onGlass(BackgroundStyle.AuroraCurtains, spec()).ink3.toArgb())
            .isEqualTo(0xFF8D9298.toInt())
    }

    @Test
    fun `over Flowing light at 40 percent and 8 dp ink-3 is A8ACB1`() {
        val spec = spec(style = BackgroundStyle.FlowingLight)

        assertThat(onGlass(BackgroundStyle.FlowingLight, spec).ink3.toArgb())
            .isEqualTo(0xFFA8ACB1.toInt())
    }

    @Test
    fun `over Light mist at 40 percent and 8 dp ink-3 is 969BA0`() {
        val spec = spec(style = BackgroundStyle.LightMist)

        assertThat(onGlass(BackgroundStyle.LightMist, spec).ink3.toArgb())
            .isEqualTo(0xFF969BA0.toInt())
    }

    @Test
    fun `over Northern lights at its floor of 70 percent ink-3 is 9DA1A7`() {
        val spec = spec(style = BackgroundStyle.NorthernLights)

        assertThat(onGlass(BackgroundStyle.NorthernLights, spec).ink3.toArgb())
            .isEqualTo(0xFF9DA1A7.toInt())
    }

    @Test
    fun `Soft glow and Starfield keep ink-3 as it is at 40 percent and 8 dp`() {
        listOf(BackgroundStyle.SoftGlow, BackgroundStyle.Starfield).forEach { style ->
            assertWithMessage("$style")
                .that(onGlass(style, spec(style = style)).ink3)
                .isEqualTo(tokens.ink3)
        }
    }

    @Test
    fun `ink-4 is walked from ink-4 to the same target, so it ends beside ink-3`() {
        val colors = onGlass(BackgroundStyle.AuroraCurtains, spec())

        assertThat(colors.ink4.toArgb()).isEqualTo(0xFF8D9298.toInt())
        assertThat(colors.ink4).isNotEqualTo(tokens.ink4)
    }

    @Test
    fun `Reduced leaves ink-3 alone over every background`() {
        val look = BorealisLook(transparency = Transparency.Reduced)

        styles.forEach { style ->
            assertWithMessage("$style")
                .that(onGlass(style, spec(look, style)).ink3)
                .isEqualTo(tokens.ink3)
        }
    }

    @Test
    fun `Off leaves every colour as it is`() {
        val colors = onGlass(BackgroundStyle.NorthernLights, GlassSpec.Solid)

        assertThat(colors.ink3).isEqualTo(tokens.ink3)
        assertThat(colors.ink4).isEqualTo(tokens.ink4)
    }

    @Test
    fun `only ink-3 and ink-4 change on glass`() {
        val colors =
            onGlass(BackgroundStyle.FlowingLight, spec(style = BackgroundStyle.FlowingLight))

        assertThat(colors.ink).isEqualTo(tokens.ink)
        assertThat(colors.ink2).isEqualTo(tokens.ink2)
        assertThat(colors.accentSoft).isEqualTo(tokens.accentSoft)
        assertThat(colors.surface).isEqualTo(tokens.surface)
    }

    // The contrast, over the whole table

    @Test
    fun `every glass that Settings can make keeps the text at 4_5 to 1 or more`() {
        for (style in styles) {
            for (blur in GlassContrastTable.blursDp.filter { it > 0 }) {
                for (tint in GlassContrastTable.tintsPercent) {
                    val look = BorealisLook(glassTintPercent = tint, glassBlurDp = blur)
                    assertKeepsAa(style, spec(look, style))
                }
            }
            assertKeepsAa(style, spec(BorealisLook(transparency = Transparency.Reduced), style))
            assertKeepsAa(style, spec(sdk = API_NO_BLUR, style = style))
            assertKeepsAa(style, spec(BorealisLook(glassTintPercent = 30), style, API_OLDEST))
        }
    }

    private fun assertKeepsAa(style: BackgroundStyle, spec: GlassSpec) {
        val backdrop = backdropLuminance(style, spec.blurDp, spec.tintPercent)
        val colors = onGlass(style, spec)
        val roles =
            mapOf(
                "ink" to colors.ink,
                "ink-2" to colors.ink2,
                "accent-soft" to colors.accentSoft,
                "ink-3" to colors.ink3,
                "ink-4" to colors.ink4,
            )
        roles.forEach { (role, color) ->
            assertWithMessage("$role over $style at $spec: ${color.toArgb().toUInt().toString(16)}")
                .that(ratio(color, backdrop))
                .isAtLeast(AA)
        }
    }

    @Test
    fun `the adjusted ink-3 is never lighter than A8ACB1 where Standard glass is drawn`() {
        val bound = Color(0xFFA8ACB1).luminance()
        for (style in styles) {
            for (blur in GlassContrastTable.blursDp.filter { it > 0 }) {
                val look = BorealisLook(glassTintPercent = 30, glassBlurDp = blur)
                val ink3 = onGlass(style, spec(look, style)).ink3

                assertWithMessage("$style at $blur dp")
                    .that(ink3.luminance())
                    .isAtMost(bound + LUMINANCE_SLACK)
            }
        }
    }

    // The pace of the drawn background

    @Test
    fun `a TV runs the drawn background at 24 frames a second while glass is drawn`() {
        assertThat(glassFrameRate(spec(), FormFactor.Tv)).isEqualTo(24)
        assertThat(glassFrameRate(spec(sdk = API_NO_BLUR), FormFactor.Tv)).isEqualTo(24)
    }

    @Test
    fun `a TV runs it at 30 where the glass is solid`() {
        assertThat(glassFrameRate(GlassSpec.Solid, FormFactor.Tv)).isEqualTo(30)
    }

    @Test
    fun `a phone and a handheld run it at 30 whatever the glass`() {
        listOf(FormFactor.Phone, FormFactor.Handheld).forEach {
            assertWithMessage("$it").that(glassFrameRate(spec(), it)).isEqualTo(30)
            assertWithMessage("$it").that(glassFrameRate(GlassSpec.Solid, it)).isEqualTo(30)
        }
    }

    private fun onGlass(style: BackgroundStyle, spec: GlassSpec) = tokens.onGlass(style, spec)

    private fun ratio(foreground: Color, backdrop: Double): Double =
        (foreground.luminance() + NEAR_BLACK) / (backdrop + NEAR_BLACK)

    private companion object {
        const val API_OLDEST = 28
        const val API_NO_BLUR = 30
        const val API_BLUR = 31
        const val API_NEWEST = 35
        const val AA = 4.5
        const val NEAR_BLACK = 0.05

        // Colour is Float: a luminance that is the bound, worked out twice, can differ by a hair.
        const val LUMINANCE_SLACK = 1e-5f
    }
}
