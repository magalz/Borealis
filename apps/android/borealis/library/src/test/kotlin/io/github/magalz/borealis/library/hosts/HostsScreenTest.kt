package io.github.magalz.borealis.library.hosts

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.setContentInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class HostsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val selected = mutableListOf<String>()
    private val continued = mutableListOf<String>()
    private val woken = mutableListOf<String>()
    private val forgotten = mutableListOf<String>()
    private val detailed = mutableListOf<String>()
    private var detailsDismissed = 0
    private var addHost = 0
    private var settings = 0
    private val unhandledKeys = mutableListOf<Key>()

    @Test
    @Config(qualifiers = PHONE)
    fun `the phone shows the title, the actions and both sections`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Hosts").assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE").assertIsDisplayed()
        composeRule.onNodeWithText("HOSTS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add host").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Add host and Settings actions of the top bar call back`() {
        showPhone(withHosts)

        composeRule.onNodeWithContentDescription("Add host").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(addHost).isEqualTo(1)
        assertThat(settings).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the phone has no navigation rail`() {
        showPhone(withHosts)

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Continue card says where the app runs and a tap continues it`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Running on Desk PC").assertIsDisplayed()
        composeRule.onNodeWithTag("continue:desk").performClick()

        assertThat(continued).containsExactly("desk")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `there is no Continue section without a running app`() {
        showPhone(withHostsNoContinue)

        composeRule.onNodeWithText("CONTINUE").assertDoesNotExist()
        composeRule.onNodeWithText("Resume").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host card shows its status and the app it runs`() {
        showPhone(withHosts.copy(items = listOf(deskPc, livingRoomPc, studio, checkingPc)))

        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("Not paired").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("Checking…").assertIsDisplayed()
        // The host card names the running app as well as the Continue card does.
        composeRule.onAllNodes(hasText("Desktop"), useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the inline buttons show the action of the state`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Open", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Wake", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pair", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host that was not polled yet has no button`() {
        showPhone(withHosts.copy(items = listOf(checkingPc), continueItems = emptyList()))

        composeRule.onNodeWithText("Checking…").assertIsDisplayed()
        composeRule.onNodeWithText("Open", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Pair", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Wake", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Open and Pair select the host and Wake wakes it`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Open", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Pair", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Wake", useUnmergedTree = true).performClick()

        assertThat(selected).containsExactly("desk", "studio").inOrder()
        assertThat(woken).containsExactly("living")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on a card selects the host`() {
        showPhone(withHosts)

        composeRule.onNodeWithTag("host:living").performClick()

        assertThat(selected).containsExactly("living")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a long press opens the options of an offline host that can wake`() {
        showPhone(withHosts)

        composeRule.onNodeWithTag("host:living").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithText(WAKE_CONSEQUENCE).assertIsDisplayed()
        composeRule.onNodeWithText("Forget").assertIsDisplayed()
        assertThat(selected).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the options of an online host have no Wake`() {
        showPhone(withHosts)

        composeRule.onNodeWithTag("host:desk").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithText("Forget").assertIsDisplayed()
        composeRule.onNodeWithText(WAKE_CONSEQUENCE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Details asks for the details of the host and closes the options`() {
        showPhone(withHosts)
        composeRule.onNodeWithTag("host:desk").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").performClick()
        composeRule.waitForIdle()

        assertThat(detailed).containsExactly("desk")
        composeRule.onNodeWithText("Forget").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Wake in the options wakes the host and closes the options`() {
        showPhone(withHosts)
        composeRule.onNodeWithTag("host:living").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(WAKE_CONSEQUENCE).performClick()
        composeRule.waitForIdle()

        assertThat(woken).containsExactly("living")
        composeRule.onNodeWithText("Forget").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Forget asks first and forgets only when confirmed`() {
        showPhone(withHosts)
        composeRule.onNodeWithTag("host:desk").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Forget Desk PC? You will need to pair again.")
            .assertIsDisplayed()
        assertThat(forgotten).isEmpty()

        composeRule.onNodeWithText("Forget").performClick()

        assertThat(forgotten).containsExactly("desk")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel in the confirmation keeps the host`() {
        showPhone(withHosts)
        composeRule.onNodeWithTag("host:desk").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(forgotten).isEmpty()
        composeRule
            .onNodeWithText("Forget Desk PC? You will need to pair again.")
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the details show in a dialog that Close dismisses`() {
        showPhone(withHosts.copy(details = "Desk PC, LuminalShine 26.09.1"))

        composeRule.onNodeWithText("Desk PC, LuminalShine 26.09.1").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        assertThat(detailsDismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `searching explains the next step and offers Add host`() {
        showPhone(searching)

        composeRule.onNodeWithText("Looking for hosts").assertIsDisplayed()
        composeRule
            .onNode(
                hasText("LuminalShine", substring = true) and hasText("network", substring = true)
            )
            .assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE").assertDoesNotExist()

        composeRule.onNodeWithText("Add host").performClick()

        assertThat(addHost).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the TV has a rail with Hosts selected, Add host and Settings`() {
        showTv(withHosts)

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
            )
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add host").performClick()
        composeRule.onNodeWithText("Settings").performClick()

        assertThat(addHost).isEqualTo(1)
        assertThat(settings).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the first Continue card has focus on a TV`() {
        showTv(withHosts)

        composeRule.onNodeWithTag("continue:desk").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `without Continue the first host card has focus`() {
        showTv(withHostsNoContinue)

        composeRule.onNodeWithTag("host:desk").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on the focused host selects it`() {
        showTv(withHostsNoContinue)

        composeRule.onNodeWithTag("host:desk").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(selected).containsExactly("desk")
    }

    @Test
    @Config(qualifiers = TV)
    fun `X on a focused host opens its options`() {
        showTv(withHosts)
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithText("Forget").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the TV shows the hosts in three columns`() {
        showTv(withHostsNoContinue)

        val tops =
            listOf("desk", "living", "studio").map {
                composeRule.onNodeWithTag("host:$it").getUnclippedBoundsInRoot().top
            }
        assertThat(tops.toSet()).hasSize(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the phone shows the hosts in one column`() {
        showPhone(withHostsNoContinue)

        val desk = composeRule.onNodeWithTag("host:desk").getUnclippedBoundsInRoot()
        val living = composeRule.onNodeWithTag("host:living").getUnclippedBoundsInRoot()
        assertThat(living.top).isGreaterThan(desk.bottom)
        assertThat(living.left).isEqualTo(desk.left)
    }

    @Test
    @Config(qualifiers = TV)
    fun `D-pad Left from the first card reaches the rail`() {
        showTv(withHosts)

        composeRule.onNodeWithTag("continue:desk").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and isFocused()
            )
            .assertExists()
    }

    @Test
    @Config(qualifiers = TV)
    fun `D-pad Right from the rail returns to the content`() {
        showTv(withHosts)
        composeRule.onNodeWithTag("continue:desk").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and isFocused()
            )
            .assertDoesNotExist()
        composeRule.onNode(isFocused()).assertExists()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B at the top level is consumed on a TV`() {
        showTv(withHosts)

        composeRule.onNodeWithTag("continue:desk").performKeyInput { pressKey(Key.ButtonB) }

        assertThat(unhandledKeys).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the remote Back key is not consumed so Back still leaves the app`() {
        showTv(withHosts)

        composeRule.onNodeWithTag("continue:desk").performKeyInput { pressKey(Key.Back) }

        assertThat(unhandledKeys).containsExactly(Key.Back)
    }

    @Test
    @Config(qualifiers = TV)
    fun `searching on a TV puts focus in the screen`() {
        showTv(searching)

        composeRule.onNode(isFocused()).assertExists()
    }

    private fun showPhone(state: HostsUiState) {
        composeRule.setContent { Screen(state, FormFactor.Phone) }
    }

    private fun showTv(state: HostsUiState) {
        composeRule.setContentInKeyboardMode { Screen(state, FormFactor.Tv) }
    }

    @Composable
    private fun Screen(state: HostsUiState, formFactor: FormFactor) {
        // A parent sees the keys that the screen did not consume.
        Box(
            Modifier.onKeyEvent {
                if (it.type == KeyEventType.KeyDown) unhandledKeys += it.key
                false
            }
        ) {
            HostsScreen(
                state = state,
                formFactor = formFactor,
                snackbarHostState = SnackbarHostState(),
                onHostSelected = { selected += it },
                onContinue = { continued += it },
                onWake = { woken += it },
                onForget = { forgotten += it },
                onDetails = { detailed += it },
                onDetailsDismissed = { detailsDismissed++ },
                onAddHost = { addHost++ },
                onSettings = { settings++ },
            )
        }
    }

    private companion object {
        const val WAKE_CONSEQUENCE = "Send a Wake-on-LAN packet to the PC."
    }
}
