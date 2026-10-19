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
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
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

    /** A screen that applies a selection some frames after [SettingsTwoPane] asks for it. */
    private var deferSelection = false
    private var held: String? = null

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
    fun `moving focus down the left list selects the next group by its key once focus rests`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)
        restOnTheGroup()

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `the pane keeps its group until focus has rested on another one`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(WAIT_MILLIS / 2)

        composeRule.onNodeWithText("Video").assertIsFocused()
        assertThat(selections).isEmpty()
        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()

        restOnTheGroup()

        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
    }

    @Test
    fun `moving over several groups quickly selects only the last one`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        restOnTheGroup()

        composeRule.onNodeWithText("About").assertIsFocused()
        assertThat(selections).containsExactly("about")
        composeRule.onNodeWithText("Licences").assertIsDisplayed()
    }

    @Test
    fun `moving back to the selected group before the delay selects nothing`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)
        press(Key.DirectionUp)
        restOnTheGroup()

        composeRule.onNodeWithText("Stream").assertIsFocused()
        assertThat(selections).isEmpty()
        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
    }

    @Test
    fun `the group that has focus looks selected at once`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()

        press(Key.DirectionDown)

        composeRule.onNodeWithText("Video").assertIsSelected()
        composeRule.onNodeWithText("Stream").assertIsNotSelected()
        assertThat(selections).isEmpty()
    }

    @Test
    fun `OK on a group selects it at once`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        press(Key.Enter)

        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        restOnTheGroup()
        assertThat(selections).containsExactly("video")
    }

    @Test
    fun `Right from a group the pane has not followed yet selects it and enters its rows`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        press(Key.DirectionRight)

        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Codec").assertIsFocused()
        restOnTheGroup()
        assertThat(selections).containsExactly("video")
    }

    @Test
    fun `Left from the pane after Right on a pending group returns to the new group`() {
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
    fun `Right on a pending group waits for the group when the selection lands later`() {
        deferSelection = true
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        press(Key.DirectionRight)
        composeRule.mainClock.advanceTimeBy(WAIT_MILLIS / 2)
        composeRule.waitForIdle()

        // The caller was asked and has not applied it: the old pane must not take focus.
        assertThat(selections).containsExactly("video")
        composeRule.onNodeWithText("Resolution").assertIsNotFocused()

        landSelection()

        composeRule.onNodeWithText("Codec").assertIsFocused()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `moving to another group before the pending one arrives cancels entering the pane`() {
        deferSelection = true
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        press(Key.DirectionDown)
        landSelection()

        composeRule.onNodeWithText("Codec").assertIsNotFocused()
        composeRule.onNodeWithText("Audio").assertIsFocused()
    }

    @Test
    fun `a key that moves nothing also cancels entering the pane`() {
        deferSelection = true
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        press(Key.DirectionLeft)
        landSelection()

        composeRule.onNodeWithText("Codec").assertIsNotFocused()
        composeRule.onNodeWithText("Video").assertIsFocused()
    }

    @Test
    fun `focus leaving the group list cancels the pending selection`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        composeRule.onNodeWithText("Resolution").requestFocus()
        restOnTheGroup()

        assertThat(selections).isEmpty()
        composeRule.onNodeWithText("Resolution").assertIsFocused()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
        composeRule.onNodeWithText("Stream").assertIsSelected()
    }

    @Test
    fun `a tap on another group cancels the pending one`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)

        composeRule.onNodeWithText("Audio").performClick()
        restOnTheGroup()

        assertThat(selections).containsExactly("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `OK on a group that focus reached after a pending one cancels that one`() {
        composeRule.setContentInKeyboardMode { Specimen() }
        composeRule.onNodeWithText("Stream").requestFocus()
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        press(Key.Enter)
        restOnTheGroup()

        assertThat(selections).containsExactly("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @Test
    fun `a tap on a group selects it`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Audio").performClick()

        assertThat(selected).isEqualTo("audio")
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
        restOnTheGroup()
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

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    /** Applies the selection that the screen was asked for and has held back. */
    private fun landSelection() {
        composeRule.runOnIdle { selected = checkNotNull(held) }
        composeRule.waitForIdle()
    }

    /** Lets focus rest on the group it is on for longer than the pane waits. */
    private fun restOnTheGroup() {
        composeRule.mainClock.advanceTimeBy(2 * WAIT_MILLIS)
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(
        groups: List<SettingsGroup<String>> = allGroups,
        formFactor: FormFactor = FormFactor.Tv,
        focusOnOpen: Boolean = false,
    ) {
        BorealisTheme(formFactor) {
            SettingsTwoPane(
                groups = groups,
                selected = { selected },
                onSelect = {
                    selections += it
                    if (deferSelection) held = it else selected = it
                },
                selectDelayMillis = WAIT_MILLIS,
                focusOnOpen = focusOnOpen,
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

    private companion object {
        /**
         * The wait of the pane in these tests. It is far longer than the frames that one step of a
         * test lets pass, so that the steps are what the tests mean them to be, whatever the screen
         * uses.
         */
        const val WAIT_MILLIS = 300L
    }
}
