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
 * Screenshots of the dialog that Settings opens when the user chooses Wider at the largest font
 * scale that Android offers, 2.0, where Wider is drawn as Wide (ADR 0058, decision 4), named
 * `settingsspacinglimit_<device>_dialog.png`: on a TV and on a phone.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SpacingLimitScreenshotTest : SettingsScreenTestBase() {
    @Before fun holdTheBackgroundStill() = noAnimations()

    @Test
    @Config(qualifiers = TV, fontScale = 2f)
    fun tvWiderDrawnAsWide() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.ACCESSIBILITY) { content ->
            InGlassHost(FormFactor.Tv, BorealisLook(), BackgroundStyle.AuroraCurtains, content)
        }

        chooseWiderAndShoot("settingsspacinglimit_tv_dialog")
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = 2f)
    fun phoneWiderDrawnAsWide() {
        show { content ->
            InGlassHost(FormFactor.Phone, BorealisLook(), BackgroundStyle.AuroraCurtains, content)
        }
        composeRule.onAllNodesWithText("ACCESSIBILITY")[0].performScrollTo().performClick()
        composeRule.waitForIdle()

        chooseWiderAndShoot("settingsspacinglimit_phone_dialog")
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
