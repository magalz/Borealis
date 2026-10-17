package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsTwoPaneTest {
    @get:Rule val composeRule = createComposeRule()
    private var selected by mutableIntStateOf(0)

    @Test
    fun `the left list shows the groups and the pane the selected one`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Video").assertIsDisplayed()
        composeRule.onNodeWithText("Audio").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `moving focus down the left list selects the next group`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        composeRule.onNodeWithText("Stream").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selected).isEqualTo(1)
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `a tap on a group selects it`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Audio").performClick()

        assertThat(selected).isEqualTo(2)
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `Right from a group enters the pane`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        composeRule.onNodeWithText("Stream").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Resolution").assertIsFocused()
        assertThat(selected).isEqualTo(0)
    }

    @Test
    fun `Left from any row of the pane returns to the selected group`() {
        selected = 1
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("HDR").requestFocus()

        composeRule.onNodeWithText("HDR").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selected).isEqualTo(1)
    }

    @Test
    fun `a slider at its lowest value passes Left on to return to the group`() {
        selected = 2
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Vibration").requestFocus()

        composeRule.onNodeWithText("Vibration").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Audio").assertIsFocused()
    }

    @Composable
    private fun Specimen() {
        BorealisTheme(FormFactor.Tv) {
            SettingsTwoPane(
                groups = listOf("Stream", "Video", "Audio"),
                selectedIndex = selected,
                onSelect = { selected = it },
            ) { group ->
                when (group) {
                    0 -> {
                        SettingsValueRow("Resolution", "1280 x 720", {})
                        SettingsValueRow("Frame rate", "60 fps", {})
                    }
                    1 -> {
                        SettingsValueRow("Codec", "Automatic", {})
                        SettingsSwitchRow("HDR", false, {})
                    }
                    else -> {
                        SettingsValueRow("Channels", "Stereo", {})
                        SettingsSliderRow("Vibration", 0f, 0f..200f, 10f, "0 %", {})
                    }
                }
            }
        }
    }
}
