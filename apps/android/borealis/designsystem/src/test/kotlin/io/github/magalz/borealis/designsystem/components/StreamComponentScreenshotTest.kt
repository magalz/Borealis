package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the components of the stream screens (Sprint 5), named like those of
 * [ComponentScreenshotTest]: `<component>_<device>_<state>.png` in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class StreamComponentScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun chipsPhone() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Win+D", onClick = {})
                    Chip("Alt+Tab", selected = true, onClick = {})
                    Chip("Ctrl+Alt+Del", onClick = {})
                }
            }
        }

        shoot("chips_phone")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun tagsPhone() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag("HDR")
                    Tag("HEVC")
                    Tag("Wake on LAN")
                }
            }
        }

        shoot("tags_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun segmentedTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                SegmentedControl(
                    options = listOf("30", "60", "120"),
                    selected = "60",
                    label = { "$it fps" },
                    onSelect = {},
                )
            }
        }

        // The selected segment is 60; focus the next one to show the outline on a plain segment.
        composeRule.onNodeWithText("120 fps").requestFocus()
        shoot("segmented_tv_focused")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun sidePanelPhone() {
        composeRule.setContent { Page(FormFactor.Phone, padded = false) { Stream { Menu() } } }

        shoot("sidepanel_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun sidePanelTv() {
        composeRule.setContent { Page(FormFactor.Tv, padded = false) { Stream { Menu() } } }

        shoot("sidepanel_tv")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun stepListPhoneProgress() {
        composeRule.setContent { Page(FormFactor.Phone) { StepList(LAUNCH_STEPS, current = 2) } }

        shoot("steplist_phone_progress")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun stepListPhoneFailed() {
        composeRule.setContent {
            Page(FormFactor.Phone) { StepList(LAUNCH_STEPS, current = 2, failedAt = 2) }
        }

        shoot("steplist_phone_failed")
    }

    @Test
    @Config(qualifiers = TV)
    fun keyboardKeysTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (letter in listOf("Q", "W", "E", "R", "T", "Y")) {
                            KeyboardKey(letter, onClick = {})
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyboardKey("Ctrl", onClick = {}, style = KeyStyle.Wide, latched = true)
                        KeyboardKey("Alt", onClick = {}, style = KeyStyle.Wide)
                        KeyboardKey("Enter", onClick = {}, style = KeyStyle.Wide)
                        KeyboardKey("Space", onClick = {}, style = KeyStyle.Space)
                    }
                }
            }
        }

        composeRule.onNodeWithText("W").requestFocus()
        shoot("keyboardkeys_tv_focused-latched")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuRowsTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                Column(
                    Modifier.width(360.dp)
                        .background(LocalBorealisColors.current.surface)
                        .padding(8.dp)
                ) {
                    MenuRows()
                }
            }
        }

        composeRule.onNodeWithText("Send keys").requestFocus()
        shoot("menurows_tv_focused")
    }

    @Composable
    private fun Menu() {
        SidePanel(onDismiss = {}) {
            Text(
                "Desk PC",
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(Modifier.padding(8.dp)) { MenuRows() }
        }
    }

    @Composable
    private fun MenuRows() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow("Back to stream", onClick = {})
            MenuRow("Send keys", onClick = {}, consequence = "Opens the keyboard")
            MenuRow(
                "Video quality",
                onClick = {},
                trailing = {
                    SegmentedControl(
                        options = listOf("Low", "High"),
                        selected = "High",
                        label = { it },
                        onSelect = {},
                    )
                },
            )
            MenuRow(
                "Mute microphone",
                onClick = {},
                enabled = false,
                note = "The host has no microphone",
            )
            MenuRow(
                "Quit",
                onClick = {},
                consequence = "Closes the app on the host and loses unsaved work",
                style = MenuRowStyle.Danger,
            )
        }
    }

    /** A stand-in for the picture of the stream, so the scrim of the panel can be seen over it. */
    @Composable
    private fun Stream(content: @Composable () -> Unit) {
        val colors = LocalBorealisColors.current
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.accentStrong, colors.page)))
        ) {
            content()
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
        const val PHONE = "w360dp-h640dp-xhdpi"
        // The stream is landscape: a phone in portrait would give the panel 187dp.
        const val PHONE_LANDSCAPE = "w640dp-h360dp-land-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        val LAUNCH_STEPS = listOf("Find the host", "Pair", "Start the app", "Open the stream")
    }
}
