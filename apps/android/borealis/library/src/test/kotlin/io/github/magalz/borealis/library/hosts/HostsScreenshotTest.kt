package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.setContentInKeyboardMode
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
    fun hostsTvOptions() {
        composeRule.setContentInKeyboardMode { Screen(withHosts, FormFactor.Tv) }
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()

        // The options are a dialog window, which the root capture does not include.
        captureScreenRoboImage("src/test/screenshots/hosts_tv_options.png")
    }

    @Composable
    private fun Screen(state: HostsUiState, formFactor: FormFactor) {
        // The shell draws the rail beside Hosts on a TV and a handheld.
        LibraryScaffold(formFactor, showRail = true, actions = RailActions({}, {}, {})) {
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

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
