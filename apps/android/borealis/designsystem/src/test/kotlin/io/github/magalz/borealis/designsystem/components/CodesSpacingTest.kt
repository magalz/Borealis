package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.letterSpacingEm
import io.github.magalz.borealis.designsystem.testing.lineHeightEm
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The small texts in the font of the codes carry the Text spacing of the look (ADR 0058, decision
 * 4): the label of a chip and the number of a step. They are built from the size and the family of
 * the codes style, so they must be given the level on top. At Normal they are drawn as they always
 * were, with no letter spacing and no line height of their own.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CodesSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                Column {
                    Chip("Alt+Tab", onClick = {})
                    StepList(listOf("Connecting", "Starting"), current = 0)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun style(text: String): TextStyle =
        composeRule.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style

    private fun assertDrawsAt(level: TextSpacing) {
        for (text in listOf("Alt+Tab", "1")) {
            val style = style(text)

            if (level == TextSpacing.Normal) {
                assertWithMessage("$text at $level").that(style.letterSpacing.isSpecified).isFalse()
                assertWithMessage("$text at $level").that(style.lineHeight.isSpecified).isFalse()
            } else {
                assertWithMessage("$text at $level")
                    .that(style.letterSpacingEm())
                    .isAtLeast(level.letterEm)
                assertWithMessage("$text at $level")
                    .that(style.lineHeightEm() ?: 0f)
                    .isAtLeast(level.lineHeightEm)
            }
        }
    }

    @Test
    fun `at Normal a chip and the number of a step have no spacing of their own`() {
        show()

        assertDrawsAt(TextSpacing.Normal)
    }

    @Test
    fun `at Wide a chip and the number of a step have the letter spacing and the line height`() {
        spacing = TextSpacing.Wide
        show()

        assertDrawsAt(TextSpacing.Wide)
    }

    @Test
    fun `at Wider a chip and the number of a step have the letter spacing and the line height`() {
        spacing = TextSpacing.Wider
        show()

        assertDrawsAt(TextSpacing.Wider)
    }

    @Test
    fun `a change of the setting reaches a chip and the number of a step`() {
        show()

        spacing = TextSpacing.Wider
        composeRule.waitForIdle()
        assertDrawsAt(TextSpacing.Wider)

        spacing = TextSpacing.Normal
        composeRule.waitForIdle()
        assertDrawsAt(TextSpacing.Normal)
        assertThat(style("Alt+Tab").letterSpacingEm()).isEqualTo(0f)
    }
}
