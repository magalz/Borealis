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
    fun phoneScrolled() {
        show()
        composeRule.onNodeWithText("Vibration strength").performScrollTo()

        shoot("settingsscreen_phone_scrolled")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvStreamFocused() {
        show(keyboard = true)

        shoot("settingsscreen_tv_stream-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvInputWithDisabledRow() {
        show(keyboard = true)
        composeRule.onNodeWithText("Input").performClick()
        composeRule.onNodeWithText("Analog scrolling").requestFocus()

        shoot("settingsscreen_tv_input-disabled")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvChoiceSheet() {
        show(keyboard = true)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/settingsscreen_tv_choice-sheet.png")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun handheldStream() {
        show(keyboard = true)

        shoot("settingsscreen_handheld_stream")
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
