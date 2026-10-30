package io.github.magalz.borealis.stream.ui.glass

import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Test

/**
 * What the glass over the stream video is, and when it is not there (ADR 0061, decisions 3 and 4):
 * the tint and the blur of the viewer's transparency level as they are, and the rules that make it
 * a solid surface. The text on it stands on a halo and not on a heavier tint, so the tint is never
 * raised for the contrast.
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
    fun `Standard is the tint and the blur of the look, 40 percent at 8 dp by default`() {
        val glass = glassOf()

        assertThat(glass.tintPercent).isEqualTo(40)
        assertThat(glass.blurDp).isEqualTo(8)
    }

    @Test
    fun `Standard follows the tint and the blur that the viewer chose`() {
        val glass = glassOf(BorealisLook(glassTintPercent = 70, glassBlurDp = 24))

        assertThat(glass.tintPercent).isEqualTo(70)
        assertThat(glass.blurDp).isEqualTo(24)
    }

    @Test
    fun `Reduced is 85 percent at 48 dp`() {
        val glass = glassOf(BorealisLook(transparency = Transparency.Reduced))

        assertThat(glass.tintPercent).isEqualTo(85)
        assertThat(glass.blurDp).isEqualTo(48)
    }

    @Test
    fun `the tint is the viewer's whatever the accent or the palette, it is never raised`() {
        for (accent in AccentChoice.entries) {
            for (vision in ColourVision.entries) {
                val glass = glassOf(BorealisLook(accent = accent, colourVision = vision))

                assertWithMessage("$accent $vision").that(glass.tintPercent).isEqualTo(40)
            }
        }
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

    // The text colours

    @Test
    fun `on the glass ink-3 and ink-4 are ink-2, as on a background with a halo, and the rest stay`() {
        val tokens = borealisColors(BorealisLook())
        val glass = glassOf().colors

        assertThat(glass.ink3).isEqualTo(tokens.ink2)
        assertThat(glass.ink4).isEqualTo(tokens.ink2)
        assertThat(glass.ink).isEqualTo(tokens.ink)
        assertThat(glass.ink2).isEqualTo(tokens.ink2)
        assertThat(glass.accentSoft).isEqualTo(tokens.accentSoft)
        assertThat(glass.dangerInk).isEqualTo(tokens.dangerInk)
    }

    @Test
    fun `what sits on the solid surface keeps the colours of the look, ink-3 included`() {
        for (accent in AccentChoice.entries) {
            val look = BorealisLook(accent = accent)

            assertWithMessage("$accent")
                .that(glassOf(look).surfaceColors)
                .isEqualTo(borealisColors(look))
        }
    }
}
