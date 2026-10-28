package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateSpeakableTextCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.AcceptedFinding
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.StaysAsSnackbar
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.showing
import io.github.magalz.borealis.designsystem.testing.taggedWith
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.addhost.AddHostError
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.loadingHosts
import io.github.magalz.borealis.library.hosts.searching
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.pairing.PairingFailure
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Google's Accessibility Test Framework over the Library screens, in the shell that the app draws
 * them in: Hosts, Apps, Add host and Pairing, on a phone, a TV and a handheld, with the sheets and
 * the dialogs that open over them. The glass is off here (`BackgroundStyle.Off`), so the contrast
 * checks see the colours that the text really has; over the animated background the contrast is
 * measured by the tool in `design/accessibility/`, which docs/ux/accessibility.md describes.
 */
@RunWith(AndroidJUnit4::class)
class LibraryAccessibilityChecksTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * The primary button of Add host says what the screen is for, as its title and the item of the
     * rail do. A screen reader says heading, tab or button after the name, so the three are told
     * apart, and the label of the button has to be the visible one (WCAG 2.5.3).
     */
    private val sameNameAsTheTitle =
        AcceptedFinding(
            DuplicateSpeakableTextCheck::class.java,
            "the Add host button, the title and the rail item of the screen carry one name; the " +
                "role tells them apart, and the label must stay the visible one (WCAG 2.5.3)",
            taggedWith("addhost-submit"),
        )

    /** The top bar and the empty state of Hosts both offer Add host, and do the same thing. */
    private val twoWaysToAddAHost =
        AcceptedFinding(
            DuplicateSpeakableTextCheck::class.java,
            "the Add host action of the top bar and the Add host button of the empty state do " +
                "the same thing, so hearing the name twice costs nothing",
            taggedWith("hosts-add-host"),
        )

    private val names = listOf("Desktop", "Steam Big Picture", "Playnite", "Hades II", "Elden Ring")
    private val waiting =
        PairingUiState.Waiting("4817", "https://192.168.1.40:47990", "Galaxy S22", false)

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts on a phone`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Hosts(it) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Hosts on a handheld`() {
        show(FormFactor.Handheld, RailSelection.Hosts) { Hosts(it) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Hosts on a TV`() {
        show(FormFactor.Tv, RailSelection.Hosts) { Hosts(it) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts with a snackbar on a phone`() {
        show(FormFactor.Phone, RailSelection.Hosts) { HostsWithSnackbar(it) }

        composeRule.onNodeWithText(SNACKBAR).assertIsDisplayed()
        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Hosts with a snackbar on a TV`() {
        show(FormFactor.Tv, RailSelection.Hosts) { HostsWithSnackbar(it) }

        composeRule.onNodeWithText(SNACKBAR).assertIsDisplayed()
        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts while the hosts load`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Hosts(it, loadingHosts) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts with no host found`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Hosts(it, searching) }

        composeRule.assertAccessible(twoWaysToAddAHost)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the options of a host on a phone`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Hosts(it) }
        composeRule.onNodeWithTag("host:living").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the options of a host on a TV`() {
        show(FormFactor.Tv, RailSelection.Hosts) { Hosts(it) }
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the details of a host`() {
        show(FormFactor.Phone, RailSelection.Hosts) {
            Hosts(it, withHosts.copy(details = "Desk PC\nLuminalShine 0.24.1\nGFE 3.27"))
        }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Apps on a phone`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Apps(it) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Apps on a handheld`() {
        show(FormFactor.Handheld, RailSelection.Hosts) { Apps(it) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Apps on a TV`() {
        show(FormFactor.Tv, RailSelection.Hosts) { Apps(it) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host on a phone`() {
        show(FormFactor.Phone, RailSelection.AddHost) { AddHost(it) }

        composeRule.assertAccessible(sameNameAsTheTitle)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Add host on a TV`() {
        show(FormFactor.Tv, RailSelection.AddHost) { AddHost(it) }

        composeRule.assertAccessible(NoTouchOnTv, sameNameAsTheTitle)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host with an error under the address`() {
        show(FormFactor.Phone, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("abc", error = AddHostError.INVALID))
        }

        composeRule.assertAccessible(sameNameAsTheTitle)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host while the host is added`() {
        show(FormFactor.Phone, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("192.168.1.40", busy = true))
        }

        // A disabled button is dimmed: WCAG 1.4.3 leaves an inactive component out of the contrast.
        composeRule.assertAccessible(
            sameNameAsTheTitle,
            AcceptedFinding(
                TextContrastCheck::class.java,
                "the buttons are disabled while the host is added, and WCAG 1.4.3 exempts an " +
                    "inactive component from the contrast",
                anyOf(showing("Add host"), showing("Cancel")),
            ),
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing on a phone`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, waiting) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Pairing on a TV`() {
        show(FormFactor.Tv, RailSelection.Hosts) { Pairing(it, waiting) }

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing while the PIN is made`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, PairingUiState.Preparing) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing with a PIN that expired`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, PairingUiState.Expired) }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing that failed`() {
        show(FormFactor.Phone, RailSelection.Hosts) {
            Pairing(it, PairingUiState.Failed(PairingFailure.PIN_WRONG, null))
        }

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing that succeeded`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, PairingUiState.Paired) }

        composeRule.assertAccessible()
    }

    private fun show(
        formFactor: FormFactor,
        selected: RailSelection,
        screen: @Composable (FormFactor) -> Unit,
    ) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                screen(formFactor)
            }
        }
        if (formFactor == FormFactor.Phone) composeRule.setContent(content)
        else composeRule.setContentInKeyboardMode(content)
        composeRule.waitForIdle()
    }

    /** Hosts with the notice that a Quit leaves, on screen for as long as the test. */
    @Composable
    private fun HostsWithSnackbar(formFactor: FormFactor) {
        val snackbars = remember { SnackbarHostState() }
        StaysAsSnackbar(snackbars, SNACKBAR)
        Hosts(formFactor, snackbars = snackbars)
    }

    @Composable
    private fun Hosts(
        formFactor: FormFactor,
        state: HostsUiState = withHosts,
        snackbars: SnackbarHostState = SnackbarHostState(),
    ) {
        HostsScreen(
            state = state,
            formFactor = formFactor,
            snackbarHostState = snackbars,
            onHostSelected = {},
            onContinue = {},
            onWake = {},
            onForget = {},
            onDetails = {},
            onDetailsDismissed = {},
            onAddHost = {},
            onSettings = {},
        )
    }

    @Composable
    private fun Apps(formFactor: FormFactor) {
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = "Desk PC",
                    runningAppId = 1,
                    runningAppName = "Desktop",
                    items =
                        names.mapIndexed { index, name ->
                            AppItemUiState(
                                id = index + 1,
                                name = name,
                                running = index == 0,
                                hidden = index == 3,
                                hdrSupported = index % 2 == 0,
                            )
                        },
                ),
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onAppSelected = {},
            onActions = {},
            onActionChosen = {},
            onActionsDismissed = {},
            onDetailsDismissed = {},
            onToggleShowHidden = {},
        )
    }

    @Composable
    private fun AddHost(formFactor: FormFactor, state: AddHostUiState = AddHostUiState()) {
        AddHostScreen(
            state,
            formFactor,
            onAddressChanged = {},
            onSubmit = {},
            onCancel = {},
        )
    }

    @Composable
    private fun Pairing(formFactor: FormFactor, state: PairingUiState) {
        PairingScreen(
            state,
            formFactor,
            onGetNewPin = {},
            onCancel = {},
            hostName = "Studio",
        )
    }

    private companion object {
        const val SNACKBAR = "App quit."
    }
}
