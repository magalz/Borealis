package io.github.magalz.borealis.stream.game.keys

import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.R
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import org.junit.Test
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputEvent

/**
 * Where a key goes before it can reach the host: to the stream chrome while it is modal, to the
 * Android keyboard, to a gamepad handler, to the system, or to the host as a key or a mouse button.
 */
internal class GameKeyRoutingTest : GameTestBase() {
    private val down = KeyboardPacket.KEY_DOWN
    private val up = KeyboardPacket.KEY_UP

    private val streamView: View
        get() = rig.game.findViewById(R.id.surfaceView)

    private val panel: Panel
        get() = (rig.phase as StreamPhase.Streaming).panel

    // The chrome first

    @Test
    @Suppress("DEPRECATION") // onKeyMultiple and ACTION_MULTIPLE are what the activity handles.
    fun `while the chrome is modal the keys are its own and nothing reaches the host`() {
        rig.streaming()
        rig.openChrome()
        assertThat(rig.session.chromeMode).isEqualTo(ChromeMode.MODAL)
        forgetCalls()

        val handled =
            listOf(
                rig.game.handleKeyDown(keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)),
                rig.game.handleKeyUp(keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)),
                rig.game.onKeyDown(
                    KeyEvent.KEYCODE_B,
                    keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B),
                ),
                rig.game.onKeyMultiple(KeyEvent.KEYCODE_UNKNOWN, 1, textKey("text")),
                // The keys of a gamepad too: the chrome has the gamepad.
                rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)),
                rig.game.handleKeyUp(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)),
            )

        // Not handled: the activity default runs, and Compose gets the key.
        assertThat(handled).containsExactly(false, false, false, false, false, false)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `while the chrome is modal Ctrl Alt Shift and a key do not start a chord`() {
        rig.streaming()
        rig.openChrome()
        forgetCalls()

        val handled =
            listOf(
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_Q,
                )
                .map { rig.game.handleKeyDown(keyboardKey(KeyEvent.ACTION_DOWN, it)) }

        // The chord eats the Q; here every key is the chrome's, and the Q too.
        assertThat(handled).containsExactly(false, false, false, false)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `a docked keyboard leaves the keys to the stream`() {
        rig.streaming()
        rig.session.openKeyboard()
        idle()
        assertThat(rig.session.chromeMode).isEqualTo(ChromeMode.DOCKED)
        forgetCalls()

        val handled = rig.tap(KeyEvent.KEYCODE_A)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, 0, NON_NORMALIZED),
                SentKey(0x8041, up, 0, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `the keys come back to the stream when the chrome gives the input back`() {
        rig.streaming()
        rig.openChrome()
        rig.closeChrome()
        forgetCalls()

        val handled = rig.tap(KeyEvent.KEYCODE_A)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, 0, NON_NORMALIZED),
                SentKey(0x8041, up, 0, NON_NORMALIZED),
            )
            .inOrder()
    }

    // The Escape key acts as Back while the chrome is modal

    @Test
    fun `the Escape key acts as Back while the chrome is modal, on its release`() {
        rig.streaming()
        rig.openChrome()
        assertThat(panel).isEqualTo(Panel.MENU)
        forgetCalls()

        val pressed =
            rig.game.onKeyDown(
                KeyEvent.KEYCODE_ESCAPE,
                keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
            )

        // The key down is taken and does nothing yet.
        assertThat(pressed).isTrue()
        assertThat(panel).isEqualTo(Panel.MENU)

        val released =
            rig.game.onKeyUp(
                KeyEvent.KEYCODE_ESCAPE,
                keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE),
            )

        // Back closes the menu that is open.
        assertThat(released).isTrue()
        assertThat(panel).isEqualTo(Panel.NONE)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the Escape key leaves the stream while it is starting, as Back does`() {
        rig.create()
        assertThat(rig.game.isFinishing).isFalse()

        rig.game.onKeyDown(
            KeyEvent.KEYCODE_ESCAPE,
            keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
        )
        assertThat(rig.game.isFinishing).isFalse()
        val released =
            rig.game.onKeyUp(
                KeyEvent.KEYCODE_ESCAPE,
                keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE),
            )

        assertThat(released).isTrue()
        assertThat(rig.game.isFinishing).isTrue()
    }

    @Test
    fun `outside the modal chrome the Escape key goes to the host`() {
        rig.streaming()
        forgetCalls()

        val handled = rig.tap(KeyEvent.KEYCODE_ESCAPE)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x801B, down, 0, NON_NORMALIZED),
                SentKey(0x801B, up, 0, NON_NORMALIZED),
            )
            .inOrder()
        assertThat(panel).isEqualTo(Panel.NONE)
    }

    // The Android keyboard keeps the D-pad, Enter and Back of a gamepad

    private val imeKeys =
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK,
        )

    @Test
    fun `while the Android keyboard shows, the D-pad, Enter and Back of a gamepad are left to it`() {
        rig.streaming()
        rig.androidKeyboardShown(true)
        forgetCalls()

        for (code in imeKeys) {
            val pressed = rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, code))
            val released = rig.game.handleKeyUp(padKey(KeyEvent.ACTION_UP, code))

            assertWithMessage("key $code down").that(pressed).isFalse()
            assertWithMessage("key $code up").that(released).isFalse()
        }

        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `while the Android keyboard shows, the other keys of a gamepad still reach the pad`() {
        rig.streaming()
        rig.androidKeyboardShown(true)
        forgetCalls()

        val pressed =
            rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        val released = rig.game.handleKeyUp(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.A_FLAG, NO_BUTTONS).inOrder()
    }

    @Test
    fun `when the Android keyboard is not showing, the D-pad of a gamepad belongs to the pad`() {
        rig.streaming()
        rig.androidKeyboardShown(false)
        forgetCalls()

        val pressed = rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        val released = rig.game.handleKeyUp(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.UP_FLAG, NO_BUTTONS).inOrder()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `while the Android keyboard shows, a hardware keyboard keeps its Enter and arrows`() {
        rig.streaming()
        rig.androidKeyboardShown(true)
        forgetCalls()

        val handled = rig.tap(KeyEvent.KEYCODE_ENTER) + rig.tap(KeyEvent.KEYCODE_DPAD_LEFT)

        assertThat(handled).containsExactly(true, true, true, true)
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x800D, down, 0, NON_NORMALIZED),
                SentKey(0x800D, up, 0, NON_NORMALIZED),
                SentKey(0x8025, down, 0, NON_NORMALIZED),
                SentKey(0x8025, up, 0, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `while the Android keyboard shows, the Back of a right click is still a mouse button`() {
        rig.streaming()
        rig.androidKeyboardShown(true)
        forgetCalls()

        val pressed =
            rig.game.handleKeyDown(mouseBack(KeyEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE))
        val released = rig.game.handleKeyUp(mouseBack(KeyEvent.ACTION_UP, InputDevice.SOURCE_MOUSE))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentMouseButtons())
            .containsExactly(
                MouseButtonPacket.PRESS_EVENT to MouseButtonPacket.BUTTON_RIGHT,
                MouseButtonPacket.RELEASE_EVENT to MouseButtonPacket.BUTTON_RIGHT,
            )
            .inOrder()
    }

    // The gamepad branch

    @Test
    fun `a key of a gamepad goes to the pad handler, not to the host as a key`() {
        rig.streaming()
        forgetCalls()

        val pressed =
            rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        val released =
            rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.A_FLAG, NO_BUTTONS).inOrder()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `a key that the pad handler does not take goes on to the host as a key`() {
        rig.streaming()
        forgetCalls()

        val pressed = rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
        val released = rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, 0, NON_NORMALIZED),
                SentKey(0x8041, up, 0, NON_NORMALIZED),
            )
            .inOrder()
        assertThat(sentPadButtons()).isEmpty()
    }

    @Test
    fun `the D-pad of a keyboard with letters goes to the host, the D-pad of a gamepad to the pad`() {
        rig.streaming()
        forgetCalls()

        rig.tap(KeyEvent.KEYCODE_DPAD_UP)
        val fromKeyboard = sentKeys()
        val padButtonsFromKeyboard = sentPadButtons()
        forgetCalls()
        rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))

        assertThat(fromKeyboard)
            .containsExactly(
                SentKey(0x8026, down, 0, NON_NORMALIZED),
                SentKey(0x8026, up, 0, NON_NORMALIZED),
            )
            .inOrder()
        assertThat(padButtonsFromKeyboard).isEmpty()
        assertThat(sentKeys()).isEmpty()
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.UP_FLAG, NO_BUTTONS).inOrder()
    }

    // The system's keys and the right click

    @Test
    fun `a virtual navigation key is left to the system, down and up`() {
        rig.streaming()
        forgetCalls()
        val flag = KeyEvent.FLAG_VIRTUAL_HARD_KEY

        val handled =
            listOf(
                rig.game.handleKeyDown(
                    keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, flags = flag)
                ),
                rig.game.handleKeyUp(
                    keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, flags = flag)
                ),
                // Before the right click is looked at.
                rig.game.handleKeyDown(
                    mouseBack(KeyEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE, flags = flag)
                ),
                rig.game.handleKeyUp(
                    mouseBack(KeyEvent.ACTION_UP, InputDevice.SOURCE_MOUSE, flags = flag)
                ),
            )

        assertThat(handled).containsExactly(false, false, false, false)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the Back that Android makes of a right click is a right mouse button, and always handled`() {
        rig.streaming()

        for (source in listOf(InputDevice.SOURCE_MOUSE, InputDevice.SOURCE_MOUSE_RELATIVE)) {
            forgetCalls()

            val pressed = rig.game.handleKeyDown(mouseBack(KeyEvent.ACTION_DOWN, source))
            val released = rig.game.handleKeyUp(mouseBack(KeyEvent.ACTION_UP, source))

            assertWithMessage("source $source").that(pressed).isTrue()
            assertWithMessage("source $source").that(released).isTrue()
            assertWithMessage("source $source")
                .that(sentMouseButtons())
                .containsExactly(
                    MouseButtonPacket.PRESS_EVENT to MouseButtonPacket.BUTTON_RIGHT,
                    MouseButtonPacket.RELEASE_EVENT to MouseButtonPacket.BUTTON_RIGHT,
                )
                .inOrder()
            assertWithMessage("source $source").that(sentKeys()).isEmpty()
        }
    }

    @Test
    fun `with the mouse navigation buttons on, the Back of a mouse is handled and the motion path sends the button`() {
        rig.preference("checkbox_mouse_nav_buttons", true)
        rig.streaming()
        forgetCalls()

        val pressed =
            rig.game.handleKeyDown(mouseBack(KeyEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE))
        val released = rig.game.handleKeyUp(mouseBack(KeyEvent.ACTION_UP, InputDevice.SOURCE_MOUSE))

        // Handled, so that the back press does not finish the activity, and nothing is sent here.
        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the Back of a keyboard is no right click and goes nowhere`() {
        rig.streaming()
        forgetCalls()

        val pressed =
            rig.game.handleKeyDown(keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        val released = rig.game.handleKeyUp(keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertThat(pressed).isFalse()
        assertThat(released).isFalse()
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the Back of a pad that has a mouse source is a right click, and the pad does not take it`() {
        rig.streaming()
        // A pad with a touchpad: its context passes Back on as a pad button, so only the order of
        // the checks keeps the right click out of the pad.
        val pad =
            inputDevice(
                PAD_ID,
                InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_MOUSE,
                joystickXy = true,
            )
        connect(rig.application, pad)
        forgetCalls()

        val pressed = rig.game.handleKeyDown(mouseBackOf(pad, KeyEvent.ACTION_DOWN))
        val released = rig.game.handleKeyUp(mouseBackOf(pad, KeyEvent.ACTION_UP))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentMouseButtons())
            .containsExactly(
                MouseButtonPacket.PRESS_EVENT to MouseButtonPacket.BUTTON_RIGHT,
                MouseButtonPacket.RELEASE_EVENT to MouseButtonPacket.BUTTON_RIGHT,
            )
            .inOrder()
        assertThat(sentPadButtons()).isEmpty()
    }

    // The key that arms a chord

    /**
     * Ctrl, Alt and Shift go down, then [code]; the key comes up after the three when
     * [afterModifiers] is set, and before them otherwise. Nothing runs the main looper in between,
     * so what the chord posts (the toggle of the grab, the chrome taking the input) has not run
     * when the key comes up. Returns whether the activity handled the key down and the key up.
     */
    private fun chordKey(code: Int, afterModifiers: Boolean): List<Boolean> {
        rig.holdCtrlAltShift()
        val pressed = rig.down(code)
        if (afterModifiers) {
            rig.releaseCtrlAltShift()
            return listOf(pressed, rig.up(code))
        }
        val released = rig.up(code)
        rig.releaseCtrlAltShift()
        return listOf(pressed, released)
    }

    @Test
    fun `the Z that arms the grab chord, released after the modifiers, sends no key up to the host`() {
        rig.streaming()
        forgetCalls()

        val handled = chordKey(KeyEvent.KEYCODE_Z, afterModifiers = true)

        assertThat(handled).containsExactly(true, true)
        // The host saw the modifiers and never the Z: no key down, so no key up.
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `the Z that arms the grab chord, released before the modifiers, is not the host's either`() {
        rig.streaming()
        forgetCalls()

        val handled = chordKey(KeyEvent.KEYCODE_Z, afterModifiers = false)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `the M that arms the menu chord, released after the modifiers, sends no key up to the host`() {
        rig.streaming()
        forgetCalls()

        val handled = chordKey(KeyEvent.KEYCODE_M, afterModifiers = true)

        // The menu is modal as soon as it opens, so this key up goes to the chrome and never
        // meets the chord code: no lone key up reaches the host before the change either.
        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `the M that arms the menu chord, released before the modifiers, is not the host's either`() {
        rig.streaming()
        forgetCalls()

        val handled = chordKey(KeyEvent.KEYCODE_M, afterModifiers = false)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `the C that arms the cursor chord, released after the modifiers, sends no key up to the host`() {
        rig.streaming()
        forgetCalls()

        val handled = chordKey(KeyEvent.KEYCODE_C, afterModifiers = true)

        assertThat(handled).containsExactly(true, true)
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `the key of a chord that goes down again while the chord waits is eaten, and so is its key up`() {
        rig.streaming()
        forgetCalls()

        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        rig.up(KeyEvent.KEYCODE_Z)
        val pressedAgain = rig.down(KeyEvent.KEYCODE_Z)
        rig.releaseCtrlAltShift()
        val releasedAgain = rig.up(KeyEvent.KEYCODE_Z)

        assertThat(pressedAgain).isTrue()
        assertThat(releasedAgain).isTrue()
        assertThat(sentKeys().map { it.code }).containsExactlyElementsIn(MODIFIERS_ONLY).inOrder()
    }

    @Test
    fun `a key of a gamepad goes to the pad while a chord waits for the modifiers, down and up`() {
        rig.streaming()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        // The Up of the keyboard goes down while the chord waits: it is eaten, and so is its key
        // up.
        assertThat(rig.down(KeyEvent.KEYCODE_DPAD_UP)).isTrue()
        forgetCalls()

        val handled =
            listOf(
                rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)),
                rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)),
                // The Up of the pad is the pad's, though the keyboard's Up is waiting to be eaten.
                rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)),
                rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)),
            )

        assertThat(handled).containsExactly(true, true, true, true)
        assertThat(sentPadButtons())
            .containsExactly(
                ControllerPacket.A_FLAG,
                NO_BUTTONS,
                ControllerPacket.UP_FLAG,
                NO_BUTTONS,
            )
            .inOrder()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `a key that the pad took never reaches the special keys, down or up`() {
        rig.streaming()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        // The keyboard's Up goes down while the chord waits, and its key up is to be eaten.
        rig.down(KeyEvent.KEYCODE_DPAD_UP)
        // The pad's Up comes and goes, and the pad's Down goes down: none of them is the chord's.
        rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
        rig.game.dispatchKeyEvent(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        rig.releaseCtrlAltShift()
        forgetCalls()

        // The chord ate the key down of the keyboard's Up, so its key up is eaten.
        val eaten = rig.up(KeyEvent.KEYCODE_DPAD_UP)
        // The chord never ate a Down: the key up of the keyboard's Down is the host's.
        val sent = rig.up(KeyEvent.KEYCODE_DPAD_DOWN)

        assertThat(eaten).isTrue()
        assertThat(sent).isTrue()
        assertThat(sentKeys()).containsExactly(SentKey(0x8028, up, 0, NON_NORMALIZED))
    }

    // The entry points

    @Test
    fun `the view listener gives a key to the activity by its action`() {
        rig.streaming()
        forgetCalls()
        val a = KeyEvent.KEYCODE_A

        val handled =
            listOf(
                rig.game.onKey(streamView, a, keyboardKey(KeyEvent.ACTION_DOWN, a)),
                rig.game.onKey(streamView, a, keyboardKey(KeyEvent.ACTION_UP, a)),
                rig.game.onKey(streamView, KeyEvent.KEYCODE_UNKNOWN, textKey("hi")),
                // An action that no key has.
                rig.game.onKey(streamView, a, keyboardKey(KeySpec(ACTION_NONE, a))),
            )

        assertThat(handled).containsExactly(true, true, true, false).inOrder()
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, 0, NON_NORMALIZED),
                SentKey(0x8041, up, 0, NON_NORMALIZED),
            )
            .inOrder()
        assertThat(sentTexts()).containsExactly("hi")
    }

    @Test
    fun `the stream view gives the keys to the activity before the keyboard of Android sees them`() {
        rig.streaming()
        forgetCalls()
        val a = KeyEvent.KEYCODE_A

        val pressed = streamView.onKeyPreIme(a, keyboardKey(KeyEvent.ACTION_DOWN, a))
        val released = streamView.onKeyPreIme(a, keyboardKey(KeyEvent.ACTION_UP, a))
        val multiple = streamView.onKeyPreIme(a, keyboardKey(KeySpec(ACTION_NONE, a)))

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(multiple).isFalse()
        assertThat(sentKeys())
            .containsExactly(
                SentKey(0x8041, down, 0, NON_NORMALIZED),
                SentKey(0x8041, up, 0, NON_NORMALIZED),
            )
            .inOrder()
    }

    @Test
    fun `a key that the stream does not handle is the activity's, as the key of a plain activity`() {
        rig.streaming()
        forgetCalls()

        val pressed =
            rig.game.onKeyDown(
                KeyEvent.KEYCODE_VOLUME_UP,
                keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
            )
        val released =
            rig.game.onKeyUp(
                KeyEvent.KEYCODE_VOLUME_UP,
                keyboardKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP),
            )
        val handled =
            rig.game.onKeyDown(
                KeyEvent.KEYCODE_A,
                keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A),
            )

        assertThat(pressed).isFalse()
        assertThat(released).isFalse()
        assertThat(handled).isTrue()
    }

    private fun mouseBackOf(device: InputDevice, action: Int): KeyEvent =
        keyEvent(
                KeySpec(
                    action,
                    KeyEvent.KEYCODE_BACK,
                    source = InputDevice.SOURCE_MOUSE,
                    deviceId = device.id,
                )
            )
            .also { Shadow.extract<ShadowInputEvent>(it).setDevice(device) }

    private companion object {
        const val ACTION_NONE = 99
        const val NO_BUTTONS = 0
        const val PAD_ID = 31

        /**
         * The keys of the host that Ctrl, Alt and Shift make: they go down, then come up, in pairs.
         */
        val MODIFIERS_ONLY = listOf(0x80A2, 0x80A4, 0x80A0, 0x80A0, 0x80A4, 0x80A2)
    }
}
