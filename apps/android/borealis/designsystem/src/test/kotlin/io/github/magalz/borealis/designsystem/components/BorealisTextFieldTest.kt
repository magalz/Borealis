package io.github.magalz.borealis.designsystem.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The D-pad behaviour of the text field, when it opens the keyboard, and what it does when the
 * caller changes the text.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BorealisTextFieldTest {
    @get:Rule val composeRule = createComposeRule()

    private var value by mutableStateOf("192.168.1.20")

    private fun show(leaveWithDpad: Boolean = true, showKeyboardOnFocus: Boolean = true) {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) {
                Row {
                    Box(Modifier.size(40.dp).testTag(LEFT).focusable())
                    Column {
                        Box(Modifier.size(40.dp).testTag(ABOVE).focusable())
                        BorealisTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = "Address",
                            leaveWithDpad = leaveWithDpad,
                            showKeyboardOnFocus = showKeyboardOnFocus,
                        )
                        Box(Modifier.size(40.dp).testTag(BELOW).focusable())
                    }
                }
            }
        }
        composeRule.onNode(hasSetTextAction()).requestFocus()
        composeRule.waitForIdle()
    }

    /** Whether the system keyboard is up, as the framework's input method manager has it. */
    private fun keyboardIsShown(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(InputMethodManager::class.java)
        return shadowOf(manager).isSoftInputVisible
    }

    private fun press(key: Key) =
        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(key) }

    @Test
    fun `Down leaves the field for the neighbour below`() {
        show()

        press(Key.DirectionDown)

        composeRule.onNodeWithTag(BELOW).assertIsFocused()
    }

    @Test
    fun `Up leaves the field for the neighbour above`() {
        show()

        press(Key.DirectionUp)

        composeRule.onNodeWithTag(ABOVE).assertIsFocused()
    }

    @Test
    fun `Left at the start of the text leaves the field`() {
        show()
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(0))

        press(Key.DirectionLeft)

        composeRule.onNodeWithTag(LEFT).assertIsFocused()
    }

    @Test
    fun `Left in the middle of the text stays in the field`() {
        show()
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(4))

        press(Key.DirectionLeft)

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    @Test
    fun `without leaveWithDpad Down and Left at the start stay in the field`() {
        show(leaveWithDpad = false)
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(0))

        press(Key.DirectionLeft)
        press(Key.DirectionDown)

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `text that the caller clears wins and the field stays usable`() {
        show()

        composeRule.runOnIdle { value = "" }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assert(hasText(""))
        composeRule.onNode(hasSetTextAction()).performTextInput("10.0.0.5")

        composeRule.onNode(hasSetTextAction()).assert(hasText("10.0.0.5"))
    }

    @Test
    fun `text that the caller replaces wins and the caret goes to its end`() {
        show()

        composeRule.runOnIdle { value = "host.local" }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("2")

        composeRule.onNode(hasSetTextAction()).assert(hasText("host.local2"))
    }

    @Test
    fun `by default focus on the field opens the keyboard`() {
        show()

        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus focus on the field does not open the keyboard`() {
        show(showKeyboardOnFocus = false)

        assertThat(keyboardIsShown()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus coming back to the field does not open the keyboard`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionDown)
        composeRule.onNodeWithTag(BELOW).assertIsFocused()

        composeRule.onNode(hasSetTextAction()).requestFocus()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertThat(keyboardIsShown()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus DPAD_CENTER opens the keyboard`() {
        show(showKeyboardOnFocus = false)

        press(Key.DirectionCenter)

        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus Enter opens the keyboard`() {
        show(showKeyboardOnFocus = false)

        press(Key.Enter)

        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus the numeric keypad Enter opens the keyboard`() {
        show(showKeyboardOnFocus = false)

        press(Key.NumPadEnter)

        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus other keys do not open the keyboard`() {
        show(showKeyboardOnFocus = false)

        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertThat(keyboardIsShown()).isFalse()
    }

    private companion object {
        const val LEFT = "left"
        const val ABOVE = "above"
        const val BELOW = "below"
    }
}
