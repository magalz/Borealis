package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.RowState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The groups of the settings screen, in the order of the list. */
private val GROUP_TITLES =
    listOf(
        "Stream",
        "Video",
        "Input",
        "Gamepad",
        "On-screen controls",
        "Audio",
        "Advanced",
        "Appearance",
        "Accessibility",
        "Privacy and diagnostics",
        "About",
    )

/** The entry of the group list with [title], and not a row of a pane that has the same words. */
private fun SettingsScreenTestBase.group(title: String): SemanticsNodeInteraction =
    composeRule.onNode(
        hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    )

/**
 * The [entry] is whole inside the window: the list does not cut it, and none of it is outside the
 * screen, where only a scroll would bring it.
 */
private fun SettingsScreenTestBase.assertWholeInWindow(entry: SemanticsNodeInteraction) {
    val window = composeRule.onRoot().getUnclippedBoundsInRoot()
    val whole = entry.getUnclippedBoundsInRoot()
    assertThat(entry.getBoundsInRoot()).isEqualTo(whole)
    assertThat(whole.top.value).isAtLeast(window.top.value)
    assertThat(whole.bottom.value).isAtMost(window.bottom.value)
    assertThat(whole.left.value).isAtLeast(window.left.value)
    assertThat(whole.right.value).isAtMost(window.right.value)
}

/** Walks Down from Stream to About: each group takes focus in turn, whole inside the window. */
private fun SettingsScreenTestBase.walkDownTheGroups() {
    group(GROUP_TITLES.first()).assertIsFocused()
    assertWholeInWindow(group(GROUP_TITLES.first()))
    GROUP_TITLES.zipWithNext().forEach { (from, to) ->
        group(from).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        group(to).assertIsFocused()
        assertWholeInWindow(group(to))
    }
}

