package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ChoiceSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val selections = mutableListOf<String>()
    private var dismissed = 0

    private val options =
        listOf(
            ChoiceOption("720p", "1280 x 720"),
            ChoiceOption("1080p", "1920 x 1080", summary = "Matches most displays"),
            ChoiceOption("native", "Native"),
        )

    @Test
    @Config(qualifiers = TV)
    fun `the title and every option show`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("1280 x 720").assertIsDisplayed()
        composeRule.onNodeWithText("1920 x 1080").assertIsDisplayed()
        composeRule.onNodeWithText("Matches most displays").assertIsDisplayed()
        composeRule.onNodeWithText("Native").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the selected option is marked and the others are not`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }

        composeRule
            .onNodeWithText("1920 x 1080")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule
            .onNodeWithText("1280 x 720")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    @Config(qualifiers = TV)
    fun `the selected option has the initial focus on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1920 x 1080").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `with no option selected the first has the initial focus`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "unknown") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1280 x 720").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on another option returns its key and closes`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Native").requestFocus()

        composeRule.onNodeWithText("Native").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(selections).containsExactly("native")
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap returns the key and closes`() {
        composeRule.setContent { Specimen(FormFactor.Phone, selected = "1080p") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1280 x 720").performClick()

        assertThat(selections).containsExactly("720p")
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back closes the dialog without a choice`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(selections).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back closes the bottom sheet without a choice`() {
        composeRule.setContent { Specimen(FormFactor.Phone) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(selections).isEmpty()
    }

    @Composable
    private fun Specimen(formFactor: FormFactor, selected: String = "720p") {
        BorealisTheme(formFactor) {
            ChoiceSheet(
                title = "Resolution",
                options = options,
                selected = selected,
                onSelect = { selections += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
