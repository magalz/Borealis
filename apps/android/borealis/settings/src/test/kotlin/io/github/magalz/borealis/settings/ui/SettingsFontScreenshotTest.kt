package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.settings.appearance.LookLists
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screenshots of Appearance on a phone and of the font picker on a TV in each font of the Font
 * setting except the default one, which the other references show (ADR 0058, decision 2). The
 * references live in `src/test/screenshots`, named `settingsscreen_phone_font-<font>.png` and
 * `settingsscreen_tv_font-picker-<font>.png`. The picker shows every name in its own font.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsFontScreenshotTest(private val font: FontChoice) : SettingsScreenTestBase() {
    @Before
    fun theFontIsChosen() {
        stored(Settings.FONT to LookLists.FONT.choices.first { it.value == font }.id)
    }

    /** The page with the look that the Library gives the screens. */
    private val withLook: @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalBorealisLook provides BorealisLook(font = font)) {
            OnPage(it)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneAppearance() {
        show(backdrop = withLook)
        composeRule.onNodeWithText("APPEARANCE").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The rows from Font down to Card density are on the screen.
        composeRule.onNodeWithText("Card density").performScrollTo()
        composeRule.waitForIdle()

        composeRule
            .onRoot()
            .captureRoboImage("src/test/screenshots/settingsscreen_phone_font-$id.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun tvFontPicker() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.APPEARANCE, backdrop = withLook)
        composeRule.onNodeWithText("Font").performClick()
        composeRule.waitForIdle()
        composeRule.settleWindows()

        captureScreenRoboImage("src/test/screenshots/settingsscreen_tv_font-picker-$id.png")
    }

    private val id: String
        get() = font.name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

    companion object {
        /** Every font but the default one: IBM Plex is what the other references show. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries - FontChoice.Plex
    }
}
