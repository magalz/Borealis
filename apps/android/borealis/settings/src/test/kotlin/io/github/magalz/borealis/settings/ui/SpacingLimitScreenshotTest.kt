package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the dialog that Settings opens when the user chooses a Text spacing that the font
 * scale draws as a smaller level (ADR 0058, decision 4), named
 * `settingsspacinglimit_<device>_<state>.png`: Wider drawn as Wide on a TV and on a phone at a font
 * scale of 1.6, and Wider drawn as Normal on a phone at 2.0, the largest that Android offers.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SpacingLimitScreenshotTest : SettingsScreenTestBase() {
    @Before fun holdTheBackgroundStill() = noAnimations()

    @Test
    @Config(qualifiers = TV, fontScale = 1.6f)
    fun tvWiderDrawnAsWide() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.ACCESSIBILITY) { content ->
            InGlassHost(FormFactor.Tv, BorealisLook(), BackgroundStyle.AuroraCurtains, content)
        }

        chooseWiderAndShoot("settingsspacinglimit_tv_dialog")
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = 1.6f)
    fun phoneWiderDrawnAsWide() {
        show { content ->
            InGlassHost(FormFactor.Phone, BorealisLook(), BackgroundStyle.AuroraCurtains, content)
        }
        composeRule.onAllNodesWithText("ACCESSIBILITY")[0].performScrollTo().performClick()
        composeRule.waitForIdle()

        chooseWiderAndShoot("settingsspacinglimit_phone_dialog")
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = 2f)
    fun phoneWiderDrawnAsNormal() {
        show { content ->
            InGlassHost(FormFactor.Phone, BorealisLook(), BackgroundStyle.AuroraCurtains, content)
        }
        composeRule.onAllNodesWithText("ACCESSIBILITY")[0].performScrollTo().performClick()
        composeRule.waitForIdle()

        chooseWiderAndShoot("settingsspacinglimit_phone_dialog-normal")
    }

    /** Chooses Wider in the list of Text spacing, and takes the screen with the dialog on it. */
    private fun chooseWiderAndShoot(name: String) {
        row("Text spacing").performClick()
        composeRule.waitForIdle()
        sheetOption("Wider").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.settleWindows()

        composeRule.onNodeWithText("Wider spacing is limited").assertIsDisplayed()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
