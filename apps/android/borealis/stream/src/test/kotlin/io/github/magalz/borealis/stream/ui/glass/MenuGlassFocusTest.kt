package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.aStreaming
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The focus outline of the in-game menu over the video glass, read in the pixels on a TV and on a
 * handheld, the devices that have a focus (ADR 0061, decision 4): over a white frame, which is the
 * lightest the glass gets, the accent-soft outline of the first row keeps 3:1 against what is next
 * to it on each side (WCAG 1.4.11; `docs/ux/accessibility.md`). Without a casing it is 1.39:1.
 */
@RunWith(AndroidJUnit4::class)
class MenuGlassFocusTest {
    @get:Rule val composeRule = createComposeRule()

    private val outline = borealisColors(BorealisLook()).accentSoft

    private fun focusedMenu(formFactor: FormFactor) {
        val glass =
            GlassFixture(
                composeRule,
                FakeVideoSource(
                    bounds = VideoBounds(0, 0, WIDTH_PX, HEIGHT_PX),
                    frameColor = android.graphics.Color.WHITE,
                    completing = true,
                ),
            )
        glass.formFactor = formFactor
        glass.keyboardMode = true
        glass.phase = aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)
        glass.show()
        glass.run(100)
    }

    /** The ratio of the outline over the pixel two away from it, on each side, left of the row. */
    private fun ratiosAcrossTheOutline(): List<Double> {
        val row = composeRule.onNodeWithText("Back to stream").fetchSemanticsNode().boundsInRoot
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val y = row.center.y.toInt()
        val left = row.left.toInt()
        val reach = (OUTLINE_REACH_DP * composeRule.density.density).toInt()
        val run = (left - reach until left).filter { pixels[it, y].near(outline) }
        assertWithMessage("the outline is on the line").that(run).isNotEmpty()
        return listOf(run.first() - 2, run.last() + 2).map {
            contrastOver(outline, wcagLuminance(pixels[it, y]))
        }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < NEAR

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the outline of the focused row keeps 3 to 1 over the lightest glass`() {
        focusedMenu(FormFactor.Tv)

        for (ratio in ratiosAcrossTheOutline()) assertThat(ratio).isAtLeast(FOCUS_RATIO)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the outline of the focused row keeps 3 to 1 over the lightest glass`() {
        focusedMenu(FormFactor.Handheld)

        for (ratio in ratiosAcrossTheOutline()) assertThat(ratio).isAtLeast(FOCUS_RATIO)
    }

    private companion object {
        const val WIDTH_PX = 1920
        const val HEIGHT_PX = 1080
        const val FOCUS_RATIO = 3.0
        const val OUTLINE_REACH_DP = 9
        const val NEAR = 0.08f
    }
}
