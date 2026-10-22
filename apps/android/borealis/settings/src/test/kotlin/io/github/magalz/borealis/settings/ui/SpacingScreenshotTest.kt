package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of Accessibility on a phone at Wide and Wider, in IBM Plex (ADR 0058, decision 4),
 * with the Text spacing row on the screen, showing the level that is chosen. The references live in
 * `src/test/screenshots`, named `settingsscreen_phone_spacing-wide.png` and
 * `settingsscreen_phone_spacing-wider.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
@Config(qualifiers = PHONE)
class SpacingScreenshotTest : SettingsScreenTestBase() {
    /** The page with the look that the Library gives the screens. */
    private fun withLook(spacing: TextSpacing): @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalBorealisLook provides BorealisLook(textSpacing = spacing)) {
            OnPage(it)
        }
    }

    @Test
    fun phoneAccessibilityWide() =
        accessibility(TextSpacing.Wide, Settings.TEXT_SPACING_WIDE, "wide")

    @Test
    fun phoneAccessibilityWider() =
        accessibility(TextSpacing.Wider, Settings.TEXT_SPACING_WIDER, "wider")

    private fun accessibility(spacing: TextSpacing, stored: String, name: String) {
        stored(Settings.TEXT_SPACING to stored)
        show(backdrop = withLook(spacing))
        composeRule.onNodeWithText("ACCESSIBILITY").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Text spacing").performScrollTo()
        composeRule.waitForIdle()

        composeRule
            .onRoot()
            .captureRoboImage("src/test/screenshots/settingsscreen_phone_spacing-$name.png")
    }
}
