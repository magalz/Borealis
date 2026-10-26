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
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
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

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }
        assertThat(value).isEqualTo(8f)
        assertThat(changes).containsExactly(12f, 10f, 8f).inOrder()
    }

    @Test
    fun `the value stops at the top of the range`() {
        value = 19f
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }

        assertThat(value).isEqualTo(20f)
        assertThat(changes).containsExactly(20f)
    }

    @Test
    fun `the value stops at the bottom of the range`() {
        value = 1f
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }

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
                    ProgressBarRangeInfo(10f, 0f..20f, steps = 9),
                )
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 %"))
    }

    @Test
    fun `a tap on the track sets the nearest step`() {
        composeRule.setContent { Specimen(step = 5f) }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            click(Offset(width * 0.75f, height * TRACK_Y))
        }

        assertThat(value).isEqualTo(15f)
    }

    @Test
    fun `dragging along the track moves the value to the end of the range`() {
        value = 0f
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            swipe(
                start = Offset(width * 0.1f, height * TRACK_Y),
                end = Offset(width - 1f, height * TRACK_Y),
                durationMillis = 200,
            )
        }

        assertThat(value).isEqualTo(20f)
    }

    @Test
    fun `a disabled row ignores touch`() {
        composeRule.setContent { Specimen(enabled = false) }

        composeRule.onNodeWithText("Deadzone").performTouchInput {
            click(Offset(width * 0.75f, height * TRACK_Y))
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

    @Test
    fun `the D-pad moves by the key step while a touch snaps to the step`() {
        value = 50f
        composeRule.setContentInKeyboardMode {
            Specimen(range = 0f..100f, step = 1f, keyStep = 10f)
        }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }
        assertThat(value).isEqualTo(60f)
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionLeft) }
        assertThat(value).isEqualTo(40f)

        composeRule.onNodeWithText("Deadzone").performSemanticsAction(
            SemanticsActions.SetProgress
        ) {
            it(43.2f)
        }
        assertThat(value).isEqualTo(43f)
    }

    @Test
    fun `a key step larger than what is left stops at the end of the range`() {
        value = 95f
        composeRule.setContentInKeyboardMode {
            Specimen(range = 0f..100f, step = 1f, keyStep = 10f)
        }
        composeRule.onNodeWithText("Deadzone").requestFocus()

        composeRule.onNodeWithText("Deadzone").performKeyInput { pressKey(Key.DirectionRight) }

        assertThat(value).isEqualTo(100f)
    }

    @Test
    fun `the summary shows under the title`() {
        composeRule.setContent { Specimen(summary = "How far the stick moves before it counts") }

        composeRule
            .onNodeWithText("How far the stick moves before it counts", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a disabled row shows its note and an enabled one does not`() {
        composeRule.setContent {
            Specimen(enabled = false, disabledNote = "The controller has no motors")
        }

        composeRule
            .onNodeWithText("The controller has no motors", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `the note of an enabled row stays hidden`() {
        composeRule.setContent { Specimen(disabledNote = "The controller has no motors") }

        composeRule
            .onNodeWithText("The controller has no motors", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a step that is not positive is refused`() {
        val failure = runCatching {
            composeRule.setContent { Specimen(step = 0f) }
        }
            .exceptionOrNull()

        assertThat(rootCause(failure)).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a key step finer than the step is refused`() {
        val failure = runCatching {
            composeRule.setContent { Specimen(step = 2f, keyStep = 1f) }
        }
            .exceptionOrNull()

        assertThat(rootCause(failure)).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun rootCause(failure: Throwable?): Throwable? {
        var cause = failure
        while (cause?.cause != null) cause = cause.cause
        return cause
    }

    @Composable
    private fun Specimen(
        enabled: Boolean = true,
        step: Float = 2f,
        keyStep: Float = step,
        range: ClosedFloatingPointRange<Float> = 0f..20f,
        summary: String? = null,
        disabledNote: String? = null,
    ) {
        BorealisTheme(FormFactor.Tv) {
            SettingsSliderRow(
                title = "Deadzone",
                value = value,
                valueRange = range,
                step = step,
                valueLabel = "${value.toInt()} %",
                onValueChange = {
                    changes += it
                    value = it
                },
                summary = summary,
                enabled = enabled,
                disabledNote = disabledNote,
                keyStep = keyStep,
            )
        }
    }

    private companion object {
        /** Where the track is, as a share of the height of the row: below the title. */
        const val TRACK_Y = 0.75f
    }
}
