package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.OverMarker
import io.github.magalz.borealis.designsystem.testing.assertMarkerShows
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
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shell draws the background behind every Library screen, so a screen must not paint a page of
 * its own over it: each is shown over a marker colour that its margin has to leave visible.
 */
@RunWith(AndroidJUnit4::class)
class ScreensOverBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts leaves the background to the shell on a phone`() = assertLeaves {
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

    @Test
    @Config(qualifiers = TV)
    fun `Hosts leaves the background to the shell on a TV`() = assertLeaves {
        HostsScreen(
            state = withHosts,
            formFactor = FormFactor.Tv,
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

    @Test
    @Config(qualifiers = PHONE)
    fun `Apps leaves the background to the shell`() = assertLeaves {
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

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host leaves the background to the shell`() = assertLeaves {
        AddHostScreen(
            state = AddHostUiState(),
            formFactor = FormFactor.Phone,
            onAddressChanged = {},
            onSubmit = {},
            onCancel = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing leaves the background to the shell`() = assertLeaves {
        PairingScreen(
            state = PairingUiState.Paired,
            formFactor = FormFactor.Phone,
            onGetNewPin = {},
            onCancel = {},
            hostName = "Studio",
        )
    }

    private fun assertLeaves(screen: @Composable () -> Unit) {
        composeRule.setContent { OverMarker { screen() } }
        composeRule.waitForIdle()

        composeRule.assertMarkerShows()
    }
}
