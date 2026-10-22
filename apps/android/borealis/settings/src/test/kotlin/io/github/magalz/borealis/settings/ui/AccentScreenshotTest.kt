package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the settings screen on a TV with the teal and the white accent (ADR 0058, decision
 * 5), named `accent_tv_settings-<accent>.png`: the selected group, the focus and the values in the
 * accent, the background still violet, and, with white, the indicator bar on the selected group and
 * on the selected segment. The animated background is held on its still frame.
 *
 * The accents that have a variant under a colour-blind palette (violet under Red-green and
 * Blue-yellow, white under Blue-yellow) are named `accent_tv_settings-<accent>-<palette>.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AccentScreenshotTest : SettingsScreenTestBase() {
    @Before
    fun theBackgroundIsItsStill() {
        noAnimations()
    }

    @Test
    @Config(qualifiers = TV)
    fun tvTeal() = settingsTv(AccentChoice.Teal, "accent_tv_settings-teal")

    @Test
    @Config(qualifiers = TV)
    fun tvWhite() = settingsTv(AccentChoice.White, "accent_tv_settings-white")

    @Test
    @Config(qualifiers = TV)
    fun tvVioletRedGreen() =
        settingsTv(
            AccentChoice.Violet,
            "accent_tv_settings-violet-red-green",
            ColourVision.RedGreen,
        )

    @Test
    @Config(qualifiers = TV)
    fun tvVioletBlueYellow() =
        settingsTv(
            AccentChoice.Violet,
            "accent_tv_settings-violet-blue-yellow",
            ColourVision.BlueYellow,
        )

    @Test
    @Config(qualifiers = TV)
    fun tvWhiteBlueYellow() =
        settingsTv(
            AccentChoice.White,
            "accent_tv_settings-white-blue-yellow",
            ColourVision.BlueYellow,
        )

    private fun settingsTv(
        accent: AccentChoice,
        name: String,
        colourVision: ColourVision = ColourVision.Off,
    ) {
        asTelevision()
        show(keyboard = true) { content ->
            InGlassHost(
                FormFactor.Tv,
                BorealisLook(accent = accent, colourVision = colourVision),
                BackgroundStyle.AuroraCurtains,
                content,
            )
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
