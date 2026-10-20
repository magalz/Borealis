package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.noAnimations
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the settings screen on glass, over the still of Northern lights (which is the
 * style whose glass has the highest floor), named `settingsglass_<device>_<state>.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsGlassScreenshotTest : SettingsScreenTestBase() {
    @Before
    fun offerWhatAPhoneOffers() {
        noAnimations()
        options.resolutions =
            STANDARD_RESOLUTIONS + ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        options.frameRates = options.frameRates + FrameRateChoice(90, isNative = false)
    }

    private fun showOnGlass(formFactor: FormFactor, keyboard: Boolean = false) {
        show(keyboard = keyboard) { content ->
            InGlassHost(formFactor, BorealisLook(), BackgroundStyle.NorthernLights, content)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneTwoSectionsOpen() {
        showOnGlass(FormFactor.Phone)
        openSection("STREAM")
        openSection("INPUT")

        shoot("settingsglass_phone_two-open")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phonePrivacyRow() {
        showOnGlass(FormFactor.Phone)
        composeRule.onNodeWithText("Privacy and diagnostics").performScrollTo()

        shoot("settingsglass_phone_privacy-row")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvStreamFocused() {
        asTelevision()
        showOnGlass(FormFactor.Tv, keyboard = true)

        shoot("settingsglass_tv_stream-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvChoiceSheet() {
        asTelevision()
        showOnGlass(FormFactor.Tv, keyboard = true)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        // The sheet is a dialog window over the blurred window of the screen, with no scrim.
        captureScreenRoboImage("src/test/screenshots/settingsglass_tv_choice-sheet.png")
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
