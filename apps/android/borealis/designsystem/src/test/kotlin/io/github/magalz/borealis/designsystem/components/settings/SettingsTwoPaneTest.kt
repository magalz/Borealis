package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.testing.FakeInputModeManager
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Test

class SettingsTwoPaneTest : SettingsTwoPaneTestBase("stream") {
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
    fun `moving focus down the left list selects the next group by its key at once`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)

        composeRule.onNodeWithText("Video").assertIsFocused()
        composeRule.onNodeWithText("Video").assertIsSelected()
        composeRule.onNodeWithText("Stream").assertIsNotSelected()
        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `moving down over several groups selects each of them in turn`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        composeRule.onNodeWithText("About").assertIsFocused()
        assertThat(selections).containsExactly("video", "audio", "about").inOrder()
        composeRule.onNodeWithText("Licences").assertIsDisplayed()
    }

    @Test
    fun `OK on the group that has focus keeps it selected`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        press(Key.Enter)

        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
    }

    @Test
    fun `Right right after Down enters the rows of the new group`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        press(Key.DirectionRight)

        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsFocused()
    }

    @Test
    fun `Left from the pane after Down and Right returns to the new group`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        composeRule.onNodeWithText("Codec").assertIsFocused()

        press(Key.DirectionLeft)

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selected).isEqualTo("video")
    }

    @Test
    fun `focus moving to another group after Right and before the next frame cancels entering`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        // The pane takes focus a frame after Right; focus moves to another group before it.
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Audio").requestFocus()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Channels").assertIsNotFocused()
        composeRule.onNodeWithText("Audio").assertIsFocused()
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `a tap on a group selects it`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Audio").performClick()

        assertThat(selected).isEqualTo("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        assertThat(selections).containsExactly("audio")
    }

    @Test
    fun `the selection is a key, so a hidden group does not shift it`() {
        selected = "audio"
        composeRule.setContent { Specimen(allGroups.filter { it.key != "video" }) }

        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `OK on a group enters the pane at its first row`() = okEntersTheFirstRow {
        press(Key.DirectionCenter)
    }

    @Test
    fun `Enter on a group enters the pane at its first row`() = okEntersTheFirstRow {
        press(Key.Enter)
    }

    @Test
    fun `the numpad Enter on a group enters the pane at its first row`() = okEntersTheFirstRow {
        press(Key.NumPadEnter)
    }

    @Test
    fun `the gamepad A button on a group enters the pane at its first row`() = okEntersTheFirstRow {
        pressGamepadA()
    }

    private fun okEntersTheFirstRow(pressOk: () -> Unit) {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithText("Video").assertIsFocused()

        pressOk()

        // The first row, not the nearest to the group, and the group stays selected.
        composeRule.onNodeWithText("Codec").assertIsFocused()
        assertThat(selected).isEqualTo("video")
        assertThat(selections).containsExactly("video")
    }

    @Test
    fun `OK on the first group enters the pane at its first row`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionCenter)

        composeRule.onNodeWithText("Resolution").assertIsFocused()
        assertThat(selected).isEqualTo("stream")
        assertThat(selections).isEmpty()
    }

    @Test
    fun `OK on a group that is not the selected one selects it and enters its first row`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        // Another group is selected from outside while focus stays where the user left it.
        composeRule.runOnUiThread { selected = "video" }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stream").assertIsFocused()
        assertThat(selections).isEmpty()

        press(Key.DirectionCenter)

        assertThat(selected).isEqualTo("stream")
        assertThat(selections).containsExactly("stream")
        composeRule.onNodeWithText("Resolution").assertIsFocused()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `OK on a group with a single row enters that row`() {
        selected = "about"
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("About").requestFocus()

        press(Key.DirectionCenter)

        composeRule.onNodeWithText("Licences").assertIsFocused()
    }

    @Test
    fun `Left from the pane that OK entered returns to the group`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        composeRule.onNodeWithText("Codec").assertIsFocused()

        press(Key.DirectionLeft)

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selected).isEqualTo("video")
    }

    @Test
    fun `focus moving to another group after OK and before the next frame cancels entering`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        // The pane takes focus a frame after OK; focus moves to another group before it.
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Audio").requestFocus()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Channels").assertIsNotFocused()
        composeRule.onNodeWithText("Audio").assertIsFocused()
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `a tap on a group selects it and takes no focus into the pane`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Audio").performClick()
        composeRule.waitForIdle()

        assertThat(selected).isEqualTo("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        // The slider and the notes take focus in touch mode too: a pane that was entered shows it.
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    @Test
    fun `a tap in a window that was in keyboard mode selects the group and enters no pane`() {
        val window = FakeInputModeManager(InputMode.Keyboard)
        composeRule.setContentInKeyboardMode {
            CompositionLocalProvider(LocalInputModeManager provides window) { Specimen() }
        }

        window.inputMode = InputMode.Touch
        composeRule.onNodeWithText("Audio").performClick()
        composeRule.waitForIdle()

        assertThat(selected).isEqualTo("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        // The slider and the notes take focus in touch mode too: a pane that was entered shows it.
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)
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
    fun `with focusOnOpen the selected group takes focus when the panes open`() {
        composeRule.setContent { Specimen(focusOnOpen = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Stream").assertIsFocused()
    }

    @Test
    fun `with focusOnOpen the group that is selected takes focus, whichever it is`() {
        selected = "video"
        composeRule.setContent { Specimen(focusOnOpen = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Video").assertIsFocused()
    }

    @Test
    fun `focusOnOpen is not taken again when the selection changes later`() {
        composeRule.setContent { Specimen(focusOnOpen = true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stream").assertIsFocused()

        // Another group is selected from outside while focus stays where the user left it.
        composeRule.runOnUiThread { selected = "video" }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Stream").assertIsFocused()
        composeRule.onNodeWithText("Video").assertIsNotFocused()
    }

    @Test
    fun `without focusOnOpen no group takes focus when the panes open`() {
        composeRule.setContent { Specimen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Stream").assertIsNotFocused()
    }

    @Test
    fun `a request for a row that is not the first, from a group, stays on that row`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Frame rate").requestFocus()

        composeRule.onNodeWithText("Frame rate").assertIsFocused()
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
                    selected = { "own" },
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

    @Test
    fun `on a TV the group list is 220dp wide`() {
        composeRule.setContent { Specimen() }

        // A group fills the list less the 8dp padding on each side.
        val group = composeRule.onNodeWithText("Stream").getUnclippedBoundsInRoot()

        assertThat(group.right - group.left).isEqualTo(204.dp)
    }

    @Test
    fun `on a handheld the group list is 200dp wide, as wide as the rail`() {
        composeRule.setContent { Specimen(formFactor = FormFactor.Handheld) }

        val group = composeRule.onNodeWithText("Stream").getUnclippedBoundsInRoot()

        assertThat(group.right - group.left).isEqualTo(184.dp)
    }

    @Composable
    private fun Specimen(
        groups: List<SettingsGroup<String>> = allGroups,
        formFactor: FormFactor = FormFactor.Tv,
        focusOnOpen: Boolean = false,
    ) {
        TwoPane(
            groups,
            formFactor = formFactor,
            focusOnOpen = focusOnOpen,
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
                    // Unlike the clickable rows it takes focus in touch mode too, as a text
                    // field does: a pane that something entered in that mode shows it.
                    Text("Notes", Modifier.focusable())
                }
                else -> SettingsValueRow("Licences", null, {})
            }
        }
    }
}
