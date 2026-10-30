package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.components.LocalTextHalo
import io.github.magalz.borealis.designsystem.components.TextHaloStyle
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.luminance
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The focus outline over the glass of a stream (ADR 0061, decision 4): the glass is the viewer's 40
 * percent of the surface over a video, and over a white frame it is the lightest it gets, where the
 * accent-soft outline is 1.39:1 and the thick outline in ink 2.1:1, short of the 3:1 that a focus
 * outline needs (WCAG 1.4.11). Under [LocalTextHalo] the outline has a dark casing, so it keeps 3:1
 * against what is next to it, read in the pixels on a line across it; without the hook it is what
 * it was. Every case is a 100dp square on a 24dp frame (xhdpi: 2 pixels per dp).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FocusOverGlassTest {
    @get:Rule val composeRule = createComposeRule()
    private val focus = FocusRequester()
    private val colors = BorealisColors()

    /** The surface at 40 percent over a white frame: the lightest glass of the Standard level. */
    private val glass = colors.surface.copy(alpha = 0.4f).compositeOver(Color.White)

    @Composable
    private fun Specimen(halo: TextHaloStyle?, look: BorealisLook) {
        BorealisTheme(look = look) {
            CompositionLocalProvider(LocalTextHalo provides halo) {
                Column {
                    Box(Modifier.testTag(FRAME).background(glass).padding(24.dp)) {
                        Box(
                            Modifier.size(100.dp)
                                .borealisFocusIndication(RoundedCornerShape(8.dp))
                                .background(glass, RoundedCornerShape(8.dp))
                                .focusRequester(focus)
                                .focusable()
                        )
                    }
                }
            }
        }
    }

    private fun focused(halo: TextHaloStyle?, look: BorealisLook = BorealisLook()) {
        composeRule.setContentInKeyboardMode { Specimen(halo, look) }
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
    }

    /**
     * What the outline keeps against what is next to it: the ratio of its colour over the pixel two
     * away from it on each side, on the line down from the top of the frame to the square, in the
     * middle of its top edge. Two pixels are past the soft edge of the outline.
     */
    private fun ratiosAgainstNeighbours(outline: Color): List<Double> {
        val pixels = composeRule.frame(FRAME)
        val x = pixels.width / 2
        val run = (0 until 24 * PX_PER_DP).filter { pixels[x, it].near(outline) }
        assertWithMessage("the outline is on the line").that(run).isNotEmpty()
        return listOf(run.first() - 2, run.last() + 2).map {
            contrast(outline, luminance(pixels[x, it]))
        }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < NEAR

    @Test
    fun `over the lightest glass the outline falls short of 3 to 1 where there is no halo`() {
        focused(halo = null)

        assertThat(ratiosAgainstNeighbours(colors.accentSoft).min()).isLessThan(FOCUS_RATIO)
    }

    @Test
    fun `under the hook the outline keeps 3 to 1 against what is next to it, on both sides`() {
        focused(halo = HALO)

        for (ratio in ratiosAgainstNeighbours(colors.accentSoft)) {
            assertThat(ratio).isAtLeast(FOCUS_RATIO)
        }
    }

    @Test
    fun `the thick outline in ink falls short of 3 to 1 over the lightest glass without the hook`() {
        focused(halo = null, look = BorealisLook(thickFocus = true))

        assertThat(ratiosAgainstNeighbours(colors.ink).min()).isLessThan(FOCUS_RATIO)
    }

    @Test
    fun `the thick outline in ink keeps 3 to 1 under the hook, on both sides`() {
        focused(halo = HALO, look = BorealisLook(thickFocus = true))

        for (ratio in ratiosAgainstNeighbours(colors.ink)) {
            assertThat(ratio).isAtLeast(FOCUS_RATIO)
        }
    }

    @Test
    fun `without the hook the outline is as it was, with the glass up to its edge`() {
        focused(halo = null)

        val pixels = composeRule.frame(FRAME)
        val x = pixels.width / 2

        // 6 dp above the square, outside the outline, and 1 dp above it, in the gap.
        assertThat(pixels[x, 24 * PX_PER_DP - 6 * PX_PER_DP].near(glass)).isTrue()
        assertThat(pixels[x, 24 * PX_PER_DP - PX_PER_DP].near(glass)).isTrue()
    }

    private companion object {
        const val FRAME = "frame"
        const val PX_PER_DP = 2
        const val FOCUS_RATIO = 3.0
        const val NEAR = 0.06f
        val HALO = TextHaloStyle(opacity = 1f, strokeDp = 3, filled = true)
    }
}
