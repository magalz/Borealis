package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The licences dialog on a TV: a click on its row opens it, and from then on only the D-pad from
 * Close drives it. The text is what the focus search finds above Close, and it takes Up and Down
 * until its ends.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class LicensesDialogTvTest : SettingsScreenTestBase() {
    @Before
    fun theLicencesAreOpenOnATelevision() {
        asTelevision()
        licenses = (1..LINES).joinToString("\n") { "Licence line $it" }
        show(keyboard = true)
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.waitForIdle()
    }

    private val text: SemanticsNodeInteraction
        get() =
            composeRule.onNode(
                hasText("Licence line 1", substring = true) and hasAnyAncestor(isDialog())
            )

    private val close: SemanticsNodeInteraction
        get() = composeRule.onNode(hasText("Close") and hasAnyAncestor(isDialog()))

    private fun press(target: SemanticsNodeInteraction, key: Key) {
        target.performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun scrolled(): Float =
        text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollEnd(): Float =
        text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    @Test
    fun `the dialog opens with focus on Close`() {
        close.assertIsFocused()
    }

    @Test
    fun `Up from Close moves focus to the text`() {
        press(close, Key.DirectionUp)

        text.assertIsFocused()
    }

    @Test
    fun `Down scrolls the text by a step and Up scrolls it back`() {
        press(close, Key.DirectionUp)

        press(text, Key.DirectionDown)
        val afterDown = scrolled()
        press(text, Key.DirectionUp)

        assertThat(afterDown).isGreaterThan(0f)
        assertThat(scrolled()).isLessThan(afterDown)
        text.assertIsFocused()
    }

    @Test
    fun `Down at the end of the text moves on to Close`() {
        press(close, Key.DirectionUp)
        repeat(LINES) { if (scrolled() < scrollEnd()) press(text, Key.DirectionDown) }
        assertThat(scrolled()).isEqualTo(scrollEnd())
        text.assertIsFocused()

        press(text, Key.DirectionDown)

        close.assertIsFocused()
    }

    @Test
    fun `the text shows the focus outline when it has focus`() {
        press(close, Key.DirectionUp)
        text.assertIsFocused()

        assertThat(outlinePixelsAcrossText()).isGreaterThan(0)
    }

    @Test
    fun `the text shows no focus outline while Close has focus`() {
        close.assertIsFocused()

        assertThat(outlinePixelsAcrossText()).isEqualTo(0)
    }

    /**
     * How many pixels of the focus outline (ADR 0053) lie on the middle row of the text, from a
     * little outside its left edge to a little outside its right edge. The outline reaches about
     * 5dp (10 pixels at xhdpi) outside what it surrounds, so a margin of 12dp takes in all of it
     * however the text is padded inside its node.
     */
    private fun outlinePixelsAcrossText(): Int {
        val image = composeRule.onNode(isDialog()).captureToImage().toPixelMap()
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val row = bounds.center.y.toInt()
        val margin = 2 * OUTLINE_MARGIN_DP
        val xs = (bounds.left.toInt() - margin)..(bounds.right.toInt() + margin)
        return xs.count { x -> image[x, row].isCloseTo(OUTLINE) }
    }

    private fun Color.isCloseTo(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < COLOUR_TOLERANCE

    @Test
    fun `Back dismisses the dialog`() {
        Espresso.pressBack()
        composeRule.waitForIdle()

        text.assertDoesNotExist()
    }

    private companion object {
        const val LINES = 40
        const val OUTLINE_MARGIN_DP = 12
        const val COLOUR_TOLERANCE = 0.02f
        val OUTLINE = BorealisTokens.Color.AccentSoft
    }
}
