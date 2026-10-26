package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.systemAnimationScale
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The D-pad scroll of a long text moves it a step: smoothly, or at once when animations are
 * removed. A step is 96dp, 192 pixels at xhdpi.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class DpadScrollTest {
    @get:Rule val composeRule = createComposeRule()

    private val focus = FocusRequester()
    private lateinit var scroll: ScrollState

    /** Presses Down on the text and says how far it has scrolled two frames later. */
    private fun scrolledTwoFramesAfterDown(look: BorealisLook): Int {
        composeRule.setContentInKeyboardMode {
            CompositionLocalProvider(LocalBorealisLook provides look) {
                scroll = rememberScrollState()
                Box(
                    Modifier.testTag(TEXT)
                        .size(200.dp)
                        .dpadScroll(scroll)
                        .focusRequester(focus)
                        .focusable()
                        .verticalScroll(scroll)
                ) {
                    Box(Modifier.size(1000.dp))
                }
            }
        }
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(TEXT).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.mainClock.advanceTimeBy(TWO_FRAMES_MILLIS)

        return scroll.value
    }

    @Test
    fun `a step moves the text over several frames while animations are kept`() {
        val scrolled = scrolledTwoFramesAfterDown(BorealisLook())

        assertThat(scrolled).isGreaterThan(0)
        assertThat(scrolled).isLessThan(STEP_PX)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertThat(scroll.value).isEqualTo(STEP_PX)
    }

    @Test
    fun `a step moves the text at once with Reduce motion On`() {
        assertThat(scrolledTwoFramesAfterDown(BorealisLook(reduceMotion = ReduceMotion.On)))
            .isEqualTo(STEP_PX)
    }

    @Test
    fun `a step moves the text at once when the system removes animations`() {
        systemAnimationScale(0f)

        assertThat(scrolledTwoFramesAfterDown(BorealisLook())).isEqualTo(STEP_PX)
    }

    @Test
    fun `a step moves the text over several frames with Reduce motion Off, also when the system removes animations`() {
        systemAnimationScale(0f)

        val scrolled = scrolledTwoFramesAfterDown(BorealisLook(reduceMotion = ReduceMotion.Off))

        assertThat(scrolled).isGreaterThan(0)
        assertThat(scrolled).isLessThan(STEP_PX)
    }

    private companion object {
        const val TEXT = "text"
        const val STEP_PX = 192
        const val TWO_FRAMES_MILLIS = 32L
        const val SETTLE_MILLIS = 1_000L
    }
}
