package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the components of the settings screens (Sprint 6), named like those of
 * [io.github.magalz.borealis.designsystem.components.ComponentScreenshotTest]:
 * `settings_<component>_<device>_<state>.png` in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsComponentScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun rowsPhone() {
        composeRule.setContent { Page(FormFactor.Phone) { PhoneList() } }

        shoot("settings_rows_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun rowsTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) { Rows(Modifier.width(560.dp)) }
        }

        composeRule.onNodeWithText("Frame rate").requestFocus()
        shoot("settings_rows_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun rowsTvDisabledFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) { Rows(Modifier.width(560.dp)) }
        }

        composeRule.onNodeWithText("HDR").requestFocus()
        shoot("settings_rows_tv_disabled-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun rowsTvDangerFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) { Rows(Modifier.width(560.dp)) }
        }

        composeRule.onNodeWithText("Erase audit trail").requestFocus()
        shoot("settings_rows_tv_danger-focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun sliderTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) { Rows(Modifier.width(560.dp)) }
        }

        composeRule.onNodeWithText("Deadzone").requestFocus()
        shoot("settings_slider_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun sliderTvDisabledFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) { Rows(Modifier.width(560.dp)) }
        }

        composeRule.onNodeWithText("Vibration strength").requestFocus()
        shoot("settings_slider_tv_disabled-focused")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun choiceSheetPhone() {
        composeRule.setContent { Page(FormFactor.Phone) { Sheet() } }
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/settings_choicesheet_phone.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun choiceSheetTv() {
        composeRule.setContent { Page(FormFactor.Tv) { Sheet() } }
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/settings_choicesheet_tv.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun twoPaneTvFocused() {
        composeRule.setContentInKeyboardMode { Page(FormFactor.Tv, padded = false) { TwoPane() } }

        composeRule.onNodeWithText("Stream").requestFocus()
        shoot("settings_twopane_tv_focused")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun twoPaneHandheld() {
        composeRule.setContent { Page(FormFactor.Handheld, padded = false) { TwoPane() } }

        shoot("settings_twopane_handheld")
    }

    @Composable
    private fun PhoneList() {
        Column(Modifier.padding(horizontal = 8.dp)) { Rows(Modifier.fillMaxWidth()) }
    }

    @Composable
    private fun Rows(modifier: Modifier) {
        Column(
            modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsValueRow("Frame rate", "60 fps", {})
            SettingsValueRow("Source code", null, {})
            SettingsSwitchRow("Full range", true, {}, summary = "Uses the full 0 to 255 range")
            SettingsSwitchRow(
                "HDR",
                false,
                {},
                enabled = false,
                disabledNote = "This display does not support HDR",
            )
            SettingsSliderRow(
                "Deadzone",
                7f,
                0f..20f,
                1f,
                "7 %",
                {},
                summary = "How far the stick moves before it counts",
            )
            SettingsSliderRow(
                "Vibration strength",
                100f,
                0f..200f,
                10f,
                "100 %",
                {},
                enabled = false,
                disabledNote = "This device has no vibration motor",
            )
            SettingsValueRow("Erase audit trail", null, {}, tone = SettingsRowTone.Danger)
        }
    }

    @Composable
    private fun Sheet() {
        ChoiceSheet(
            title = "Frame pacing",
            options =
                listOf(
                    ChoiceOption(
                        "latency",
                        "Lowest latency",
                        "Shows frames as soon as they arrive",
                    ),
                    ChoiceOption(
                        "smooth",
                        "Smoothest video",
                        "Buffers a frame to even out the pace",
                    ),
                    ChoiceOption("balanced", "Balanced"),
                ),
            selected = "latency",
            onSelect = {},
            onDismiss = {},
        )
    }

    @Composable
    private fun TwoPane() {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            SettingsTwoPane(
                groups =
                    listOf("Stream", "Video", "Input", "Audio", "About").map {
                        SettingsGroup(it, it)
                    },
                selected = { "Stream" },
                onSelect = {},
                modifier = Modifier.padding(top = 12.dp),
            ) {
                SettingsValueRow("Resolution", "2560 x 1440 (native)", {})
                SettingsValueRow("Frame rate", "60 fps", {})
                SettingsValueRow("Bitrate", "40 Mbps (automatic)", {})
                SettingsValueRow("Frame pacing", "Lowest latency", {})
            }
        }
    }

    @Composable
    private fun Page(
        formFactor: FormFactor,
        padded: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor) {
            val gutter = if (formFactor == FormFactor.Phone) 16.dp else 34.dp
            Box(
                Modifier.fillMaxSize()
                    .background(BorealisTokens.Color.Page)
                    .padding(if (padded) gutter else 0.dp)
            ) {
                content()
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val HANDHELD = "w640dp-h360dp-land-xhdpi"
        const val PHONE = "w360dp-h900dp-xhdpi"
        const val TV = "w960dp-h640dp-land-television-xhdpi"
    }
}
