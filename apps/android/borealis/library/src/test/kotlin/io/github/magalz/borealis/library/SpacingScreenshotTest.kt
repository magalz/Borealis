package io.github.magalz.borealis.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of Hosts on a TV at each Text spacing (ADR 0058, decision 4): Wide and Wider in IBM
 * Plex, and Wider in Andika, the font that is widest at the same x-height and the widest case. The
 * references live in `src/test/screenshots`, named `hosts_tv_spacing-wide.png`,
 * `hosts_tv_spacing-wider.png` and `hosts_tv_spacing-wider-andika.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SpacingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWide() =
        hostsTv(BorealisLook(textSpacing = TextSpacing.Wide), "hosts_tv_spacing-wide")

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWider() =
        hostsTv(BorealisLook(textSpacing = TextSpacing.Wider), "hosts_tv_spacing-wider")

    @Test
    @Config(qualifiers = TV)
    fun hostsTvWiderAndika() =
        hostsTv(
            BorealisLook(font = FontChoice.Andika, textSpacing = TextSpacing.Wider),
            "hosts_tv_spacing-wider-andika",
        )

    private fun hostsTv(look: BorealisLook, name: String) {
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

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
