package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Hosts screen; the references live in `src/test/screenshots` and are named
 * `<screen>_<device>_<state>.png`. The TV and handheld ones are taken in keyboard input mode, as a
 * D-pad user sees them, with the focus outline on the first card and the shell's rail beside them.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class HostsScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun hostsPhoneWithHosts() {
        composeRule.setContent { Screen(withHosts, FormFactor.Phone) }

        shoot("hosts_phone_with-hosts")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWithHosts() {
        composeRule.setContentInKeyboardMode { Screen(withHosts, FormFactor.Tv) }

        shoot("hosts_tv_with-hosts")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun hostsHandheldWithHosts() {
        composeRule.setContentInKeyboardMode { Screen(withHosts, FormFactor.Handheld) }

        shoot("hosts_handheld_with-hosts")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun hostsPhoneSearching() {
        composeRule.setContent { Screen(searching, FormFactor.Phone) }

        shoot("hosts_phone_searching")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvSearching() {
        composeRule.setContentInKeyboardMode { Screen(searching, FormFactor.Tv) }

        shoot("hosts_tv_searching")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvLoading() {
        // The loader fills for ever: freeze the clock, then move it to a time at which the bands
        // are partly drawn, so the capture is the same every run.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContentInKeyboardMode { Screen(loadingHosts, FormFactor.Tv) }
        composeRule.mainClock.advanceTimeBy(LOADER_TIME_MILLIS)

        shoot("hosts_tv_loading")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvOptions() {
        composeRule.setContentInKeyboardMode { Screen(withHosts, FormFactor.Tv) }
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()

        // The options are a dialog window, which the root capture does not include.
        captureScreenRoboImage("src/test/screenshots/hosts_tv_options.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvForget() {
        composeRule.setContentInKeyboardMode { Screen(withHosts, FormFactor.Tv) }
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.settleWindows()
        captureScreenRoboImage("src/test/screenshots/hosts_tv_forget.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvDetails() {
        composeRule.setContentInKeyboardMode {
            Screen(withHosts.copy(details = DETAILS), FormFactor.Tv)
        }
        composeRule.waitForIdle()

        composeRule.settleWindows()
        captureScreenRoboImage("src/test/screenshots/hosts_tv_details.png")
    }

    @Composable
    private fun Screen(state: HostsUiState, formFactor: FormFactor) {
        // The shell draws the rail beside Hosts on a TV and a handheld.
        LibraryScaffold(
            formFactor,
            selected = RailSelection.Hosts,
            actions = RailActions({}, {}, {}),
            backgroundStyle = BackgroundStyle.Off,
        ) {
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
    }

    private companion object {
        const val LOADER_TIME_MILLIS = 600L

        /**
         * What the repository gives for an online host that runs an app, as the dialog shows it.
         */
        val DETAILS =
            """
            Name: Desk PC
            Status: Online
            Address: 192.168.1.20
            Other addresses: 203.0.113.7, 2001:db8::20
            Paired: Yes
            MAC address (for wake): AA:BB:CC:DD:EE:20
            Running: Desktop

            Host capabilities
            Host software: Sunshine-compatible, version 7.1.431.-1
            Video: HEVC, HEVC 10-bit, AV1, 4:4:4
            PyroWave: SDR 4:2:0, HDR 4:2:0
            Ports: HTTP 47989, HTTPS 47984
            """
                .trimIndent()
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
