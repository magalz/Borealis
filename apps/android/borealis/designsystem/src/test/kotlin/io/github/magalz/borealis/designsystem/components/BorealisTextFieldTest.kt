package io.github.magalz.borealis.designsystem.components

import android.content.Context
import android.view.View
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
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
    private lateinit var view: View
    private var accent = Color.Unspecified
    private var done = 0
    private var rejectEdits = false
    private val reported = mutableListOf<String>()

    private fun show(leaveWithDpad: Boolean = true, showKeyboardOnFocus: Boolean = true) {
        composeRule.setContentInKeyboardMode {
            view = LocalView.current
            BorealisTheme(FormFactor.Tv) {
                accent = LocalBorealisColors.current.accent
                Row {
                    Box(Modifier.size(40.dp).testTag(LEFT).focusable())
                    Column {
                        Box(Modifier.size(40.dp).testTag(ABOVE).focusable())
                        BorealisTextField(
                            value = value,
                            onValueChange = {
                                reported += it
                                if (!rejectEdits) value = it
                            },
                            onDone = { done++ },
                            label = "Address",
                            leaveWithDpad = leaveWithDpad,
                            showKeyboardOnFocus = showKeyboardOnFocus,
                        )
                        Box(Modifier.size(40.dp).testTag(BELOW).focusable())
                    }
                }
            }
        }
        // Without the keyboard on focus the D-pad lands on what wraps the field, not on the field.
        (if (showKeyboardOnFocus) editable() else wrapper()).requestFocus()
        composeRule.waitForIdle()
    }

    private fun editable() = composeRule.onNode(hasSetTextAction())

    private fun caret() =
        editable().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].start

    /** What takes the D-pad focus while the field is not being edited: it lies over the field. */
    private fun wrapper() = composeRule.onNode(hasContentDescription("Address", substring = true))

    /**
     * Whether the window has a text input session, which is what the input method needs to open a
     * keyboard for the app (an editor, in the log of the input method).
     */
    private fun textSessionIsOpen() = composeRule.runOnIdle { view.onCheckIsTextEditor() }

    /**
     * Whether the system keyboard is up, as the framework's input method manager has it. This is
     * what the device's `dumpsys input_method` reports as shown, so the tests ask the platform and
     * not a stand-in for the software keyboard controller.
     */
    private fun keyboardIsShown(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(InputMethodManager::class.java)
        return shadowOf(manager).isSoftInputVisible
    }

    /** Presses [key] on whatever has the focus, as a remote does. */
    private fun press(key: Key) = composeRule.onNode(isFocused()).performKeyInput { pressKey(key) }

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

        editable().assertIsNotFocused()
        assertThat(keyboardIsShown()).isFalse()
        assertThat(textSessionIsOpen()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus the wrapper has the focused look of the field`() {
        show(showKeyboardOnFocus = false)

        wrapper().assertIsFocused()
        // The border at the left edge, halfway down the field.
        editable().assertPixel(1f, 32f, accent)
        press(Key.DirectionDown)
        composeRule.waitForIdle()

        assertThat(editable().pixelAt(1f, 32f)).isNotEqualTo(accent)
    }

    @Test
    fun `without showKeyboardOnFocus OK moves the focus into the field and opens the keyboard`() {
        show(showKeyboardOnFocus = false)

        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        editable().assertIsFocused()
        assertThat(textSessionIsOpen()).isTrue()
        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus Down leaves the field being edited and ends the editing`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        press(Key.DirectionDown)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BELOW).assertIsFocused()
        editable().assertIsNotFocused()
        assertThat(textSessionIsOpen()).isFalse()

        // Back on the field, the focus is on its wrapper and the keyboard stays shut.
        wrapper().requestFocus()
        composeRule.waitForIdle()

        wrapper().assertIsFocused()
        editable().assertIsNotFocused()
        assertThat(textSessionIsOpen()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus Left at the start of the text leaves the field being edited`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()
        editable().performTextInputSelection(TextRange(0))

        press(Key.DirectionLeft)

        composeRule.onNodeWithTag(LEFT).assertIsFocused()
    }

    @Test
    fun `without showKeyboardOnFocus what is typed while editing reaches the caller`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        editable().performTextInput("5")

        assertThat(reported).containsExactly("192.168.1.205")
        editable().assert(hasText("192.168.1.205"))
    }

    @Test
    fun `without showKeyboardOnFocus Done while editing is reported and the focus goes back to the wrapper`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        press(Key.Enter)
        composeRule.waitForIdle()

        assertThat(done).isEqualTo(1)
        wrapper().assertIsFocused()
        editable().assertIsNotFocused()
        assertThat(keyboardIsShown()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus a tap on the field being edited keeps it focused and moves the caret`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()
        assertThat(caret()).isEqualTo(12)

        editable().performTouchInput { click(Offset(48f, centerY)) }
        composeRule.waitForIdle()

        editable().assertIsFocused()
        assertThat(caret()).isLessThan(12)
    }

    @Test
    fun `without showKeyboardOnFocus accessibility meets one node that says the text until editing`() {
        show(showKeyboardOnFocus = false)

        composeRule.onNode(hasContentDescription("Address, 192.168.1.20")).assertExists()
        editable().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))

        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        editable().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun `without showKeyboardOnFocus an empty field says only its label`() {
        value = ""
        show(showKeyboardOnFocus = false)

        composeRule.onNode(hasContentDescription("Address")).assertExists()
    }

    @Test
    fun `without showKeyboardOnFocus OK after the keyboard was closed opens it again`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionCenter)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            view.context
                .getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
        assertThat(keyboardIsShown()).isFalse()

        press(Key.DirectionCenter)

        editable().assertIsFocused()
        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    fun `without showKeyboardOnFocus coming back to the field does not open the keyboard`() {
        show(showKeyboardOnFocus = false)
        press(Key.DirectionDown)
        composeRule.onNodeWithTag(BELOW).assertIsFocused()

        wrapper().requestFocus()
        composeRule.waitForIdle()

        wrapper().assertIsFocused()
        assertThat(keyboardIsShown()).isFalse()
        assertThat(textSessionIsOpen()).isFalse()
    }

    @Test
    fun `without showKeyboardOnFocus Enter is Done, not OK`() {
        show(showKeyboardOnFocus = false)

        press(Key.Enter)

        assertThat(done).isEqualTo(1)
        assertThat(keyboardIsShown()).isFalse()
        editable().assertIsNotFocused()
    }

    @Test
    fun `without showKeyboardOnFocus the numeric keypad Enter is Done, not OK`() {
        show(showKeyboardOnFocus = false)

        press(Key.NumPadEnter)

        assertThat(done).isEqualTo(1)
        assertThat(keyboardIsShown()).isFalse()
        editable().assertIsNotFocused()
    }

    @Test
    fun `by default Enter is Done`() {
        show()

        press(Key.Enter)

        assertThat(done).isEqualTo(1)
    }

    @Test
    fun `an edit that the caller rejects is undone`() {
        show()
        rejectEdits = true

        composeRule.onNode(hasSetTextAction()).performTextInput("5")
        composeRule.waitForIdle()

        assertThat(reported).containsExactly("192.168.1.205")
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
        composeRule.onNode(hasSetTextAction()).performTextInput("7")
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
        assertThat(reported).containsExactly("192.168.1.205", "192.168.1.207").inOrder()
    }

    @Test
    fun `an edit that undoes the last one before the caller catches up is reported`() {
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNode(hasSetTextAction()).performTextInput("5")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("192.168.1.20")
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertThat(reported).containsExactly("192.168.1.205", "192.168.1.20").inOrder()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
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
