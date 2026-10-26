package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the dialogs and the sheets over the glass of the Library, named
 * `libraryglass_<device>_<state>.png`. The window of the screen is blurred under them, they draw no
 * scrim, and their panel is a tint of the surface. The background is the still of Northern lights.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class LibraryGlassScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun theBackgroundIsItsStill() {
        noAnimations()
    }

    @Test
    @Config(qualifiers = TV)
    fun tvForgetDialog() {
        composeRule.setContentInKeyboardMode { Hosts(FormFactor.Tv) }
        composeRule.onNodeWithTag("host:living").requestFocus()
        composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.settleWindows()
        captureScreenRoboImage("src/test/screenshots/libraryglass_tv_forget-dialog.png")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneOptionsSheet() {
        composeRule.setContent { Hosts(FormFactor.Phone) }
        composeRule.onNodeWithTag("host:living").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.settleWindows()
        captureScreenRoboImage("src/test/screenshots/libraryglass_phone_options-sheet.png")
    }

    @Composable
    private fun Hosts(formFactor: FormFactor) {
        LibraryScaffold(
            formFactor,
            selected = RailSelection.Hosts,
            actions = RailActions({}, {}, {}),
            backgroundStyle = BackgroundStyle.NorthernLights,
        ) {
            HostsScreen(
                state = withHosts,
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
