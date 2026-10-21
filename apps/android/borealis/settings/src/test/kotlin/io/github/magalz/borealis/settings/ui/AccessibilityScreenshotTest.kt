package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the settings screen under the accessibility settings of ADR 0057, named
 * `settingsa11y_<device>_<state>.png`: high contrast on a phone, over the dimmed background, and
 * the largest text size on a TV. The animated background is held on its still frame.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AccessibilityScreenshotTest : SettingsScreenTestBase() {
    @Before
    fun offerWhatAPhoneOffers() {
        noAnimations()
        options.resolutions =
            STANDARD_RESOLUTIONS + ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        options.frameRates = options.frameRates + FrameRateChoice(90, isNative = false)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneHighContrast() {
        show { content ->
            InGlassHost(
                FormFactor.Phone,
                BorealisLook(highContrast = true),
                BackgroundStyle.AuroraCurtains,
                content,
            )
        }
        openSection("STREAM")

        shoot("settingsa11y_phone_high-contrast")
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.3f)
    fun tvLargestText() {
        asTelevision()
        show(keyboard = true) { content ->
            InGlassHost(
                FormFactor.Tv,
                BorealisLook(textSize = TextSize.Largest),
                BackgroundStyle.AuroraCurtains,
                content,
            )
        }

        shoot("settingsa11y_tv_text-largest")
    }

    /** Opens the section of a phone list: its header shows the title in capitals. */
    private fun openSection(title: String) {
        composeRule.onAllNodesWithText(title)[0].performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
