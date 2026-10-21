package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo
import io.github.magalz.borealis.designsystem.glass.GlassHost
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.searching
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.pairing.PairingFailure
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The texts of the Library that stand straight on the background, outside any glass, are drawn with
 * the halo (issue #33): the titles, the labels of the sections, the count, the hints and the
 * loading, searching and empty states. The texts on a glass surface are not.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBackdropTextTest {
    @get:Rule val composeRule = createComposeRule()

    // Hosts

    @Test
    @Config(qualifiers = TV)
    fun `the header of Hosts on a TV is on the background`() {
        show(FormFactor.Tv) { Hosts(FormFactor.Tv, withHosts) }

        assertHalo("Hosts")
        assertHalo("3 hosts")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the labels of the sections of Hosts are on the background`() {
        show(FormFactor.Tv) { Hosts(FormFactor.Tv, withHosts) }

        assertHalo("CONTINUE")
        assertHalo("HOSTS")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the cards of Hosts are glass and have no halo`() {
        show(FormFactor.Tv) { Hosts(FormFactor.Tv, withHosts) }

        assertNoHalo("Studio")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title of Hosts on a phone is on the background`() {
        show(FormFactor.Phone) { Hosts(FormFactor.Phone, withHosts) }

        assertHalo("Hosts")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the searching state of Hosts is on the background`() {
        show(FormFactor.Tv) { Hosts(FormFactor.Tv, searching) }

        assertHalo("Looking for hosts")
        assertHalo("Borealis searches the local network", substring = true)
    }

    // Apps

    @Test
    @Config(qualifiers = PHONE)
    fun `the title of Apps on a phone is on the background`() {
        show(FormFactor.Phone) { Apps(FormFactor.Phone) }

        assertHalo("Desk PC")
        assertHalo("Nothing running")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the header of Apps on a TV is on the background`() {
        show(FormFactor.Tv) { Apps(FormFactor.Tv) }

        assertHalo("Desk PC")
        assertHalo("Nothing running")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the empty state of Apps is on the background`() {
        show(FormFactor.Phone) { Apps(FormFactor.Phone, items = emptyList()) }

        assertHalo("No apps to show")
        assertHalo("Add apps in the LuminalShine Web UI", substring = true)
    }

    // Add host

    @Test
    @Config(qualifiers = PHONE)
    fun `the title and the hints of Add host are on the background`() {
        show(FormFactor.Phone) { AddHost(FormFactor.Phone) }

        assertHalo("Add host")
        assertHalo("IP address or host name", substring = true)
    }

    // Pairing

    @Test
    @Config(qualifiers = PHONE)
    fun `the title of Pairing is on the background`() {
        show(FormFactor.Phone) { Pairing(FormFactor.Phone, waiting) }

        assertHalo("Pair with Desk PC")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the hint and the steps of Pairing are on the background`() {
        show(FormFactor.Tv) { Pairing(FormFactor.Tv, waiting) }

        assertHalo("The PIN stays valid for 5 minutes.")
        assertHalo("Go to the Clients page.")
        assertHalo("https://192.168.1.20:47990")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the message of a failed pairing is on the background`() {
        show(FormFactor.Phone) {
            Pairing(FormFactor.Phone, PairingUiState.Failed(PairingFailure.PIN_WRONG, null))
        }

        assertHalo("The PIN was wrong.")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Paired is on the background`() {
        show(FormFactor.Phone) { Pairing(FormFactor.Phone, PairingUiState.Paired) }

        assertHalo("Paired")
    }

    // The plain page

    @Test
    @Config(qualifiers = TV)
    fun `with no background Hosts has no halo`() {
        show(FormFactor.Tv, BackgroundStyle.Off) { Hosts(FormFactor.Tv, withHosts) }

        assertNoHalo("Hosts", drawn = true)
        assertNoHalo("CONTINUE", drawn = true)
    }

    // Helpers

    private val waiting =
        PairingUiState.Waiting("4817", "https://192.168.1.20:47990", "Galaxy S22", false)

    private fun show(
        formFactor: FormFactor,
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { BorealisTheme(formFactor) { GlassHost(style, content = content) } }
        composeRule.waitForIdle()
    }

    /** The text is a backdrop text with the halo drawn. */
    private fun assertHalo(text: String, substring: Boolean = false) {
        composeRule
            .onNode(
                hasText(text, substring) and SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertExists()
    }

    /** The text is no backdrop text, or [drawn] says that it is one with no halo. */
    private fun assertNoHalo(text: String, drawn: Boolean = false) {
        val rule =
            if (drawn) SemanticsMatcher.expectValue(BackdropTextHalo, false)
            else SemanticsMatcher.keyNotDefined(BackdropTextHalo)
        composeRule.onNode(hasText(text) and rule, useUnmergedTree = true).assertExists()
    }

    @Composable
    private fun Hosts(formFactor: FormFactor, state: HostsUiState) {
        HostsScreen(
            state = state,
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
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
    private fun Apps(
        formFactor: FormFactor,
        items: List<AppItemUiState> = listOf(AppItemUiState(1, "Desktop", false, false, false)),
    ) {
        AppsScreen(
            state = AppsUiState(loading = false, hostName = "Desk PC", items = items),
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
    private fun AddHost(formFactor: FormFactor) {
        AddHostScreen(
            state = AddHostUiState(),
            formFactor = formFactor,
            onAddressChanged = {},
            onSubmit = {},
            onCancel = {},
        )
    }

    @Composable
    private fun Pairing(formFactor: FormFactor, state: PairingUiState) {
        PairingScreen(
            state = state,
            formFactor = formFactor,
            onGetNewPin = {},
            onCancel = {},
            hostName = "Desk PC",
        )
    }
}
