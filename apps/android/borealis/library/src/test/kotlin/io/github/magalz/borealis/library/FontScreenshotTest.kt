package io.github.magalz.borealis.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screenshots of Hosts on a TV and of the Pairing PIN on a phone in each font of the Font setting
 * except the default one, which the other references show (ADR 0058, decision 2). The references
 * live in `src/test/screenshots`, named `hosts_tv_font-<font>.png` and
 * `pairing_phone_font-<font>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@OptIn(ExperimentalRoborazziApi::class)
class FontScreenshotTest(private val font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(font = font)

    @Test
    @Config(qualifiers = TV)
    fun hostsTv() {
        composeRule.setContentInKeyboardMode {
            LibraryScaffold(
                FormFactor.Tv,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                look = look,
                backgroundStyle = BackgroundStyle.Off,
            ) {
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
        }

        shoot("hosts_tv_font-${font.id}")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun pairingPhone() {
        composeRule.setContent {
            LibraryScaffold(
                FormFactor.Phone,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                look = look,
                backgroundStyle = BackgroundStyle.Off,
            ) {
                PairingScreen(
                    state =
                        PairingUiState.Waiting(
                            "4817",
                            "https://192.0.2.10:47990",
                            "Galaxy S22",
                            false,
                        ),
                    formFactor = FormFactor.Phone,
                    onGetNewPin = {},
                    onCancel = {},
                    hostName = "Studio",
                )
            }
        }

        shoot("pairing_phone_font-${font.id}")
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    companion object {
        /** The font in a file name: `atkinson-next`, `b612`. */
        private val FontChoice.id: String
            get() = name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

        /** Every font but the default one: IBM Plex is what the other references show. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries - FontChoice.Plex
    }
}
