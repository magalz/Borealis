package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Test

/**
 * What the glass over the stream video is, and when it is not there (ADR 0061, decisions 3 and 4):
 * the tint and the blur of the viewer's transparency level, raised to the tint under which no text
 * colour of the menu keeps 4.5:1 over the whitest frame a video can show, and the rules that make
 * it a solid surface.
 */
class VideoGlassSpecTest {
    private fun spec(
        look: BorealisLook = BorealisLook(),
        sdk: Int = Build.VERSION_CODES.S,
        batterySaver: Boolean = false,
        animationsRemoved: Boolean = false,
    ) = videoGlassSpec(look, borealisColors(look), sdk, batterySaver, animationsRemoved)

    /** The glass of [look], which the test says there is. */
    private fun glassOf(look: BorealisLook = BorealisLook()): VideoGlassSpec {
        val glass = spec(look)
        assertWithMessage("a glass for $look").that(glass).isNotNull()
        return glass!!
    }

    // The tint and the blur

    @Test
    fun `Standard blurs by the radius of the look`() {
        assertThat(glassOf(BorealisLook(glassBlurDp = 24)).blurDp).isEqualTo(24)
    }

    @Test
    fun `Standard keeps a tint above the floor as the viewer chose it`() {
        assertThat(glassOf(BorealisLook(glassTintPercent = 95)).tintPercent).isEqualTo(95)
    }

    @Test
    fun `Standard at the default tint is raised to the floor of the whitest frame, 83 percent`() {
        assertThat(glassOf().tintPercent).isEqualTo(DEFAULT_FLOOR)
    }

    @Test
    fun `Reduced is 85 percent at 48 dp`() {
        val glass = glassOf(BorealisLook(transparency = Transparency.Reduced))

        assertThat(glass.tintPercent).isEqualTo(85)
        assertThat(glass.blurDp).isEqualTo(48)
    }

    // The rules that make it solid

    @Test
    fun `API 31 is where the glass starts, a level below it is solid`() {
        assertThat(spec(sdk = Build.VERSION_CODES.S)).isNotNull()
        assertThat(spec(sdk = Build.VERSION_CODES.R)).isNull()
    }

    @Test
    fun `Transparency Off is solid`() {
        assertThat(spec(BorealisLook(transparency = Transparency.Reduced))).isNotNull()
        assertThat(spec(BorealisLook(transparency = Transparency.Off))).isNull()
    }

    @Test
    fun `high contrast is solid`() {
        assertThat(spec(BorealisLook(highContrast = false))).isNotNull()
        assertThat(spec(BorealisLook(highContrast = true))).isNull()
    }

    @Test
    fun `Reduce motion is solid`() {
        assertThat(spec(animationsRemoved = false)).isNotNull()
        assertThat(spec(animationsRemoved = true)).isNull()
    }

    @Test
    fun `the battery saver is solid`() {
        assertThat(spec(batterySaver = false)).isNotNull()
        assertThat(spec(batterySaver = true)).isNull()
    }

    // The contrast

    private fun looks(): List<BorealisLook> =
        listOf(Transparency.Standard, Transparency.Reduced).flatMap { level ->
            AccentChoice.entries.flatMap { accent ->
                ColourVision.entries.flatMap { vision ->
                    listOf(0, 40, 70, 100).map { tint ->
                        BorealisLook(
                            transparency = level,
                            accent = accent,
                            colourVision = vision,
                            glassTintPercent = tint,
                        )
                    }
                }
            }
        }

    /** What the menu and the popup draw as text over the glass, and the ratio each one has. */
    private fun BorealisColors.textOver(backdrop: Color) =
        mapOf(
            "ink" to contrastOver(ink, wcagLuminance(backdrop)),
            "ink-2" to contrastOver(ink2, wcagLuminance(backdrop)),
            "ink-3" to contrastOver(ink3, wcagLuminance(backdrop)),
            "ink-4" to contrastOver(ink4, wcagLuminance(backdrop)),
            "accent-soft" to contrastOver(accentSoft, wcagLuminance(backdrop)),
            "danger-ink" to contrastOver(dangerInk, wcagLuminance(backdrop)),
            "accent-soft on the selected tint" to
                contrastOver(accentSoft, wcagLuminance(selectedOver(this, backdrop))),
        )

    @Test
    fun `over the whitest frame every text colour keeps 4_5 to 1 for every accent, palette and tint`() {
        for (look in looks()) {
            val glass = glassOf(look)
            val backdrop = whitestBackdrop(glass.colors, glass.tintPercent)

            for ((role, ratio) in glass.colors.textOver(backdrop)) {
                assertWithMessage("${look.accent} ${look.colourVision} $role")
                    .that(ratio)
                    .isAtLeast(AA_RATIO)
            }
        }
    }

    @Test
    fun `the floor is the lowest tint that does, one percent less leaves a text colour short`() {
        val colors = borealisColors(BorealisLook())
        val lower = whitestBackdrop(colors, glassOf().tintPercent - 1)
        // The glass is worked out for 4.5:1 with 2 percent to spare, as GlassRules does; ink-3
        // and ink-4 are moved to keep it at any tint, so the floor is about the others.
        val unchanged = colors.textOver(lower).filterKeys { it != "ink-3" && it != "ink-4" }

        assertThat(unchanged.values.min()).isLessThan(AA_RATIO * AA_MARGIN)
    }

    @Test
    fun `the glass keeps ink and ink-2 as they are, and lightens ink-3 and ink-4 toward ink`() {
        val tokens = borealisColors(BorealisLook())
        val glass = glassOf().colors

        assertThat(glass.ink).isEqualTo(tokens.ink)
        assertThat(glass.ink2).isEqualTo(tokens.ink2)
        assertThat(wcagLuminance(glass.ink3)).isGreaterThan(wcagLuminance(tokens.ink3))
        assertThat(wcagLuminance(glass.ink4)).isGreaterThan(wcagLuminance(tokens.ink4))
        assertThat(wcagLuminance(glass.ink3)).isAtMost(wcagLuminance(tokens.ink))
    }

    private companion object {
        /** `design/accessibility/test_contrast.py` pins the same figure, from the tool's maths. */
        const val DEFAULT_FLOOR = 83
    }
}
