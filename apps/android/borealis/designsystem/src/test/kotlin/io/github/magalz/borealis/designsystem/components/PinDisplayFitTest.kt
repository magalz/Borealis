package io.github.magalz.borealis.designsystem.components

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cells of the pairing PIN follow their digit (ADR 0058, decision 2): a font whose digits are
 * taller or wider than those of IBM Plex Mono, at the largest text size, does not cut them off. A
 * cell is never smaller than 56dp by 64dp.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PinDisplayFitTest(private val case: FitCase) {
    private val font = case.font

    @get:Rule val composeRule = createComposeRule()

    private fun show(look: BorealisLook) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, look) { OnPage { PinDisplay("4817") } }
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `at the largest text size every digit has the room that its glyph needs`() {
        show(BorealisLook(font = font, textSize = TextSize.Largest, textSpacing = case.spacing))

        assertDigitsHaveTheirRoom()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at the largest text size and the largest system scale every digit has its room`() {
        show(BorealisLook(font = font, textSize = TextSize.Largest))

        assertDigitsHaveTheirRoom()
    }

    @Test
    @Config(fontScale = 1f)
    fun `at the default text size the PIN is 254dp by 64dp, four cells of 56dp by 64dp`() {
        show(BorealisLook(font = font))

        val pin = composeRule.onNodeWithContentDescription("4, 8, 1, 7").getUnclippedBoundsInRoot()
        assertThat(pin.width).isEqualTo(254.dp)
        assertThat(pin.height).isEqualTo(64.dp)
    }

    /**
     * A digit has the room that its glyph needs when its text is as high and as wide as the lines
     * of its layout: a cell of a fixed size shrinks the text to the cell, and the digit is cut.
     */
    private fun assertDigitsHaveTheirRoom() {
        composeRule.assertNoClippedText()
        for (digit in "4817") {
            val text = composeRule.onNodeWithText(digit.toString(), useUnmergedTree = true)
            val bounds = text.getUnclippedBoundsInRoot()
            val layout = text.textLayout()
            val scale = layout.layoutInput.density.density
            assertWithMessage("the height of $digit in $font")
                .that(bounds.height.value)
                .isAtLeast(layout.multiParagraph.height / scale - TOLERANCE_DP)
            assertWithMessage("the width of $digit in $font")
                .that(bounds.width.value)
                .isAtLeast(layout.multiParagraph.maxIntrinsicWidth / scale - TOLERANCE_DP)
        }
    }

    companion object {
        private const val TOLERANCE_DP = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> = fitCases()
    }
}
