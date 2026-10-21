package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the settings screen, named `settingsscreen_<device>_<state>.png`. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsScreenshotTest : SettingsScreenTestBase() {
    @Before
    fun offerWhatAPhoneOffers() {
        options.resolutions =
            STANDARD_RESOLUTIONS + ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        options.frameRates = options.frameRates + FrameRateChoice(90, isNative = false)
        options.rowStates[Settings.ANALOG_SCROLLING] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        options.rowStates[Settings.GAMEPAD_MOUSE_SPEED] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        options.rowStates[Settings.VIBRATE_FALLBACK_STRENGTH] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.VIBRATE_FALLBACK)
        stored(
            Settings.RESOLUTION to "2400x1080",
            Settings.MOUSE_EMULATION to false,
            Settings.ENABLE_HDR to true,
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneTop() {
        show()

        shoot("settingsscreen_phone_top")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneTwoSectionsOpen() {
        show()
        openSection("STREAM")
        openSection("INPUT")

        shoot("settingsscreen_phone_two-open")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneScrolled() {
        show()
        openSection("VIDEO")
        openSection("INPUT")
        openSection("GAMEPAD")
        composeRule.onNodeWithText("Vibration strength").performScrollTo()

        shoot("settingsscreen_phone_scrolled")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvStreamFocused() {
        asTelevision()
        show(keyboard = true)

        shoot("settingsscreen_tv_stream-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvInputWithDisabledRow() {
        asTelevision()
        show(keyboard = true)
        composeRule.onNodeWithText("Input").performClick()
        composeRule.onNodeWithText("Analog scrolling").requestFocus()

        shoot("settingsscreen_tv_input-disabled")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneAppearance() {
        show()
        openSection("APPEARANCE")
        composeRule.onNodeWithText("Glass blur").performScrollTo()

        shoot("settingsscreen_phone_appearance")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvAppearanceFocused() {
        asTelevision()
        show(keyboard = true)
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("Background").requestFocus()

        shoot("settingsscreen_tv_appearance-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvChoiceSheet() {
        asTelevision()
        show(keyboard = true)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/settingsscreen_tv_choice-sheet.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvLinkDialog() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)
        composeRule.onNodeWithText("Source code").performClick()
        composeRule.waitForIdle()
        composeRule.settleWindows()

        captureScreenRoboImage("src/test/screenshots/settingsscreen_tv_link-dialog.png")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun handheldStream() {
        show(keyboard = true)

        shoot("settingsscreen_handheld_stream")
    }

    /** Opens the section of a phone list: its header shows the title in capitals. */
    private fun openSection(title: String) {
        composeRule.onNodeWithText(title).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
