package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.components.BackdropIconHalo
import io.github.magalz.borealis.designsystem.glass.GlassHost
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The icons of the Library that stand straight on the background, outside any glass, are drawn with
 * the halo (issue #33): the arrow back of Add host, Pairing and Apps, the actions of the Hosts bar
 * and the toggle of the Apps bar. With no background there is none.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class LibraryBackdropIconTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the actions of the Hosts bar are on the background`() {
        show { Hosts() }

        assertHalo("Add host")
        assertHalo("Settings")
    }

    @Test
    fun `the arrow back of Add host is on the background`() {
        show { AddHost() }

        assertHalo("Back to Hosts")
    }

    @Test
    fun `the arrow back of Pairing is on the background`() {
        show { Pairing() }

        assertHalo("Cancel pairing")
    }

    @Test
    fun `the arrow back and the toggle of the Apps bar are on the background`() {
        show { Apps() }

        assertHalo("Back")
        assertHalo("Show hidden apps")
    }

    @Test
    fun `with no background the bars have no halo`() {
        show(BackgroundStyle.Off) { Hosts() }

        assertNoHalo("Add host")
        assertNoHalo("Settings")
    }

    // Helpers

    private fun show(
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) { GlassHost(style, content = content) }
        }
        composeRule.waitForIdle()
    }

    /** The icon with this description is a backdrop icon with the halo drawn. */
    private fun assertHalo(description: String) {
        composeRule
            .onNode(
                hasContentDescription(description) and
                    SemanticsMatcher.expectValue(BackdropIconHalo, true),
                useUnmergedTree = true,
            )
            .assertExists()
    }

    /** The icon with this description is a backdrop icon with no halo, as on the plain page. */
    private fun assertNoHalo(description: String) {
        composeRule
            .onNode(
                hasContentDescription(description) and
                    SemanticsMatcher.expectValue(BackdropIconHalo, false),
                useUnmergedTree = true,
            )
            .assertExists()
    }

    @Composable
    private fun Hosts() {
        HostsScreen(
            state = withHosts,
            formFactor = FormFactor.Phone,
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
    private fun AddHost() {
        AddHostScreen(
            state = AddHostUiState(),
            formFactor = FormFactor.Phone,
            onAddressChanged = {},
            onSubmit = {},
            onCancel = {},
        )
    }

    @Composable
    private fun Pairing() {
        PairingScreen(
            state =
                PairingUiState.Waiting("4817", "https://192.168.1.20:47990", "Galaxy S22", false),
            formFactor = FormFactor.Phone,
            onGetNewPin = {},
            onCancel = {},
            hostName = "Desk PC",
        )
    }

    @Composable
    private fun Apps() {
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = "Desk PC",
                    items = listOf(AppItemUiState(1, "Desktop", false, false, false)),
                ),
            formFactor = FormFactor.Phone,
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
}
