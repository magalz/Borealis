package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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

    private fun focusedTag(): String? =
        composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().firstNotNullOfOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
        }

    /** [withRail] draws the shell's rail: it is what has focus while the hosts load. */
    private fun show(withRail: Boolean = false) {
        composeRule.setContentInKeyboardMode {
            val screen =
                @Composable {
                    HostsScreen(
                        state = state.value,
                        formFactor = FormFactor.Tv,
                        snackbarHostState = SnackbarHostState(),
                        onHostSelected = { selected += it },
                        onContinue = {},
                        onWake = {},
                        onForget = {},
                        onDetails = {},
                        onDetailsDismissed = {},
                        onAddHost = {},
                        onSettings = {},
                    )
                }
            if (withRail) {
                LibraryScaffold(
                    FormFactor.Tv,
                    selected = RailSelection.Hosts,
                    actions = RailActions({}, {}, {}),
                    backgroundStyle = BackgroundStyle.Off,
                ) {
                    screen()
                }
            } else {
                screen()
            }
        }
    }

    private companion object {
        const val HOLD_MILLIS = 1_200L
    }
}
