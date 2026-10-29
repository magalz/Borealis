package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the menu panel and the Quit popup are drawn with in the Streaming phase (ADR 0061, decisions
 * 1 and 5): the solid surface until the first copy of the video has arrived, then the video behind
 * the tint of the glass, copy after copy. The pixels are read inside the edge of the panel, where
 * no row is, over a fake video of one colour, which a blur leaves as it is.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class MenuGlassDrawTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface

    private fun assertSolid(pixel: Color) {
        assertThat(pixel.red).isWithin(TOLERANCE).of(surface.red)
        assertThat(pixel.green).isWithin(TOLERANCE).of(surface.green)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(surface.blue)
    }

    /**
     * [pixel] is the `surface` colour at a tint over a red frame: the tint is the viewer's 40
     * percent or more (the floor raises it), and the other channels follow from it.
     */
    private fun assertGlassOverRed(pixel: Color) {
        val tint = (1f - pixel.red) / (1f - surface.red)
        assertThat(tint).isAtLeast(0.4f)
        assertThat(tint).isAtMost(1f)
        assertThat(pixel.green).isWithin(TOLERANCE).of(surface.green * tint)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(surface.blue * tint)
    }

    private fun fixture(source: FakeVideoSource) = GlassFixture(composeRule, source)

    @Test
    fun `the panel is the solid surface until the first copy ends, and then the glass`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.RED)
        val glass = fixture(source)
        glass.show()
        glass.run(100)

        assertSolid(glass.panel().leftPixel())

        source.finish()
        glass.run(100)

        assertGlassOverRed(glass.panel().leftPixel())
    }

    @Test
    fun `the panel shows the video behind its tint, and the next copy is drawn when it arrives`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.RED, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        assertGlassOverRed(glass.panel().leftPixel())

        source.frameColor = android.graphics.Color.BLUE
        glass.run(100)

        val pixel = glass.panel().leftPixel()
        assertThat(pixel.blue).isGreaterThan(pixel.red)
    }

    @Test
    fun `the Quit popup shows the video behind its tint too`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.RED, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        glass.openQuitPopup()

        assertGlassOverRed(glass.popup().rightPixel())
    }

    @Test
    fun `the Quit popup is the solid surface until a copy has arrived`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.RED)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        glass.openQuitPopup()
        assertSolid(glass.popup().rightPixel())

        source.finish()
        glass.run(100)

        assertGlassOverRed(glass.popup().rightPixel())
    }

    // Text over the whitest frame

    @Test
    fun `over a white frame the text of the menu keeps 4_5 to 1, in the colours that are drawn`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.WHITE, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        val pixel = glass.panel().leftPixel()
        // The video shows through: this is not the solid surface.
        assertThat(pixel.red).isGreaterThan(surface.red + 0.05f)
        val colors = borealisColors(BorealisLook())
        val drawn =
            mapOf(
                "ink" to colors.ink,
                "ink-2" to colors.ink2,
                "accent-soft" to colors.accentSoft,
                "danger-ink" to colors.dangerInk,
                "the secondary text of a row, as drawn" to composeRule.textColor(REFRESH_NOTE),
            )

        for ((role, color) in drawn) {
            assertWithMessage(role)
                .that(contrastOver(color, wcagLuminance(pixel)))
                .isAtLeast(AA_RATIO)
        }
        assertThat(composeRule.textColor(REFRESH_NOTE)).isNotEqualTo(colors.ink3)
    }

    @Test
    fun `the secondary text of the Quit popup keeps 4_5 to 1 over a white frame`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.WHITE, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        glass.openQuitPopup()
        val pixel = glass.popup().rightPixel()
        assertThat(pixel.red).isGreaterThan(surface.red + 0.05f)
        // The menu has the same sentence under its Quit row, out of reach behind the popup.
        val body =
            composeRule.onNode(
                hasText(QUIT_BODY) and hasAnyAncestor(glass.popupMatcher()),
                useUnmergedTree = true,
            )

        val color = body.textLayout().layoutInput.style.color

        assertThat(contrastOver(color, wcagLuminance(pixel))).isAtLeast(AA_RATIO)
        assertThat(color).isNotEqualTo(borealisColors(BorealisLook()).ink3)
    }

    private companion object {
        const val QUIT_BODY = "Ends the stream.\nThe display and controls go back to the PC."
        const val TOLERANCE = 0.012f
    }
}
