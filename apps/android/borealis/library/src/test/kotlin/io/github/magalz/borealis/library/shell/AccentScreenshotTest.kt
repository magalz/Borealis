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
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState
import io.github.magalz.borealis.library.hosts.checkingPc
import io.github.magalz.borealis.library.hosts.deskPc
import io.github.magalz.borealis.library.hosts.livingRoomPc
import io.github.magalz.borealis.library.hosts.studio
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.TV
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
 *
 * The accents that have a variant under a colour-blind palette are named
 * `accent_tv_hosts-<accent>-<palette>.png`, with a host of every status, so that the status colours
 * of the palette are on the screen beside the accent that the palette gives: violet under
 * Blue-yellow and white under Blue-yellow. Violet under Red-green is the screenshot of the palette
 * in `AccessibilityScreenshotTest` (`a11y_tv_palette-red-green.png`), the same screen.
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

    @Test
    @Config(qualifiers = TV)
    fun hostsTvVioletBlueYellow() =
        hostsTv(
            AccentChoice.Violet,
            "accent_tv_hosts-violet-blue-yellow",
            ColourVision.BlueYellow,
            everyStatus,
        )

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWhiteBlueYellow() =
        hostsTv(
            AccentChoice.White,
            "accent_tv_hosts-white-blue-yellow",
            ColourVision.BlueYellow,
            everyStatus,
        )

    /** Every state of the status dot: online, needs attention, offline and not known. */
    private val everyStatus =
        HostsUiState(
            loading = false,
            searching = false,
            items = listOf(deskPc, studio, livingRoomPc, checkingPc),
        )

    private fun hostsTv(
        accent: AccentChoice,
        name: String,
        colourVision: ColourVision = ColourVision.Off,
        state: HostsUiState = withHosts,
    ) {
        composeRule.setContentInKeyboardMode {
            Hosts(state, FormFactor.Tv, BorealisLook(accent = accent, colourVision = colourVision))
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
