package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.setContentInKeyboardMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Hosts screen on a TV with the teal and the white accent (ADR 0058, decision
 * 5), named `accent_tv_hosts-<accent>.png`: the selected rail item, the buttons and the focus in
 * the accent, the background still violet, and, with white, the indicator bar on the rail item. The
 * animated background is held on its still frame.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AccentScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun theBackgroundIsItsStill() {
        noAnimations()
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvTeal() = hostsTv(AccentChoice.Teal, "accent_tv_hosts-teal")

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWhite() = hostsTv(AccentChoice.White, "accent_tv_hosts-white")

    private fun hostsTv(accent: AccentChoice, name: String) {
        composeRule.setContentInKeyboardMode {
            Hosts(withHosts, FormFactor.Tv, BorealisLook(accent = accent))
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Hosts(state: HostsUiState, formFactor: FormFactor, look: BorealisLook) {
        LibraryScaffold(
            formFactor,
            selected = RailSelection.Hosts,
            actions = RailActions({}, {}, {}),
            backgroundStyle = BackgroundStyle.AuroraCurtains,
            look = look,
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
}
