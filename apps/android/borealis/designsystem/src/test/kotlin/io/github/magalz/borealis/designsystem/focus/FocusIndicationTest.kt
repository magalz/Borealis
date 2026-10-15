package io.github.magalz.borealis.designsystem.focus

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The focus indication of ADR 0053, read from the pixels. Every case is a 100dp square on a 24dp
 * frame, and positions are in dp from the frame's top-left corner (xhdpi: 2 pixels per dp).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FocusIndicationTest {
    @get:Rule val composeRule = createComposeRule()
    private val focus = FocusRequester()

    @Test
    fun `a focused element in keyboard mode has an accent-soft outline outside its shape`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        focusTheSquare()

        // The outline is 3dp wide and starts 2dp outside the element: its middle is 3.5dp out.
        assertPixel(x = 74f, y = 24f - 3.5f, OUTLINE)
        assertPixel(x = 74f, y = 24f + 100f + 3.5f, OUTLINE)
        assertPixel(x = 24f - 3.5f, y = 74f, OUTLINE)
        assertPixel(x = 24f + 100f + 3.5f, y = 74f, OUTLINE)
    }

    @Test
    fun `the 2dp gap between the element and the outline stays clear`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 1f, PAGE)
        assertPixel(x = 74f, y = 24f + 100f + 1f, PAGE)
    }

    @Test
    fun `an element that is not focused has no outline`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        assertPixel(x = 74f, y = 24f - 3.5f, PAGE)
    }

    @Test
    fun `focus that did not come from a key draws no outline`() {
        composeRule.setContent { Specimen() }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 3.5f, PAGE)
        assertPixel(x = 24f - 3.5f, y = 74f, PAGE)
    }

    @Test
    fun `the outline goes away when focus leaves`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        focusTheSquare()

        composeRule.runOnIdle { focus.freeFocus() }
        composeRule.waitForIdle()

        assertPixel(x = 74f, y = 24f - 3.5f, PAGE)
    }

    @Test
    fun `a focused element scales to the tile scale once the animation ends`() {
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true) }
        assertPixel(x = 74f, y = 24f - 1f, PAGE)

        focusTheSquare()

        // 1.04 on a 100dp square pushes each edge 2dp out: 1dp outside the square is now fill.
        assertPixel(x = 74f, y = 24f - 1f, FILL)
        assertWithMessage("token").that(BorealisTokens.Focus.TileScale).isEqualTo(1.04f)
    }

    @Test
    fun `an element without scale on focus keeps its size`() {
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = false) }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 1f, PAGE)
    }

    @Test
    fun `the scale animates by default`() {
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true) }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertPixel(x = 74f, y = 24f - 1f, PAGE)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertPixel(x = 74f, y = 24f - 1f, FILL)
    }

    @Test
    fun `the scale is instant when the system removes animations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true) }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertPixel(x = 74f, y = 24f - 1f, FILL)
    }

    @Composable
    private fun Specimen(scaleOnFocus: Boolean = false) {
        BorealisTheme {
            Box(Modifier.testTag(FRAME).background(PAGE).padding(24.dp)) {
                Box(
                    Modifier.size(100.dp)
                        .borealisFocusIndication(RoundedCornerShape(8.dp), scaleOnFocus)
                        .background(FILL, RoundedCornerShape(8.dp))
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
    }

    private fun focusTheSquare() {
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
    }

    private fun assertPixel(x: Float, y: Float, expected: Color) {
        val pixels = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val actual = pixels[(x * PIXELS_PER_DP).toInt(), (y * PIXELS_PER_DP).toInt()]
        val distance =
            abs(actual.red - expected.red) +
                abs(actual.green - expected.green) +
                abs(actual.blue - expected.blue)
        assertWithMessage("pixel at ($x, $y) dp is $actual, expected $expected")
            .that(distance < TOLERANCE)
            .isTrue()
    }

    private companion object {
        const val FRAME = "frame"
        const val PIXELS_PER_DP = 2
        const val TOLERANCE = 0.02f

        /** Early enough that a 150 to 250 ms animation has not reached the tile scale. */
        const val EARLY_MILLIS = 32L
        val PAGE = BorealisTokens.Color.Page
        val FILL = BorealisTokens.Color.SurfaceRaised
        val OUTLINE = BorealisTokens.Color.AccentSoft
    }
}
