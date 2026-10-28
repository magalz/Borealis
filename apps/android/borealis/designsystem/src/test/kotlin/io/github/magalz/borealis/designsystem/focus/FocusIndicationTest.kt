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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.testing.FakeInputModeManager
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
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
    private val elsewhere = FocusRequester()

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
        clearFocus()

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
        assertPixel(x = 74f, y = 24f - 3.5f, OUTLINE)

        clearFocus()

        assertPixel(x = 74f, y = 24f - 3.5f, PAGE)
    }

    @Test
    fun `a focused element scales to the tile scale once the animation ends`() {
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true) }
        clearFocus()
        assertPixel(x = 74f, y = 24f - 1f, PAGE)

        focusTheSquare()

        // 1.04 on a 100dp square pushes each edge 2dp out: 1dp outside the square is now fill.
        assertPixel(x = 74f, y = 24f - 1f, FILL)
        assertWithMessage("token").that(BorealisTokens.Focus.TileScale).isEqualTo(1.04f)
    }

    @Test
    fun `the outline goes away when the window returns to touch mode`() {
        val mode = FakeInputModeManager(InputMode.Keyboard)
        composeRule.setContent {
            CompositionLocalProvider(LocalInputModeManager provides mode) { Specimen() }
        }
        focusTheSquare()
        assertPixel(x = 74f, y = 24f - 3.5f, OUTLINE)

        composeRule.runOnIdle { mode.inputMode = InputMode.Touch }
        composeRule.waitForIdle()

        assertPixel(x = 74f, y = 24f - 3.5f, PAGE)
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
        clearFocus()
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
        noAnimations()
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true) }
        clearFocus()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertPixel(x = 74f, y = 24f - 1f, FILL)
    }

    @Test
    fun `the scale is instant when Reduce motion is On, though the system animates`() {
        composeRule.setContentInKeyboardMode {
            Specimen(scaleOnFocus = true, look = BorealisLook(reduceMotion = ReduceMotion.On))
        }
        clearFocus()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertPixel(x = 74f, y = 24f - 1f, FILL)
    }

    @Test
    fun `the scale animates when Reduce motion is Off, though the system removes animations`() {
        noAnimations()
        composeRule.setContentInKeyboardMode {
            Specimen(scaleOnFocus = true, look = BorealisLook(reduceMotion = ReduceMotion.Off))
        }
        clearFocus()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertPixel(x = 74f, y = 24f - 1f, PAGE)
    }

    @Test
    fun `a focused tile that leaves while its scale animation is pending raises nothing`() {
        var shown by mutableStateOf(true)
        composeRule.setContentInKeyboardMode { Specimen(scaleOnFocus = true, tile = shown) }
        clearFocus()
        composeRule.mainClock.autoAdvance = false

        // The animation is launched when the tile takes focus and has not run when the tile goes.
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.runOnIdle { shown = false }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FRAME).assertExists()
    }

    @Composable
    private fun Specimen(
        scaleOnFocus: Boolean = false,
        look: BorealisLook = BorealisLook(),
        tile: Boolean = true,
    ) {
        BorealisTheme(look = look) {
            Column {
                Box(Modifier.testTag(FRAME).background(PAGE).padding(24.dp)) {
                    if (tile) {
                        Box(
                            Modifier.size(100.dp)
                                .borealisFocusIndication(RoundedCornerShape(8.dp), scaleOnFocus)
                                .background(FILL, RoundedCornerShape(8.dp))
                                .focusRequester(focus)
                                .focusable()
                        )
                    }
                }
                Box(Modifier.size(10.dp).focusRequester(elsewhere).focusable())
            }
        }
    }

    /** Entering keyboard mode gives the first focusable focus; the cases here start without. */
    private fun clearFocus() {
        composeRule.runOnIdle { elsewhere.requestFocus() }
        composeRule.waitForIdle()
    }

    private fun focusTheSquare() {
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
    }

    private fun assertPixel(x: Float, y: Float, expected: Color) =
        composeRule.onNodeWithTag(FRAME).assertPixel(x, y, expected)

    private companion object {
        const val FRAME = "frame"

        /** Early enough that a 150 to 250 ms animation has not reached the tile scale. */
        const val EARLY_MILLIS = 32L
        val PAGE = BorealisTokens.Color.Page
        val FILL = BorealisTokens.Color.SurfaceRaised
        val OUTLINE = BorealisTokens.Color.AccentSoft
    }
}
