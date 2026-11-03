package io.github.magalz.borealis.library.hosts

import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Where focus goes on a TV: it starts once, and the D-pad reaches every part without traps. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class HostsFocusTest {
    @get:Rule val composeRule = createComposeRule()

    private val state = mutableStateOf(withHosts)
    private val selected = mutableListOf<String>()
    private val woken = mutableListOf<String>()
    private var window: View? = null
    private val aardvark =
        HostItemUiState("aard", "Aardvark", Reachability.ONLINE, true, null, HostAction.OPEN)

    @Test
    fun `a host that appears before the focused card does not take the focus`() {
        state.value = withHostsNoContinue
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()

        state.value = withHostsNoContinue.copy(items = listOf(aardvark) + withHosts.items)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("host:aard").assertIsDisplayed()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `a Continue card that appears does not take the focus`() {
        state.value = withHostsNoContinue
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()

        state.value = withHosts
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").assertIsDisplayed()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `the first card takes the focus when the hosts arrive after the search`() {
        state.value = searching
        show()

        state.value = withHosts
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    fun `the first card takes the focus when the hosts arrive after the loading`() {
        state.value = loadingHosts
        show(withRail = true)

        state.value = withHosts
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    fun `Down from the Continue card reaches a host card and Up returns`() {
        show()

        composeRule.onNodeWithTag("continue:desk").performKeyInput { pressKey(Key.DirectionDown) }
        assertThat(focusedTag()).startsWith("host:")

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    fun `Up from the second column reaches the Continue card`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    fun `Up from the third column reaches the Continue card`() {
        show()
        composeRule.onNodeWithTag("host:studio").requestFocus()

        composeRule.onNodeWithTag("host:studio").performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    fun `holding OK on a host card opens its options and does not select it`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        assertThat(selected).isEmpty()
    }

    @Test
    fun `Back from the options gives the focus back to the card that opened them`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()
        openOptions("host:living")
        resetWindowFocus()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertDoesNotExist()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `an option that keeps the screen gives the focus back to the card that opened the options`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()
        openOptions("host:living")
        resetWindowFocus()

        composeRule.onNodeWithText("Wake").performClick()
        composeRule.waitForIdle()

        assertThat(woken).containsExactly("living")
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `closing the details gives the focus back to the card that opened the options`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()
        openOptions("host:living")
        composeRule.onNodeWithText("Details").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        resetWindowFocus()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Close").assertDoesNotExist()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `cancelling the Forget confirmation gives the focus back to the card that opened the options`() {
        show()
        composeRule.onNodeWithTag("host:living").requestFocus()
        openOptions("host:living")
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        resetWindowFocus()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
    }

    @Test
    fun `a card that once opened the options does not take the focus when the screen comes back`() {
        val restoration = StateRestorationTester(composeRule)
        var inputMode: InputModeManager? = null
        restoration.setContent {
            inputMode = LocalInputModeManager.current
            Screen()
        }
        composeRule.runOnIdle { checkNotNull(inputMode).requestInputMode(InputMode.Keyboard) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:living").requestFocus()
        openOptions("host:living")
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("continue:desk").assertIsFocused()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    private fun focusedTag(): String? =
        composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
        }

    /** X on a focused card opens the options of its host. */
    private fun openOptions(tag: String) {
        composeRule.onNodeWithTag(tag).performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    /**
     * What a window that opens over the screen can do to the one under it (Sprint 14 UAT, U11):
     * Android takes the focus from its view and gives it back, and Compose then puts the focus on
     * the first element and not on the one that had it. The dialogs of Robolectric leave the focus
     * alone, so the test does what the device did.
     */
    private fun resetWindowFocus() {
        composeRule.runOnUiThread { checkNotNull(window).clearFocus() }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Screen() {
        window = LocalView.current
        HostsScreen(
            state = state.value,
            formFactor = FormFactor.Tv,
            snackbarHostState = SnackbarHostState(),
            onHostSelected = { selected += it },
            onContinue = {},
            onWake = { woken += it },
            onForget = {},
            onDetails = { state.value = state.value.copy(details = DETAILS) },
            onDetailsDismissed = { state.value = state.value.copy(details = null) },
            onAddHost = {},
            onSettings = {},
        )
    }

    /** [withRail] draws the shell's rail: it is what has focus while the hosts load. */
    private fun show(withRail: Boolean = false) {
        composeRule.setContentInKeyboardMode {
            if (withRail) {
                LibraryScaffold(
                    FormFactor.Tv,
                    selected = RailSelection.Hosts,
                    actions = RailActions({}, {}, {}),
                    backgroundStyle = BackgroundStyle.Off,
                ) {
                    Screen()
                }
            } else {
                Screen()
            }
        }
    }

    private companion object {
        const val HOLD_MILLIS = 1_200L
        const val DETAILS = "Windows 11, 4 GB of video memory"
    }
}