/** The settings screen on a TV: the groups on the left and the rows of one group on the right. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsTvTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    private fun press(title: String, key: Key) {
        composeRule.onNodeWithText(title).performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun selectGroup(title: String) {
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the groups are on the left, Privacy and diagnostics and About among them`() {
        show(keyboard = true)

        // At rest, with no scroll: the eleven groups are not too tall for the window.
        GROUP_TITLES.forEach {
            group(it).assertIsDisplayed()
            assertWholeInWindow(group(it))
        }
    }

    @Test
    fun `Down from Stream to About focuses each group in turn, and the list never scrolls`() {
        show(keyboard = true)
        val top = group("Stream").getUnclippedBoundsInRoot().top

        walkDownTheGroups()

        assertThat(group("Stream").getUnclippedBoundsInRoot().top).isEqualTo(top)
    }

    @Test
    fun `the rows of the first group are on the right`() {
        show(keyboard = true)

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("Frame pacing").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `a group that the device does not use is not in the list`() {
        options.onScreenControlsGroup = RowState.Hidden
        options.resetLayout = RowState.Hidden

        show(keyboard = true)

        composeRule.onNodeWithText("On-screen controls").assertDoesNotExist()
        composeRule.onNodeWithText("Audio").assertIsDisplayed()
    }

    @Test
    fun `the Stream group has focus when the screen opens`() {
        show(keyboard = true)

        composeRule.onNodeWithText("Stream").assertIsFocused()
    }

    @Test
    fun `Right enters the rows of the selected group and Left returns to the group`() {
        show(keyboard = true)

        press("Stream", Key.DirectionRight)
        composeRule.onNodeWithText("Resolution").assertIsFocused()

        press("Resolution", Key.DirectionLeft)
        composeRule.onNodeWithText("Stream").assertIsFocused()
    }

    @Test
    fun `Left from a row further down also returns to the group`() {
        show(keyboard = true)
        press("Stream", Key.DirectionRight)
        press("Resolution", Key.DirectionDown)
        composeRule.onNodeWithText("Frame rate").assertIsFocused()

        press("Frame rate", Key.DirectionLeft)

        composeRule.onNodeWithText("Stream").assertIsFocused()
    }

    @Test
    fun `moving down the groups shows the rows of each`() {
        show(keyboard = true)

        press("Stream", Key.DirectionDown)

        composeRule.onNodeWithText("Video").assertIsFocused()
        composeRule.onNodeWithText("Codec").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `Back leaves the screen from the rows and from the groups`() {
        show(keyboard = true)
        press("Stream", Key.DirectionRight)

        Espresso.pressBack()
        composeRule.waitForIdle()
        assertThat(backs).isEqualTo(1)

        press("Resolution", Key.DirectionLeft)
        Espresso.pressBack()
        composeRule.waitForIdle()
        assertThat(backs).isEqualTo(2)
    }

    @Test
    fun `Privacy and diagnostics is a group that shows the privacy pane`() {
        show(keyboard = true)

        selectGroup("Privacy and diagnostics")

        composeRule.onNodeWithText("Privacy pane placeholder").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        assertThat(privacyOpens).isEqualTo(0)
    }

    @Test
    fun `a request for the Privacy group opens the screen on it`() {
        show(keyboard = true, initialGroup = SettingsGroupId.PRIVACY)

        composeRule.onNodeWithText("Privacy pane placeholder").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `the About group shows the version, the links and the licences`() {
        show(keyboard = true)

        selectGroup("About")

        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("9.8.7 (commit abc1234)").assertIsDisplayed()
        composeRule.onNodeWithText("Source code").assertIsDisplayed()
        composeRule.onNodeWithText("Open-source licences").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").assertIsDisplayed()

        composeRule.onNodeWithText("Privacy policy").performClick()
        assertThat(openedLinks)
            .containsExactly("https://github.com/magalz/Borealis/blob/main/PRIVACY.md")
    }

    @Test
    fun `the Appearance group has the Background row with the seven styles`() {
        show(keyboard = true)

        selectGroup("Appearance")

        composeRule.onNodeWithText("Background").assertIsDisplayed()
        composeRule.onNodeWithText("Aurora curtains").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("Background").performClick()
        composeRule.waitForIdle()
        BACKGROUND_NAMES.forEach { sheetOption(it).assertExists() }
        sheetOption("Aurora curtains").assertIsFocused()
    }

    @Test
    fun `choosing a style stores it and the row shows it`() {
        show(keyboard = true)
        selectGroup("Appearance")
        composeRule.onNodeWithText("Background").performClick()
        composeRule.waitForIdle()

        sheetOption("Starfield").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.BACKGROUND]).isEqualTo("starfield")
        composeRule.onNodeWithText("Starfield").assertIsDisplayed()
        composeRule.onNodeWithText("Aurora curtains").assertDoesNotExist()
    }

    @Test
    fun `a stored style that is unknown shows the default as the chosen one`() {
        stored(Settings.BACKGROUND to "bogus")
        show(keyboard = true)

        selectGroup("Appearance")

        composeRule.onNodeWithText("Aurora curtains").assertIsDisplayed()
        composeRule.onNodeWithText("bogus").assertDoesNotExist()
    }

    @Test
    fun `a disabled row of the Input group can be focused and shows why it is disabled`() {
        options.rowStates[Settings.ANALOG_SCROLLING] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        show(keyboard = true)
        selectGroup("Input")

        composeRule.onNodeWithText("Analog scrolling").requestFocus()

        composeRule.onNodeWithText("Analog scrolling").assertIsFocused().assertIsNotEnabled()
        composeRule.onNodeWithText("Turn on Gamepad mouse first").assertIsDisplayed()
    }

    @Test
    fun `OK on a list opens a choice sheet with the focus on the chosen entry`() {
        show(keyboard = true)
        press("Stream", Key.DirectionRight)
        press("Resolution", Key.DirectionDown)
        press("Frame rate", Key.DirectionDown)
        press("Bitrate", Key.DirectionDown)
        composeRule.onNodeWithText("Frame pacing").assertIsFocused()

        press("Frame pacing", Key.Enter)

        sheetOption("Lowest latency").assertIsFocused()
        sheetOption("Smoothest video").performClick()
        composeRule.waitForIdle()
        assertThat(snapshot[Settings.FRAME_PACING]).isEqualTo("smoothness")
    }

    @Test
    fun `the D-pad moves the bitrate by 5 megabits`() {
        stored(Settings.BITRATE_KBPS to 10_000)
        show(keyboard = true)
        composeRule.onNodeWithText("Bitrate").requestFocus()

        press("Bitrate", Key.DirectionRight)

        assertThat(snapshot[Settings.BITRATE_KBPS]).isEqualTo(15_000)
        press("Bitrate", Key.DirectionLeft)
        press("Bitrate", Key.DirectionLeft)
        assertThat(snapshot[Settings.BITRATE_KBPS]).isEqualTo(5_000)
    }

    @Test
    fun `the D-pad moves the deadzone one step at a time`() {
        show(keyboard = true)
        selectGroup("Input")
        composeRule.onNodeWithText("Deadzone").requestFocus()

        press("Deadzone", Key.DirectionRight)

        assertThat(snapshot[Settings.DEADZONE]).isEqualTo(8)
    }

    @Test
    fun `Reset layout on a TV asks first with Cancel focused`() {
        show(keyboard = true)
        selectGroup("On-screen controls")
        composeRule.onNodeWithText("Reset layout").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.onNodeWithText("Cannot be undone", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(resets).isEqualTo(0)
    }
}

/** The settings screen on a handheld: two panes, like a TV. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = HANDHELD)
class SettingsHandheldTest : SettingsScreenTestBase() {
    @Test
    fun `a handheld shows the groups and the rows in two panes`() {
        show(keyboard = true)

        composeRule.onNodeWithText("Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy and diagnostics").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
    }

    @Test
    fun `Down from Stream to About focuses each group in turn, whole inside the window`() {
        show(keyboard = true)

        walkDownTheGroups()
    }

    @Test
    fun `the Appearance group has the Background row with the seven styles`() {
        show(keyboard = true)

        // The group list of a handheld may scroll: do not rely on how many groups fit in 480dp.
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Background").assertIsDisplayed()
        composeRule.onNodeWithText("Aurora curtains").assertIsDisplayed()
        composeRule.onNodeWithText("Background").performClick()
        composeRule.waitForIdle()
        BACKGROUND_NAMES.forEach { sheetOption(it).assertExists() }
    }
}
