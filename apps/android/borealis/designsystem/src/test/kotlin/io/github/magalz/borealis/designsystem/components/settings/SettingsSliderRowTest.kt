package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsSliderRowTest {
    @get:Rule val composeRule = createComposeRule()
    private var value by mutableFloatStateOf(10f)
    private val changes = mutableListOf<Float>()

    @Test
    fun `Right and Left move the value by one step`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }
        assertThat(value).isEqualTo(12f)

        composeRule.onNodeWithText("Deadzone").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
        }
        assertThat(value).isEqualTo(8f)
        assertThat(changes).containsExactly(12f, 10f, 8f).inOrder()
    }

    @Test
    fun `the value stops at the top of the range`() {
        value = 19f
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }

        assertThat(value).isEqualTo(20f)
        assertThat(changes).containsExactly(20f)
    }

    @Test
    fun `the value stops at the bottom of the range`() {
        value = 1f
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
        }

        assertThat(value).isEqualTo(0f)
        assertThat(changes).containsExactly(0f)
    }

    @Test
    fun `a disabled row ignores the keys but stays a focus stop`() {
        composeRule.setContentInKeyboardMode { Specimen(enabled = false) }

        composeRule.onNodeWithText("Deadzone").requestFocus().assertIsFocused()
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }

        assertThat(changes).isEmpty()
    }

    @Test
    fun `the formatted value shows next to the title`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Deadzone", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("10 %", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the row exposes the value and its range as progress`() {
        composeRule.setContent { Specimen() }

        composeRule
            .onNodeWithText("Deadzone")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(10f, 0f..20f),
                )
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 %"))
    }

    @Test
    fun `a tap on the track sets the nearest step`() {
        composeRule.setContent { Specimen(step = 5f) }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            click(Offset(width * 0.75f, height - 8f))
        }

        assertThat(value).isEqualTo(15f)
    }

    @Test
    fun `dragging along the track moves the value to the end of the range`() {
        value = 0f
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            swipeRight(startX = 0f, endX = width.toFloat(), durationMillis = 200)
        }

        assertThat(value).isEqualTo(20f)
    }

    @Test
    fun `a disabled row ignores touch`() {
        composeRule.setContent { Specimen(enabled = false) }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            click(Offset(width * 0.75f, height - 8f))
        }

        assertThat(changes).isEmpty()
    }

    @Test
    fun `an accessibility service can set the progress`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Deadzone").performSemanticsAction(
            SemanticsActions.SetProgress
        ) {
            it(13.2f)
        }

        assertThat(value).isEqualTo(14f)
    }

    @Composable
    private fun Specimen(enabled: Boolean = true, step: Float = 2f) {
        BorealisTheme(FormFactor.Tv) {
            SettingsSliderRow(
                title = "Deadzone",
                value = value,
                valueRange = 0f..20f,
                step = step,
                valueLabel = "${value.toInt()} %",
                onValueChange = {
                    changes += it
                    value = it
                },
                enabled = enabled,
            )
        }
    }
}
