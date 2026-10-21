package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The check is 48dp square here, so a unit of its 24-unit grid is 2dp. The plain check runs from
 * (5, 12.5) by (9.5, 17) to (19, 7.5); the circled one is a ring of radius 11 round a smaller check
 * from (7.5, 12.5) by (10.5, 15.5) to (16.5, 8.5).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AnimatedCheckTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the check draws itself from its start to its end`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        assertDrawn(CHECK_START)
        assertPage(CHECK_END)
    }

    @Test
    fun `the check is complete after 400 ms and stays`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(DONE_MILLIS)
        assertDrawn(CHECK_START, CHECK_MIDDLE, CHECK_END)

        composeRule.mainClock.advanceTimeBy(2_000)
        assertDrawn(CHECK_START, CHECK_MIDDLE, CHECK_END)
    }

    @Test
    fun `nothing is drawn before the animation starts`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertPage(CHECK_END)
    }

    @Test
    fun `with animations removed the check is complete from the first frame`() {
        noAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertDrawn(CHECK_START, CHECK_MIDDLE, CHECK_END)
    }

    @Test
    fun `the colour is a parameter`() {
        noAnimations()
        composeRule.setContent { Specimen(color = Color.Red) }

        composeRule.onNodeWithTag(FRAME).assertPixel(CHECK_END.first, CHECK_END.second, Color.Red)
    }

    @Test
    fun `a plain check has no ring`() {
        noAnimations()
        composeRule.setContent { Specimen() }

        assertPage(RING)
    }

    @Test
    fun `a circled check draws its ring and its check`() {
        noAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen(circled = true) }

        assertDrawn(RING, CIRCLED_CHECK_END)
    }

    @Test
    fun `a circled check draws itself and is complete after 400 ms`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen(circled = true) }

        assertPage(RING, CIRCLED_CHECK_END)
        composeRule.mainClock.advanceTimeBy(DONE_MILLIS)
        assertDrawn(RING, CIRCLED_CHECK_END)
    }

    private fun assertDrawn(vararg probes: Pair<Float, Float>) = probes.forEach {
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(it.first, it.second, BorealisTokens.Color.Success)
    }

    private fun assertPage(vararg probes: Pair<Float, Float>) = probes.forEach {
        composeRule.onNodeWithTag(FRAME).assertPixel(it.first, it.second, BorealisTokens.Color.Page)
    }

    @Composable
    private fun Specimen(circled: Boolean = false, color: Color? = null) {
        BorealisTheme {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page)) {
                if (color == null) {
                    AnimatedCheck(Modifier.size(48.dp), circled)
                } else {
                    AnimatedCheck(Modifier.size(48.dp), circled, color)
                }
            }
        }
    }

    private companion object {
        const val FRAME = "frame"

        // Probes in dp from the frame's top-left corner: twice the unit of the 24-unit grid.
        val CHECK_START = 10f to 25f
        val CHECK_MIDDLE = 19f to 34f
        val CHECK_END = 38f to 15f
        val RING = 2f to 24f
        val CIRCLED_CHECK_END = 33f to 17f

        const val EARLY_MILLIS = 100L
        const val DONE_MILLIS = 450L
    }
}
