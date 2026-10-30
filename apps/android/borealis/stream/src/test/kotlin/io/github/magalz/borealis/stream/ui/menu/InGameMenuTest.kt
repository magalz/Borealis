package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// What Quit says for Desktop: it ends the stream and closes no program. One sentence a line.
private const val DESKTOP_QUIT = "Ends the stream.\nThe display and controls go back to the PC."

// What it says for a game: the game closes and its unsaved progress is lost.
private const val GAME_QUIT = "Closes Hades II on the PC.\nUnsaved progress in it is lost."

/** Shows the menu of [phase], in the keyboard input mode unless the form factor is a phone's. */
private fun ComposeContentTestRule.showMenu(
    formFactor: FormFactor,
    phase: StreamPhase.Streaming,
    keyboardMode: Boolean = formFactor != FormFactor.Phone,
    appName: String = "Desktop",
) = LayerFixture(this, formFactor, phase, aPerf(), keyboardMode, appName).also { it.show() }

/** UC-004: the in-game menu, a side panel over the stream. */
@RunWith(AndroidJUnit4::class)
class InGameMenuTest {
    @get:Rule val composeRule = createComposeRule()

    private fun menu(
        formFactor: FormFactor,
        overlay: OverlayMode = OverlayMode.OFF,
        touchMode: TouchMode? = TouchMode.TRACKPAD,
        gamepadMouse: Boolean? = null,
        appName: String = "Desktop",
    ) =
        composeRule.showMenu(
            formFactor,
            aStreaming(Panel.MENU, overlay, touchMode, gamepadMouse),
            appName = appName,
        )

    private fun text(text: String) = composeRule.onNodeWithText(text)

    private fun SemanticsNodeInteraction.top() = fetchSemanticsNode().positionInRoot.y

