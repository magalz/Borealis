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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
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
    fun `a paired host shows its address and tags`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("192.168.1.20").assertIsDisplayed()
        composeRule.onNodeWithText("HEVC 10-bit", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("HDR", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host that was found on the network says so instead of showing its address`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Found on this network").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.40", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an offline host keeps its detail line with the address`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("192.168.1.31").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host without an address that was not found on the network has no detail line`() {
        val addressed = studio.copy(id = "a", name = "A", foundOnNetwork = false)
        val unaddressed = studio.copy(id = "b", name = "B", address = null, foundOnNetwork = false)
        showPhone(withHostsNoContinue.copy(items = listOf(addressed, unaddressed)))

        composeRule.onNodeWithText("192.168.1.40").assertIsDisplayed()
        composeRule.onAllNodes(hasText("192.168.", substring = true)).assertCountEquals(1)
        composeRule.onNodeWithText("Found on this network").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a card has no button of its own`() {
        showPhone(withHosts)

        composeRule.onNodeWithText("Open", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Wake", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Pair", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a card says to a screen reader what a tap does in its state`() {
        showPhone(withHosts.copy(items = listOf(deskPc, livingRoomPc, studio, checkingPc)))

        assertThat(clickLabel("host:desk")).isEqualTo("Open")
        assertThat(clickLabel("host:living")).isEqualTo("Wake")
        assertThat(clickLabel("host:studio")).isEqualTo("Pair")
        assertThat(clickLabel("host:new")).isNull()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on a card opens a paired host and starts pairing with one that is not paired`() {
        showPhone(withHosts)

        composeRule.onNodeWithTag("host:desk").performClick()
        composeRule.onNodeWithTag("host:studio").performClick()

        assertThat(selected).containsExactly("desk", "studio").inOrder()
        assertThat(woken).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the card of an offline host wakes it and does not select it`() {
        showPhone(withHosts)

        composeRule.onNodeWithTag("host:living").performClick()

        assertThat(woken).containsExactly("living")
        assertThat(selected).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on a card that is not polled yet selects it`() {
        showPhone(withHosts.copy(items = listOf(checkingPc), continueItems = emptyList()))

        composeRule.onNodeWithTag("host:new").performClick()

        assertThat(selected).containsExactly("new")
        assertThat(woken).isEmpty()
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

        composeRule.onNodeWithText(FORGET_CONFIRMATION).assertIsDisplayed()
        assertThat(forgotten).isEmpty()

        composeRule.onNodeWithText("Forget").performClick()

        assertThat(forgotten).containsExactly("desk")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Forget confirmation opens with the focus on Cancel`() {
        showTv(withHosts)
        composeRule.onNodeWithTag("host:desk").requestFocus()
        composeRule.onNodeWithTag("host:desk").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FORGET_CONFIRMATION).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Forget option and its confirmation say what it does and do not promise a re-pair`() {
        showPhone(withHosts)
        composeRule.onNodeWithTag("host:desk").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FORGET_CONSEQUENCE).assertIsDisplayed()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FORGET_CONFIRMATION).assertIsDisplayed()
        composeRule.onNodeWithText("pair again", substring = true).assertDoesNotExist()
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
        composeRule.onNodeWithText(FORGET_CONFIRMATION).assertDoesNotExist()
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
    @Config(qualifiers = PHONE)
    fun `loading shows the loader and not the empty state with its Add host button`() {
        showPhone(loadingHosts)

        composeRule.onNodeWithTag("hosts-loading").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for hosts").assertDoesNotExist()
        // The phone's top bar names Add host by description only: this text is the empty-state
        // button.
        composeRule.onNodeWithText("Add host").assertDoesNotExist()
        composeRule.onNodeWithText("CONTINUE").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `loading shows the aurora loader, not a progress bar`() {
        showPhone(loadingHosts)

        composeRule.onNodeWithTag("hosts-loading").assertIsDisplayed()
        composeRule
            .onNodeWithTag("hosts-loading")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        composeRule.onNodeWithContentDescription("Loading hosts").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `loading on a TV shows the loader and leaves the focus in the rail`() {
        showTv(loadingHosts)

        composeRule.onNodeWithTag("hosts-loading").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for hosts").assertDoesNotExist()
        composeRule
            .onNode(isFocused())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an app without a known name is called an app that is running`() {
        showPhone(
            HostsUiState(
                loading = false,
                searching = false,
                items = listOf(unnamedAppPc),
                continueItems = listOf(ContinueItemUiState("unnamed", "Den PC", 7, null)),
            )
        )

        composeRule
            .onAllNodes(hasText("An app is running"), useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onNodeWithText("app").assertDoesNotExist()
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
    fun `OK on the card of an offline host wakes it and does not select it`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(woken).containsExactly("living")
        assertThat(selected).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on the card of a host that is not paired starts pairing`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:studio").requestFocus()

        composeRule.onNodeWithTag("host:studio").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(selected).containsExactly("studio")
        assertThat(woken).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a host card is one focus stop so Down does not move into the card`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:desk").requestFocus()

        composeRule.onNodeWithTag("host:desk").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("host:desk").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `holding OK on the card of an online host opens its options and runs nothing`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:desk").requestFocus()

        composeRule.onNodeWithTag("host:desk").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        assertThat(selected).isEmpty()
        assertThat(woken).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `holding OK on the card of an offline host opens its options and does not wake it`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:living").requestFocus()

        composeRule.onNodeWithTag("host:living").performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(HOLD_MILLIS)
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        assertThat(woken).isEmpty()
        assertThat(selected).isEmpty()
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
    fun `Back from the options returns the focus to the same host card`() {
        showTv(withHostsNoContinue)
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Details").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Details").assertDoesNotExist()
        composeRule.onNodeWithTag("host:living").assertIsFocused()
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

    private fun clickLabel(tag: String): String? =
        composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.OnClick)
            ?.label

    private fun showPhone(state: HostsUiState) {
        composeRule.setContent { Screen(state, FormFactor.Phone) }
    }

    private fun showTv(state: HostsUiState) {
        composeRule.setContentInKeyboardMode { Screen(state, FormFactor.Tv) }
    }

    @Composable
    private fun Screen(state: HostsUiState, formFactor: FormFactor) {
        // A parent sees the keys that the screen did not consume. The shell draws the rail of a TV.
        Box(
            Modifier.onKeyEvent {
                if (it.type == KeyEventType.KeyDown) unhandledKeys += it.key
                false
            }
        ) {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, { addHost++ }, { settings++ }),
                backgroundStyle = BackgroundStyle.Off,
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
    }

    private companion object {
        const val HOLD_MILLIS = 1_200L
        const val WAKE_CONSEQUENCE = "Send a Wake-on-LAN packet to the PC."
        const val FORGET_CONSEQUENCE =
            "Remove it from this device. " +
                "The PC may keep trusting this device until you remove it " +
                "in the LuminalShine Web UI (Clients page)."
        const val FORGET_CONFIRMATION =
            "Forget Desk PC on this device? " +
                "The PC may keep trusting this device until you remove it " +
                "in the LuminalShine Web UI (Clients page)."
    }
}
