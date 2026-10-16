package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
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

/** UC-004: the in-game menu, a side panel over the stream. */
@RunWith(AndroidJUnit4::class)
class InGameMenuTest {
    @get:Rule val composeRule = createComposeRule()

    private fun menu(
        formFactor: FormFactor,
        overlay: OverlayMode = OverlayMode.OFF,
        touchMode: TouchMode? = TouchMode.TRACKPAD,
        gamepadMouse: Boolean? = null,
        withPerf: Boolean = true,
    ): LayerFixture {
        val phase = aStreaming(Panel.MENU, overlay, touchMode, gamepadMouse)
        val perf = if (withPerf) aPerf() else null
        val keyboardMode = formFactor != FormFactor.Phone
        return LayerFixture(composeRule, formFactor, phase, perf, keyboardMode = keyboardMode)
            .also { it.show() }
    }

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
                "Network" to "12 ms ± 3 · loss 0.5 %",
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
    fun `Quit says it ends the app and asks first`() {
        val fixture = menu(FormFactor.Phone)

        text("Ends the app on the PC").performScrollTo().assertIsDisplayed()
        text("Quit Desktop").performScrollTo().performClick()

        text("Quit Desktop? Unsaved work is lost.").assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
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

        text("Quit Desktop? Unsaved work is lost.").assertDoesNotExist()
        text("Back to stream").assertIsDisplayed()
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

        text("Quit Desktop? Unsaved work is lost.").assertDoesNotExist()
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

    @Test
    @Config(qualifiers = TV)
    fun `gamepad A is left to the platform so it presses the focused row`() {
        val fixture = menu(FormFactor.Tv, touchMode = null, gamepadMouse = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.ButtonA) }

        assertThat(fixture.intents.calls).isEmpty()
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
}
