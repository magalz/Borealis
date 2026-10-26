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
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.checkingPc
import io.github.magalz.borealis.library.hosts.deskPc
import io.github.magalz.borealis.library.hosts.livingRoomPc
import io.github.magalz.borealis.library.hosts.studio
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Hosts screen under the accessibility settings of ADR 0057, named
 * `a11y_<device>_<state>.png`: high contrast over the dimmed background, a colour-blind palette
 * with every status, the largest text size and the thicker focus outline. The animated background
 * is held on its still frame.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AccessibilityScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    /** Every state of the status dot: online, needs attention, offline and not known. */
    private val everyStatus =
        HostsUiState(
            loading = false,
            searching = false,
            items = listOf(deskPc, studio, livingRoomPc, checkingPc),
        )

    @Before
    fun theBackgroundIsItsStill() {
        noAnimations()
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvHighContrast() {
        composeRule.setContentInKeyboardMode {
            Hosts(withHosts, FormFactor.Tv, BorealisLook(highContrast = true))
        }

        shoot("a11y_tv_high-contrast")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvPaletteRedGreen() {
        composeRule.setContentInKeyboardMode {
            Hosts(everyStatus, FormFactor.Tv, BorealisLook(colourVision = ColourVision.RedGreen))
        }

        shoot("a11y_tv_palette-red-green")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvEveryStatus() {
        composeRule.setContentInKeyboardMode { Hosts(everyStatus, FormFactor.Tv, BorealisLook()) }

        shoot("a11y_tv_every-status")
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = 1.3f)
    fun hostsPhoneLargestText() {
        composeRule.setContent {
            Hosts(withHosts, FormFactor.Phone, BorealisLook(textSize = TextSize.Largest))
        }

        shoot("a11y_phone_text-largest")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostsTvThickFocus() {
        composeRule.setContentInKeyboardMode {
            Hosts(withHosts, FormFactor.Tv, BorealisLook(thickFocus = true))
        }

        shoot("a11y_tv_thick-focus")
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

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
