package io.github.magalz.borealis.library.addhost

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AddHostScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val changes = mutableListOf<String>()
    private lateinit var view: View
    private var submitted = 0
    private var cancelled = 0

    @Test
    @Config(qualifiers = PHONE)
    fun `the field has focus when the screen opens`() {
        show(AddHostUiState())

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the address has the focus on a TV as well, on what lies over the field`() {
        show(AddHostUiState(), FormFactor.Tv)

        addressWrapper().assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the label, the hint and the port line show`() {
        show(AddHostUiState())

        composeRule.onNodeWithText("Address").assertIsDisplayed()
        composeRule
            .onNodeWithText("IP address or host name, for example 192.168.1.20")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Add :port only if you changed the LuminalShine port (default 47989).")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `typing reports the address`() {
        show(AddHostUiState())

        composeRule.onNode(hasSetTextAction()).performTextInput("192.168.1.20")

        assertThat(changes).containsExactly("192.168.1.20")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Done on the keyboard submits`() {
        show(AddHostUiState(address = "192.168.1.20"))

        composeRule.onNode(hasSetTextAction()).performImeAction()

        assertThat(submitted).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host submits`() {
        show(AddHostUiState(address = "192.168.1.20"))

        composeRule.onNode(hasText("Add host") and hasClickAction()).performClick()

        assertThat(submitted).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel calls onCancel and submits nothing`() {
        show(AddHostUiState(address = "192.168.1.20"))

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(cancelled).isEqualTo(1)
        assertThat(submitted).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone has a back arrow that returns to Hosts`() {
        show(AddHostUiState())

        composeRule.onNodeWithContentDescription("Back to Hosts").performClick()

        assertThat(cancelled).isEqualTo(1)
        assertThat(submitted).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV has no back arrow because the rail leads back to Hosts`() {
        show(AddHostUiState(), FormFactor.Tv)

        composeRule.onNodeWithContentDescription("Back to Hosts").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the content is at most 560dp wide and starts at the left like the other screens`() {
        show(AddHostUiState(), FormFactor.Tv)

        val field = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        val button =
            composeRule.onNode(hasText("Add host") and hasClickAction()).getUnclippedBoundsInRoot()

        assertThat(field.right.value - field.left.value)
            .isAtMost(BorealisDimens.contentMaxWidth.value)
        assertThat(field.left.value).isWithin(1f).of(button.left.value)
        assertThat(field.left.value).isAtMost(MAX_GUTTER_DP)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the back arrow is disabled while busy`() {
        show(AddHostUiState(address = "192.168.1.20", busy = true))

        composeRule.onNodeWithContentDescription("Back to Hosts").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back cancels`() {
        show(AddHostUiState())

        Espresso.pressBack()

        assertThat(cancelled).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back does nothing while busy`() {
        show(AddHostUiState(address = "192.168.1.20", busy = true))

        Espresso.pressBack()

        assertThat(cancelled).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an empty address shows its error`() {
        show(AddHostUiState(error = AddHostError.EMPTY))

        composeRule.onNodeWithText("Enter an IP address or a host name.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an invalid address shows its error`() {
        show(AddHostUiState(error = AddHostError.INVALID))

        composeRule
            .onNodeWithText("Unable to resolve this address. Check it for typos.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a wrong subnet shows its error`() {
        show(AddHostUiState(error = AddHostError.WRONG_SUBNET))

        composeRule
            .onNodeWithText(
                "That address is not on this network. " +
                    "For streaming over the Internet, use your router's public IP address."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an unreachable host shows its error`() {
        show(AddHostUiState(error = AddHostError.UNREACHABLE))

        composeRule
            .onNodeWithText(
                "Unable to connect to the specified computer. " +
                    "Make sure the required ports are allowed through the firewall."
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `no error text without an error`() {
        show(AddHostUiState())

        composeRule.onNodeWithText("Enter an IP address or a host name.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `while busy there is a progress indicator and the buttons are disabled`() {
        show(AddHostUiState(address = "192.168.1.20", busy = true))

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
        composeRule.onNode(hasText("Add host") and hasClickAction()).assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `when idle there is no progress indicator and the buttons are enabled`() {
        show(AddHostUiState(address = "192.168.1.20"))

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
        composeRule.onNode(hasText("Add host") and hasClickAction()).assertIsEnabled()
        composeRule.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Down from an address with text leaves the field for Add host`() {
        showStateful("192.168.1.20")
        startEditing()

        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNode(hasText("Add host") and hasClickAction()).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Left at the start of the address leaves the field for the neighbour`() {
        showStateful("192.168.1.20")
        startEditing()
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(0))

        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithTag(NEIGHBOUR).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Left in the middle of the address moves the caret and keeps focus`() {
        showStateful("192.168.1.20")
        startEditing()
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(4))

        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the address is unchanged after leaving the field and coming back`() {
        showStateful("192.168.1.20")
        startEditing()

        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNode(hasText("Add host") and hasClickAction()).assertIsFocused()
        composeRule.onNode(hasText("Add host") and hasClickAction()).performKeyInput {
            pressKey(Key.DirectionUp)
        }

        addressWrapper().assertIsFocused()
        composeRule.onNode(hasSetTextAction()).assert(hasText("192.168.1.20"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Down from the address before OK goes to Add host`() {
        showStateful("192.168.1.20")

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNode(hasText("Add host") and hasClickAction()).assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the keyboard opens with the focus`() {
        show(AddHostUiState())

        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the focus on the address does not open the keyboard`() {
        showStateful("192.168.1.20")

        addressWrapper().assertIsFocused()
        assertThat(keyboardIsShown()).isFalse()
        assertThat(textSessionIsOpen()).isFalse()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV coming back to the address with Up does not open the keyboard`() {
        showStateful("192.168.1.20")
        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNode(hasText("Add host") and hasClickAction()).performKeyInput {
            pressKey(Key.DirectionUp)
        }

        addressWrapper().assertIsFocused()
        assertThat(keyboardIsShown()).isFalse()
        assertThat(textSessionIsOpen()).isFalse()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV OK on the address moves the focus into the field and opens the keyboard`() {
        showStateful("192.168.1.20")

        startEditing()

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertThat(textSessionIsOpen()).isTrue()
        assertThat(keyboardIsShown()).isTrue()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone a hardware Enter submits`() {
        show(AddHostUiState(address = "192.168.1.20"))

        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        assertThat(submitted).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Enter submits and does not open the keyboard`() {
        showStateful("192.168.1.20")

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }

        assertThat(submitted).isEqualTo(1)
        assertThat(keyboardIsShown()).isFalse()
    }

    /** What takes the D-pad focus on a TV until OK: it lies over the field. */
    private fun addressWrapper() =
        composeRule.onNode(hasContentDescription("Address", substring = true))

    /** OK on the address, as a remote opens it for typing. */
    private fun startEditing() {
        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    private fun textSessionIsOpen() = composeRule.runOnIdle { view.onCheckIsTextEditor() }

    private fun keyboardIsShown(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return shadowOf(context.getSystemService(InputMethodManager::class.java)).isSoftInputVisible
    }

    /**
     * The screen with a focusable neighbour on its left, as the rail is in the Library, and an
     * address that the test keeps, so the caret and the text behave as they do for a user.
     */
    private fun showStateful(initial: String) {
        composeRule.setContentInKeyboardMode {
            view = LocalView.current
            var address by remember { mutableStateOf(initial) }
            Row {
                Box(Modifier.size(40.dp).testTag(NEIGHBOUR).focusable())
                AddHostScreen(
                    state = AddHostUiState(address = address),
                    formFactor = FormFactor.Tv,
                    onAddressChanged = { address = it },
                    onSubmit = { submitted++ },
                    onCancel = { cancelled++ },
                )
            }
        }
    }

    private fun show(state: AddHostUiState, formFactor: FormFactor = FormFactor.Phone) {
        composeRule.setContent {
            AddHostScreen(
                state = state,
                formFactor = formFactor,
                onAddressChanged = { changes += it },
                onSubmit = { submitted++ },
                onCancel = { cancelled++ },
            )
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val NEIGHBOUR = "neighbour"
        const val MAX_GUTTER_DP = 64f
    }
}
