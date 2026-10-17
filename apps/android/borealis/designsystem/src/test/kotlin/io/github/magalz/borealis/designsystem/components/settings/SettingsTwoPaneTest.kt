package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    private var selected by mutableStateOf("stream")
    private val selections = mutableListOf<String>()

    private val allGroups =
        listOf(
            SettingsGroup("stream", "Stream"),
            SettingsGroup("video", "Video"),
            SettingsGroup("audio", "Audio"),
            SettingsGroup("about", "About"),
        )

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
    fun `moving focus down the left list selects the next group by its key`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        composeRule.onNodeWithText("Stream").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `a tap on a group selects it`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Audio").performClick()

        assertThat(selected).isEqualTo("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `the selection is a key, so a hidden group does not shift it`() {
        selected = "audio"
        composeRule.setContent { Specimen(allGroups.filter { it.key != "video" }) }

        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `Right from the first group enters the pane at its first row`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        composeRule.onNodeWithText("Stream").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Resolution").assertIsFocused()
        assertThat(selected).isEqualTo("stream")
    }

    @Test
    fun `Right from a later group enters the pane at its first row, not the nearest`() {
        selected = "video"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Video").requestFocus()

        composeRule.onNodeWithText("Video").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Codec").assertIsFocused()
        assertThat(selected).isEqualTo("video")
    }

    @Test
    fun `Right from a group with a single row enters that row`() {
        selected = "about"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("About").requestFocus()

        composeRule.onNodeWithText("About").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Licences").assertIsFocused()
    }

    @Test
    fun `Left from any row of the pane returns to the selected group`() {
        selected = "video"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("HDR").requestFocus()

        composeRule.onNodeWithText("HDR").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selected).isEqualTo("video")
    }

    @Test
    fun `a slider at its lowest value passes Left on to return to the group`() {
        selected = "audio"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Vibration").requestFocus()

        composeRule.onNodeWithText("Vibration").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Audio").assertIsFocused()
    }

    @Test
    fun `an unknown selected key falls back to the first group`() {
        selected = "gone"
        composeRule.setContentInKeyboardMode { Specimen() }

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").requestFocus()
        composeRule.onNodeWithText("Resolution").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Stream").assertIsFocused()
    }

    @Test
    fun `no groups show an empty pane`() {
        composeRule.setContent { Specimen(emptyList()) }

        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `a group that scrolls itself fills the pane instead of nesting in its scroll`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                SettingsTwoPane(
                    groups = listOf(SettingsGroup("own", "Own")),
                    selected = "own",
                    onSelect = {},
                    selfScrolling = { true },
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsValueRow("Its own row", null, {})
                    }
                }
            }
        }

        composeRule.onNodeWithText("Its own row").assertIsDisplayed()
    }

    @Composable
    private fun Specimen(groups: List<SettingsGroup<String>> = allGroups) {
        BorealisTheme(FormFactor.Tv) {
            SettingsTwoPane(
                groups = groups,
                selected = selected,
                onSelect = {
                    selections += it
                    selected = it
                },
            ) { group ->
                when (group) {
                    "stream" -> {
                        SettingsValueRow("Resolution", "1280 x 720", {})
                        SettingsValueRow("Frame rate", "60 fps", {})
                    }
                    "video" -> {
                        SettingsValueRow("Codec", "Automatic", {})
                        SettingsSwitchRow("HDR", false, {})
                    }
                    "audio" -> {
                        SettingsValueRow("Channels", "Stereo", {})
                        SettingsSliderRow("Vibration", 0f, 0f..200f, 10f, "0 %", {})
                    }
                    else -> SettingsValueRow("Licences", null, {})
                }
            }
        }
    }
}
