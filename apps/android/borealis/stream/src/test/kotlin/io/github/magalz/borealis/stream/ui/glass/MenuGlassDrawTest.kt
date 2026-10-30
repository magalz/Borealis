package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the menu panel and the Quit popup are drawn with in the Streaming phase (ADR 0061, decisions
 * 1 and 5): the solid surface until the first copy of the video has arrived, then the video behind
 * the tint of the glass, copy after copy. The panel takes the tint of the look, 40 percent here;
 * the popup is the firm element over the dimmed game and takes 85 percent (owner, 2026-10-30). The
 * pixels are read inside the edge of the panel, where no row is, over a fake video of one colour,
 * which a blur leaves as it is.
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
     * [pixel] is the `surface` colour at [expected] over a red frame, and the other channels follow
     * from it: 40 percent for the panel, as the viewer chose, 85 percent for the popup.
     */
    private fun assertGlassOverRed(pixel: Color, expected: Float = PANEL_TINT) {
        val tint = (1f - pixel.red) / (1f - surface.red)
        assertThat(tint).isWithin(TINT_TOLERANCE).of(expected)
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
    fun `the Quit popup shows the video behind a firm tint of 85 percent, not the 40 of the panel`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.RED, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        glass.openQuitPopup()

        assertGlassOverRed(glass.popup().rightPixel(), POPUP_TINT)
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

        assertGlassOverRed(glass.popup().rightPixel(), POPUP_TINT)
    }

    // The lightest frame

    @Test
    fun `over a white frame the panel is the lightest glass of 40 percent and the popup of 85`() {
        val source = FakeVideoSource(frameColor = android.graphics.Color.WHITE, completing = true)
        val glass = fixture(source)
        glass.show()
        glass.run(100)
        // The surface at 40 percent over white: what the halo of the text is measured over.
        val lightest = surface.copy(alpha = PANEL_TINT).compositeOver(Color.White)
        // The popup is firmer: the surface at 85 percent over white.
        val firm = surface.copy(alpha = POPUP_TINT).compositeOver(Color.White)

        assertNear(glass.panel().leftPixel(), lightest)
        glass.openQuitPopup()
        assertNear(glass.popup().rightPixel(), firm)
    }

    private fun assertNear(pixel: Color, expected: Color) {
        assertThat(pixel.red).isWithin(TOLERANCE).of(expected.red)
        assertThat(pixel.green).isWithin(TOLERANCE).of(expected.green)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(expected.blue)
    }

    private companion object {
        const val PANEL_TINT = 0.4f
        const val POPUP_TINT = 0.85f
        const val TINT_TOLERANCE = 0.02f
        const val TOLERANCE = 0.012f
    }
}
