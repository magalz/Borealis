package io.github.magalz.borealis.library.addhost

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Add host screen, named `<screen>_<device>_<state>.png`. `addhostglass_` is the
 * screen over an animated background, with the glass field and the halo of the arrow and the texts.
 */
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

    @Test
    @Config(qualifiers = PHONE)
    fun addHostPhoneOverTheBackground() {
        noAnimations()
        composeRule.setContent {
            Screen(AddHostUiState(address = "10.0.5.20"), FormFactor.Phone, STYLE)
        }

        shoot("addhostglass_phone_aurora-curtains")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun addHostPhoneWrongSubnetOverTheBackground() {
        noAnimations()
        composeRule.setContent {
            Screen(
                AddHostUiState(address = "10.0.5.20", error = AddHostError.WRONG_SUBNET),
                FormFactor.Phone,
                STYLE,
            )
        }

        shoot("addhostglass_phone_wrong-subnet")
    }

    @Test
    @Config(qualifiers = TV)
    fun addHostTvOverTheBackground() {
        noAnimations()
        composeRule.setContentInKeyboardMode {
            Screen(AddHostUiState(address = "10.0.5.20"), FormFactor.Tv, STYLE)
        }

        shoot("addhostglass_tv_aurora-curtains")
    }

    @Composable
    private fun Screen(
        state: AddHostUiState,
        formFactor: FormFactor,
        style: BackgroundStyle = BackgroundStyle.Off,
    ) {
        // The shell draws the rail beside Add host on a TV and a handheld.
        LibraryScaffold(
            formFactor,
            actions = RailActions({}, {}, {}),
            selected = RailSelection.AddHost,
            backgroundStyle = style,
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

    private companion object {
        val STYLE = BackgroundStyle.AuroraCurtains
    }
}
