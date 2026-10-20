package io.github.magalz.borealis.library.addhost

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.setContentInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the Add host screen, named `<screen>_<device>_<state>.png`. */
@RunWith(AndroidJUnit4::class)
class AddHostScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun addHostPhoneDefault() {
        composeRule.setContent { Screen(AddHostUiState(), FormFactor.Phone) }

        shoot("addhost_phone_default")
    }

    @Test
    @Config(qualifiers = TV)
    fun addHostTvDefault() {
        composeRule.setContentInKeyboardMode { Screen(AddHostUiState(), FormFactor.Tv) }

        shoot("addhost_tv_default")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun addHostPhoneWrongSubnet() {
        composeRule.setContent {
            Screen(
                AddHostUiState(address = "10.0.5.20", error = AddHostError.WRONG_SUBNET),
                FormFactor.Phone,
            )
        }

        shoot("addhost_phone_wrong-subnet")
    }

    @Composable
    private fun Screen(state: AddHostUiState, formFactor: FormFactor) {
        // The shell draws the rail beside Add host on a TV and a handheld.
        LibraryScaffold(
            formFactor,
            actions = RailActions({}, {}, {}),
            selected = RailSelection.AddHost,
            backgroundStyle = BackgroundStyle.Off,
        ) {
            AddHostScreen(
                state = state,
                formFactor = formFactor,
                onAddressChanged = {},
                onSubmit = {},
                onCancel = {},
            )
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