    private fun rowsInOrder(vararg titles: String) {
        val tops = titles.map { text(it).top() }
        assertThat(tops).isInStrictOrder()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the header names the app and the host`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        text("Desktop · Gaming PC").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV lists the rows in the approved order`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        rowsInOrder(
            "Back to stream",
            "Keyboard",
            "Send keys",
            "Gamepad mouse",
            "Overlay",
            "Refresh the picture",
            "Session info",
            "Display for next launch",
            "Disconnect",
            "Quit Desktop",
        )
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone lists Touch where a TV lists Gamepad mouse`() {
        menu(FormFactor.Phone)

        rowsInOrder(
            "Back to stream",
            "Keyboard",
            "Send keys",
            "Touch",
            "Overlay",
            "Refresh the picture",
            "Session info",
            "Display for next launch",
            "Disconnect",
            "Quit Desktop",
        )
        text("Gamepad mouse").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `focus starts on Back to stream`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        text("Back to stream").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back to stream closes the menu`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        text("Back to stream").performClick()

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Keyboard opens the keyboard`() {
        val fixture = menu(FormFactor.Phone)

        text("Keyboard").performClick()

        assertThat(fixture.intents.calls).containsExactly("openKeyboard")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Send keys shows the chips and a chip sends its chord and leaves the menu open`() {
        val fixture = menu(FormFactor.Phone)
        text("Alt+Tab").assertDoesNotExist()

        text("Send keys").performClick()
        for (chip in
            listOf(
                "Alt+Tab",
                "Win+Tab",
                "Win",
                "Ctrl+Shift+Esc",
                "Alt+F4",
                "Win+D",
                "Win+G",
                "Esc",
                "Win+Alt+B",
            )) {
            text(chip).performScrollTo().assertIsDisplayed()
        }
        text("Win+D").performClick()
        text("Esc").performClick()

        assertThat(fixture.intents.chords)
            .containsExactly(KeyChord(0x44, setOf(KeyModifier.WIN)), KeyChord(0x1B))
            .inOrder()
        assertThat(fixture.intents.calls).isEmpty()
        text("Send keys").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a second tap on Send keys hides the chips`() {
        menu(FormFactor.Phone)

        text("Send keys").performClick()
        text("Send keys").performClick()

        text("Alt+Tab").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone offers the touch modes and marks the current one`() {
        val fixture = menu(FormFactor.Phone, touchMode = TouchMode.DIRECT)

        text("Direct").assertIsSelected()
        text("Trackpad").performClick()
        composeRule.onAllNodesWithText("Off")[0].performClick()

        assertThat(fixture.intents.calls)
            .containsExactly("setTouchMode TRACKPAD", "setTouchMode OFF")
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone without a touch mode has no Touch row`() {
        menu(FormFactor.Phone, touchMode = null)

        text("Touch").assertDoesNotExist()
        text("Trackpad").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV offers Gamepad mouse as On or Off and never Touch`() {
        val fixture = menu(FormFactor.Tv, touchMode = TouchMode.TRACKPAD, gamepadMouse = false)

        text("Touch").assertDoesNotExist()
        text("On").performClick()
        composeRule.onAllNodesWithText("Off")[0].performClick()

        assertThat(fixture.intents.calls)
            .containsExactly("setGamepadMouse true", "setGamepadMouse false")
            .inOrder()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a handheld offers Gamepad mouse`() {
        val fixture = menu(FormFactor.Handheld, touchMode = null, gamepadMouse = true)

        text("Gamepad mouse").assertIsDisplayed()
        text("On").assertIsSelected()
        text("Touch").assertDoesNotExist()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Pointer speed shows below Gamepad mouse while the gamepad mouse is on`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = true)

        rowsInOrder("Send keys", "Gamepad mouse", "Pointer speed", "Overlay")
        text("Slow").assertIsDisplayed()
        text("Normal").assertIsSelected()
        text("Fast").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a handheld offers Pointer speed too`() {
        menu(FormFactor.Handheld, touchMode = null, gamepadMouse = true)

        text("Pointer speed").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Pointer speed is hidden while the gamepad mouse is off or not offered`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Gamepad mouse").assertIsDisplayed()
        text("Pointer speed").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Pointer speed is hidden when there is no gamepad mouse choice`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = null)

        text("Pointer speed").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone has no Pointer speed`() {
        menu(FormFactor.Phone, gamepadMouse = true)

        text("Pointer speed").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `choosing Fast sets the pointer speed to Fast`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = true)

        text("Fast").performClick()

        assertThat(fixture.intents.calls).containsExactly("setPointerSpeed FAST")
    }

    @Test
    @Config(qualifiers = TV)
    fun `Pointer speed shows the speed of the session`() {
        composeRule.showMenu(
            FormFactor.Tv,
            aStreaming(Panel.MENU, touchMode = null, gamepadMouse = true)
                .copy(pointerSpeed = PointerSpeed.SLOW),
        )

        text("Slow").assertIsSelected()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV with no gamepad mouse choice has no Gamepad mouse row`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = null)

        text("Gamepad mouse").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay row marks the current mode and sets the one the user picks`() {
        val fixture = menu(FormFactor.Phone, overlay = OverlayMode.COMPACT)

        text("Compact").assertIsSelected()
        text("Detailed").performClick()
        composeRule.onAllNodesWithText("Off")[1].performScrollTo().performClick()

        assertThat(fixture.intents.calls)
            .containsExactly("setOverlay DETAILED", "setOverlay OFF")
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Refresh the picture asks for a keyframe and closes the menu`() {
        val fixture = menu(FormFactor.Phone)

        text("Refresh the picture").performClick()

        assertThat(fixture.intents.calls).containsExactly("requestKeyframe", "closePanel").inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Session info is closed until it is chosen`() {
        menu(FormFactor.Phone)

        text("Video").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Session info shows the video, the codec, the decoder, the host, the network and the bitrate`() {
        val info = aSessionInfo.copy(hdr = true)
        val fixture =
            LayerFixture(
                composeRule,
                FormFactor.Phone,
                aStreaming(Panel.MENU, info = info),
                aPerf(),
            )
        fixture.show()

        text("Session info").performClick()

        for ((label, value) in
            listOf(
                "Video" to "1920×1080 · 60 fps",
                "Codec" to "HEVC 10-bit HDR",
                "Decoder" to "c2.qti.hevc.decoder",
                "Host" to "1.0 / 2.0 / 5.0 ms",
                "Network" to "12 ms ± 3 ms · loss 0.5 %",
                "Bitrate" to "18.5 of 20 Mbps",
            )) {
            text(label).performScrollTo().assertIsDisplayed()
            text(value).performScrollTo().assertIsDisplayed()
        }
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Session info leaves out the decoder when it is not known and dashes the missing figures`() {
        val info = aSessionInfo.copy(decoder = null)
        LayerFixture(composeRule, FormFactor.Phone, aStreaming(Panel.MENU, info = info), null)
            .show()

        text("Session info").performClick()

        text("Decoder").assertDoesNotExist()
        text("— of 20 Mbps").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("— ms").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Display for next launch is a disabled stop with its note and does nothing`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        text("Needs the host API (Phase 3)").assertIsDisplayed()
        text("Display for next launch").performScrollTo().assertIsNotEnabled()
        text("Display for next launch").requestFocus().assertIsFocused()
        text("Display for next launch").performKeyInput { pressKey(Key.DirectionCenter) }
        text("Display for next launch").performClick()

        assertThat(fixture.intents.calls).isEmpty()
        text("Display for next launch").assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Disconnect says the app keeps running and disconnects`() {
        val fixture = menu(FormFactor.Phone)

        text("Desktop keeps running on the PC").performScrollTo().assertIsDisplayed()
        text("Disconnect").performScrollTo().performClick()

        assertThat(fixture.intents.calls).containsExactly("disconnect")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Quit on Desktop says it ends the stream and asks first`() {
        val fixture = menu(FormFactor.Phone)

        text(DESKTOP_QUIT).performScrollTo().assertIsDisplayed()
        text("Quit Desktop").performScrollTo().performClick()

        // The menu is out of reach behind the popup, so this is its body, not the row.
        text("Quit Desktop?").assertIsDisplayed()
        text(DESKTOP_QUIT).assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Quit on a game says it closes the game and its unsaved progress is lost`() {
        val fixture = menu(FormFactor.Phone, appName = "Hades II")

        text(GAME_QUIT).performScrollTo().assertIsDisplayed()
        text("Quit Hades II").performScrollTo().performClick()

        text("Quit Hades II?").assertIsDisplayed()
        text(GAME_QUIT).assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Desktop says nothing of lost work in the row or in the confirmation`() {
        menu(FormFactor.Phone)
        composeRule.onAllNodesWithText("Unsaved", substring = true).assertCountEquals(0)

        text("Quit Desktop").performScrollTo().performClick()

        composeRule.onAllNodesWithText("Unsaved", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("lost", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a Desktop with a different case or spaces is still Desktop`() {
        menu(FormFactor.Phone, appName = " desktop ")

        text(DESKTOP_QUIT).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an app whose title only contains Desktop is a game`() {
        menu(FormFactor.Phone, appName = "Remote Desktop")

        text("Closes Remote Desktop on the PC.\nUnsaved progress in it is lost.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Disconnect still says a game keeps running on the PC`() {
        menu(FormFactor.Phone, appName = "Hades II")

        text("Hades II keeps running on the PC").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `confirming Quit quits the app`() {
        val fixture = menu(FormFactor.Phone)
        text("Quit Desktop").performScrollTo().performClick()

        text("Quit").performClick()

        assertThat(fixture.intents.calls).containsExactly("quitApp")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `cancelling Quit goes back to the menu without quitting`() {
        val fixture = menu(FormFactor.Phone)
        text("Quit Desktop").performScrollTo().performClick()

        text("Cancel").performClick()

        // The menu is as it was left, scrolled to the Quit row.
        text("Quit Desktop?").assertDoesNotExist()
        text("Quit Desktop").assertIsDisplayed()
        text("Back to stream").assertExists()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the confirmation starts with focus on Cancel`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Quit Desktop").performScrollTo().requestFocus()
        text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }

        text("Cancel").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B closes the confirmation first and then the menu`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Quit Desktop").performScrollTo().requestFocus()
        text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonB) }

        text("Quit Desktop?").assertDoesNotExist()
        assertThat(fixture.intents.calls).isEmpty()

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonB) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B closes the menu`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonB) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    // The device round: Compose took the first Back for a focus exit out of the segmented control,
    // and it still would out of a key of the keyboard or a chip of Send keys.
    @Test
    @Config(qualifiers = TV)
    fun `Back with the focus on a Send keys chip closes the menu at once`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Send keys").performClick()
        text("Alt+Tab").performScrollTo().requestFocus()
        text("Alt+Tab").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
        assertThat(fixture.keysSeenOutside).doesNotContain(Key.Back)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back from the confirmation closes the confirmation first and then the menu`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Quit Desktop").performScrollTo().requestFocus()
        text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }
        text("Cancel").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }

        text("Quit Desktop?").assertDoesNotExist()
        text("Quit Desktop").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()

        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back with the focus on Back to stream closes the menu once`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad A is not consumed, so the platform can turn it into OK`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonA) }

        // A handler outside the menu sees it: nothing in the menu took it.
        assertThat(fixture.keysSeenOutside).containsExactly(Key.ButtonA)
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B is consumed by the menu`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonB) }

        assertThat(fixture.keysSeenOutside).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Refresh the picture says when to use it`() {
        menu(FormFactor.Phone)

        text("Asks the host for a keyframe when the image is smeared")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `cancelling Quit puts the focus back on the Quit row`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Quit Desktop").performScrollTo().requestFocus()
        text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }

        text("Cancel").performKeyInput { pressKey(Key.DirectionCenter) }

        text("Quit Desktop").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `gamepad B from the confirmation puts the focus back on the Quit row`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)
        text("Quit Desktop").performScrollTo().requestFocus()
        text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonB) }

        text("Quit Desktop").assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an open Send keys stays open after Quit is cancelled`() {
        menu(FormFactor.Phone)
        text("Send keys").performClick()
        text("Quit Desktop").performScrollTo().performClick()

        text("Cancel").performClick()

        text("Alt+Tab").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the rows are not reachable while the confirmation shows`() {
        menu(FormFactor.Phone)
        text("Quit Desktop").performScrollTo().performClick()

        text("Back to stream").assertDoesNotExist()
        text("Disconnect").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the scrim closes the menu`() {
        val fixture = menu(FormFactor.Phone)

        composeRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        assertThat(fixture.intents.calls).containsExactly("closePanel")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap inside the panel does not close it`() {
        val fixture = menu(FormFactor.Phone)

        text("Desktop · Gaming PC").performClick()

        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the handle is hidden while the menu is open`() {
        menu(FormFactor.Phone)

        composeRule.onNodeWithContentDescription("Open menu").assertDoesNotExist()
    }

    /** OK on the focused row of [title], as a D-pad user presses it. */
    private fun pressOk(title: String) {
        text(title).performScrollTo().requestFocus()
        text(title).performKeyInput { pressKey(Key.DirectionCenter) }
    }

    /** What the session reports back after a change: the row shows the new value. */
    private fun LayerFixture.reflect(change: (StreamPhase.Streaming) -> StreamPhase.Streaming) {
        phase = change(phase)
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on the Overlay row cycles Off, Compact, Detailed and back to Off`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        pressOk("Overlay")
        fixture.reflect { it.copy(overlay = OverlayMode.COMPACT) }
        pressOk("Overlay")
        fixture.reflect { it.copy(overlay = OverlayMode.DETAILED) }
        pressOk("Overlay")

        assertThat(fixture.intents.calls)
            .containsExactly("setOverlay COMPACT", "setOverlay DETAILED", "setOverlay OFF")
            .inOrder()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on the Gamepad mouse row flips it`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        pressOk("Gamepad mouse")
        fixture.reflect { it.copy(gamepadMouse = true) }
        pressOk("Gamepad mouse")

        assertThat(fixture.intents.calls)
            .containsExactly("setGamepadMouse true", "setGamepadMouse false")
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `OK on the Touch row cycles Trackpad, Direct, Off and back to Trackpad`() {
        // A phone with a keyboard or a gamepad attached: the keys reach the row.
        val fixture =
            composeRule.showMenu(
                FormFactor.Phone,
                aStreaming(Panel.MENU, touchMode = TouchMode.TRACKPAD),
                keyboardMode = true,
            )

        pressOk("Touch")
        fixture.reflect { it.copy(touchMode = TouchMode.DIRECT) }
        pressOk("Touch")
        fixture.reflect { it.copy(touchMode = TouchMode.OFF) }
        pressOk("Touch")

        assertThat(fixture.intents.calls)
            .containsExactly("setTouchMode DIRECT", "setTouchMode OFF", "setTouchMode TRACKPAD")
            .inOrder()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the focus stays on the option row after OK`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        pressOk("Overlay")

        text("Overlay").assertIsFocused()
        text("Compact").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the title of an option row changes the option like OK`() {
        val fixture = menu(FormFactor.Phone, overlay = OverlayMode.COMPACT)

        text("Overlay").performTouchInput { click(centerLeft + Offset(4f, 0f)) }

        assertThat(fixture.intents.calls).containsExactly("setOverlay DETAILED")
    }

    @Test
    @Config(qualifiers = TV)
    fun `an option row announces its value and that activating it changes the value`() {
        menu(FormFactor.Tv, overlay = OverlayMode.COMPACT, touchMode = null, gamepadMouse = true)

        text("Overlay")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Compact"))
            .assert(
                SemanticsMatcher("has the click label Change Overlay") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change Overlay"
                }
            )
        text("Gamepad mouse")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `a row without options has no value`() {
        menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        text("Keyboard")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
