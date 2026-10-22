package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.letterSpacingEm
import io.github.magalz.borealis.designsystem.testing.lineHeightEm
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The name of a font in the picker is drawn in that font and with the Text spacing of the look (ADR
 * 0058, decisions 2 and 4), and it follows a change of the setting while the picker is open.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ChoiceSheetSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                ChoiceSheet(
                    title = "Font",
                    options =
                        listOf(
                            ChoiceOption("b612", "B612", "For cockpit screens.", FontChoice.B612),
                            ChoiceOption("5 %", "5 %"),
                        ),
                    selected = "b612",
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun name(): TextStyle =
        composeRule.onNodeWithText("B612", useUnmergedTree = true).textLayout().layoutInput.style

    @Test
    fun `at Normal the name of a font has the spacing of the design`() {
        show()

        assertThat(name().letterSpacing.isSpecified).isFalse()
        assertThat(name().lineHeight.isSpecified).isFalse()
    }

    @Test
    fun `at Wider the name of a font has the letter spacing and the line height of the level`() {
        spacing = TextSpacing.Wider
        show()

        assertThat(name().letterSpacingEm()).isAtLeast(TextSpacing.Wider.letterEm)
        assertThat(name().lineHeightEm() ?: 0f).isAtLeast(TextSpacing.Wider.lineHeightEm)
    }

    @Test
    fun `a change of the setting reaches the name of a font while the picker is open`() {
        show()

        spacing = TextSpacing.Wide
        composeRule.waitForIdle()
        assertThat(name().letterSpacingEm()).isAtLeast(TextSpacing.Wide.letterEm)

        spacing = TextSpacing.Normal
        composeRule.waitForIdle()
        assertThat(name().letterSpacing.isSpecified).isFalse()
    }
}
