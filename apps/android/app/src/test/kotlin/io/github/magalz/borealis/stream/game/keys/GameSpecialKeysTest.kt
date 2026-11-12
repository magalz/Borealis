package io.github.magalz.borealis.stream.game.keys

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.KeyboardPacket
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.recordSession
import org.junit.Test

/**
 * The chords of Ctrl, Alt and Shift with Z, Q, C and M: the client keeps them for itself (the grab,
 * the disconnect, the cursor and the menu) and eats the key, and while it waits for the modifiers
 * to come up it eats every key down. A key up passes, except that of a key whose key down it ate:
 * the host never saw that key go down, so it does not see it come up either.
 */
internal class GameSpecialKeysTest : GameTestBase() {
    private val down = KeyboardPacket.KEY_DOWN
    private val up = KeyboardPacket.KEY_UP
    private val ctrl = KeyboardPacket.MODIFIER_CTRL.toInt()
    private val alt = KeyboardPacket.MODIFIER_ALT.toInt()
    private val shift = KeyboardPacket.MODIFIER_SHIFT.toInt()

    private fun sent(code: Int, direction: Byte, modifiers: Int) =
        SentKey(code, direction, modifiers.toByte(), NON_NORMALIZED)

    private fun streamingAndCapturing() {
        rig.streaming()
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.cursorShown).isFalse()
        forgetCalls()
    }

    // Z: the grab

    @Test
    fun `Ctrl Alt Shift and Z eat the Z, let the modifiers through and toggle the grab 250 ms later`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()

        val pressed = rig.down(KeyEvent.KEYCODE_Z)
        val released = rig.up(KeyEvent.KEYCODE_Z)
        rig.releaseCtrlAltShift()

        // The key is the client's, so the host never sees it; the modifiers it does see, in pairs.
        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(sentKeys())
            .containsExactly(
                sent(0x80A2, down, ctrl),
                sent(0x80A4, down, ctrl or alt),
                sent(0x80A0, down, ctrl or alt or shift),
                sent(0x80A0, up, ctrl or alt),
                sent(0x80A4, up, ctrl),
                sent(0x80A2, up, 0),
            )
            .inOrder()
        // The toggle waits 250 ms.
        idle(249)
        assertThat(rig.pointerCaptured).isTrue()
        idle(1)
        assertThat(rig.pointerCaptured).isFalse()
        assertThat(rig.cursorShown).isTrue()
    }

    @Test
    fun `while the grab is off the keys are not the host's, and the same chord grabs again`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        forgetCalls()

        val pressed = rig.down(KeyEvent.KEYCODE_A)
        val released = rig.up(KeyEvent.KEYCODE_A)

        // The keys go on to whatever else wants them: nothing is sent.
        assertThat(pressed).isFalse()
        assertThat(released).isFalse()
        assertThat(sentCalls()).isEmpty()

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        forgetCalls()

        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.cursorShown).isFalse()
        assertThat(rig.tap(KeyEvent.KEYCODE_A)).containsExactly(true, true)
        assertThat(sentKeys()).containsExactly(sent(0x8041, down, 0), sent(0x8041, up, 0)).inOrder()
    }

    @Test
    fun `the keys of the chord come up on the right hand too, and the modifiers can come up in any order`() {
        streamingAndCapturing()
        rig.down(KeyEvent.KEYCODE_SHIFT_RIGHT)
        rig.down(KeyEvent.KEYCODE_ALT_RIGHT)
        rig.down(KeyEvent.KEYCODE_CTRL_RIGHT)
        forgetCalls()

        val pressed = rig.down(KeyEvent.KEYCODE_Z)
        rig.up(KeyEvent.KEYCODE_CTRL_RIGHT)
        rig.up(KeyEvent.KEYCODE_SHIFT_RIGHT)
        // Not yet: Alt is down.
        idle(250)
        assertThat(rig.pointerCaptured).isTrue()
        rig.up(KeyEvent.KEYCODE_ALT_RIGHT)
        idle(250)

        assertThat(pressed).isTrue()
        assertThat(rig.pointerCaptured).isFalse()
    }

    @Test
    fun `the key that armed the chord comes up after it ran, and the host does not see that key up either`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        rig.releaseCtrlAltShift()
        forgetCalls()

        // The chord ended with the last modifier, but the host never saw this Z go down.
        val released = rig.up(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(released).isTrue()
        assertThat(rig.pointerCaptured).isFalse()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `the chord key that repeats after the chord ran keeps its key up eaten`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        rig.releaseCtrlAltShift()

        // The Z is still held and repeats, and no chord waits now: a repeat is not a new key down.
        rig.game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, repeat = 1))
        forgetCalls()
        val released = rig.up(KeyEvent.KEYCODE_Z)

        assertThat(released).isTrue()
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `a stray key up of the chord key after a whole chord goes to the host`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_Z)
        forgetCalls()

        // The chord took the key up of its Z, and nothing else is eaten for it.
        rig.up(KeyEvent.KEYCODE_Z)

        assertThat(sentKeys()).containsExactly(sent(0x805A, up, 0))
    }

    @Test
    fun `the chord is still set while its action runs, and reset when it is done`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        val capture = rig.recordCapture()
        var keyDuringAction: Int? = null
        var waitingDuringAction: Boolean? = null
        capture.onEnable = {
            keyDuringAction = rig.field("specialKeyCode")
            waitingDuringAction = rig.field("waitingForAllModifiersUp")
        }

        // The cursor chord grabs the input first, which asks the capture provider.
        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(keyDuringAction).isEqualTo(KeyEvent.KEYCODE_C)
        assertThat(waitingDuringAction).isTrue()
        assertThat(rig.field<Int>("specialKeyCode")).isEqualTo(KeyEvent.KEYCODE_UNKNOWN)
        assertThat(rig.field<Boolean>("waitingForAllModifiersUp")).isFalse()
    }

    @Test
    fun `the grab toggle that waits for its time brings back the cursor that the user had shown`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_C)
        assertThat(rig.cursorShown).isTrue()

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        assertThat(rig.pointerCaptured).isFalse()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        // Capturing hides the cursor again, and the activity shows it, as the user asked.
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.cursorShown).isTrue()
    }

    // Q: disconnect

    @Test
    fun `Ctrl Alt Shift and Q disconnect through the session when the last modifier comes up`() {
        streamingAndCapturing()
        val actions = rig.recordSession()
        rig.holdCtrlAltShift()

        val pressed = rig.down(KeyEvent.KEYCODE_Q)
        val released = rig.up(KeyEvent.KEYCODE_Q)
        rig.up(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.up(KeyEvent.KEYCODE_ALT_LEFT)

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(actions.calls).isEmpty()
        rig.up(KeyEvent.KEYCODE_CTRL_LEFT)
        // The same flow as the Disconnect of the menu: the screen, the stop, the notice. The
        // activity is not finished here; the actions finish it when that flow is done.
        assertThat(rig.phase).isEqualTo(StreamPhase.Disconnecting)
        assertThat(actions.calls).containsExactly("disconnect Desktop")
        assertThat(rig.game.isFinishing).isFalse()
        assertThat(sentKeys().map { it.code }).doesNotContain(0x8051)
    }

    // M: the menu

    private fun panelOpen(): Panel = (rig.phase as StreamPhase.Streaming).panel

    @Test
    fun `Ctrl Alt Shift and M eat the M and open the menu when the last modifier comes up`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()

        val pressed = rig.down(KeyEvent.KEYCODE_M)
        val released = rig.up(KeyEvent.KEYCODE_M)
        rig.up(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.up(KeyEvent.KEYCODE_ALT_LEFT)

        assertThat(pressed).isTrue()
        assertThat(released).isTrue()
        assertThat(panelOpen()).isEqualTo(Panel.NONE)
        rig.up(KeyEvent.KEYCODE_CTRL_LEFT)
        idle()
        assertThat(panelOpen()).isEqualTo(Panel.MENU)
        // The M never reached the host, and the chrome took the input from the stream.
        assertThat(sentKeys().map { it.code }).doesNotContain(0x804D)
        assertThat(rig.pointerCaptured).isFalse()
    }

    @Test
    fun `Ctrl Alt Shift and M open the menu while the grab is off too`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        assertThat(rig.pointerCaptured).isFalse()
        forgetCalls()

        rig.chord(KeyEvent.KEYCODE_M)
        idle()

        assertThat(panelOpen()).isEqualTo(Panel.MENU)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the M of the chord that comes up after the menu opened does not reach the host`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_M)

        rig.releaseCtrlAltShift()
        idle()
        forgetCalls()

        rig.up(KeyEvent.KEYCODE_M)

        // The chrome has the keys now: the key of a press that began before is not the host's.
        assertThat(panelOpen()).isEqualTo(Panel.MENU)
        assertThat(sentCalls()).isEmpty()
    }

    // C: the cursor

    @Test
    fun `Ctrl Alt Shift and C show the cursor and hide it again, and keep the grab`() {
        streamingAndCapturing()

        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(rig.cursorShown).isTrue()
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(sentKeys().map { it.code }).doesNotContain(0x8043)

        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(rig.cursorShown).isFalse()
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(sentKeys().map { it.code }).doesNotContain(0x8043)
    }

    @Test
    fun `the cursor chord grabs the input when it was let go, and shows the cursor`() {
        streamingAndCapturing()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        assertThat(rig.pointerCaptured).isFalse()
        forgetCalls()

        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.cursorShown).isTrue()
        // The keys are the host's again.
        forgetCalls()
        assertThat(rig.tap(KeyEvent.KEYCODE_A)).containsExactly(true, true)
        assertThat(sentKeys()).hasSize(2)
    }

    // The keys that are not a chord

    @Test
    fun `a key that is not Z, Q, C or M goes to the host with the three modifiers`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        forgetCalls()

        // A, and the keys beside the Q and the M of the chords.
        val keys = listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_W)
        val handled = keys.flatMap { rig.tap(it) }

        assertThat(handled).containsExactly(true, true, true, true, true, true)
        assertThat(sentKeys())
            .containsExactly(
                sent(0x8041, down, ctrl or alt or shift),
                sent(0x8041, up, ctrl or alt or shift),
                sent(0x804E, down, ctrl or alt or shift),
                sent(0x804E, up, ctrl or alt or shift),
                sent(0x8057, down, ctrl or alt or shift),
                sent(0x8057, up, ctrl or alt or shift),
            )
            .inOrder()
        rig.releaseCtrlAltShift()
        idle(250)
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.game.isFinishing).isFalse()
        assertThat(panelOpen()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `Z and M with only two of the three modifiers are keys for the host`() {
        streamingAndCapturing()
        val pairs =
            listOf(
                listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT),
                listOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT),
                listOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                ),
            )

        for (modifiers in pairs) {
            modifiers.forEach { rig.down(it) }
            forgetCalls()

            val handled = rig.tap(KeyEvent.KEYCODE_Z) + rig.tap(KeyEvent.KEYCODE_M)

            assertThat(handled).containsExactly(true, true, true, true)
            assertThat(sentKeys().map { it.code }).containsExactly(0x805A, 0x805A, 0x804D, 0x804D)
            modifiers.reversed().forEach { rig.up(it) }
        }

        idle(250)
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(panelOpen()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the modifiers that come last do not start a chord for a key that is already down`() {
        streamingAndCapturing()
        rig.down(KeyEvent.KEYCODE_Q)
        forgetCalls()

        rig.holdCtrlAltShift()
        rig.up(KeyEvent.KEYCODE_Q)
        rig.releaseCtrlAltShift()

        // The Q went to the host, and no chord asked for the quit.
        assertThat(rig.game.isFinishing).isFalse()
        assertThat(sentKeys().map { it.code })
            .containsExactly(0x80A2, 0x80A4, 0x80A0, 0x8051, 0x80A0, 0x80A4, 0x80A2)
            .inOrder()
    }

    // While the chord waits

    @Test
    fun `while the chord waits for the modifiers, the keys down are eaten and so are their keys up`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        forgetCalls()

        val specialAgain = rig.down(KeyEvent.KEYCODE_Z)
        val otherDown = rig.down(KeyEvent.KEYCODE_B)
        val otherUp = rig.up(KeyEvent.KEYCODE_B)
        val specialUp = rig.up(KeyEvent.KEYCODE_Z)

        assertThat(specialAgain).isTrue()
        assertThat(otherDown).isTrue()
        assertThat(otherUp).isTrue()
        assertThat(specialUp).isTrue()
        // The host never saw the key down of the B, so it does not see its key up either.
        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `while the chord waits, the key up of a key that was down before the chord goes through`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_B)
        rig.down(KeyEvent.KEYCODE_Z)
        forgetCalls()

        rig.up(KeyEvent.KEYCODE_B)

        assertThat(sentKeys()).containsExactly(sent(0x8042, up, ctrl or alt or shift))
    }

    @Test
    fun `the repeat of a key that was down before the chord is not eaten for it, so its key up goes through`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_B)
        rig.down(KeyEvent.KEYCODE_Z)
        forgetCalls()

        // The host saw the first key down of the B; a repeat is not one.
        val repeated =
            rig.game.dispatchKeyEvent(
                keyboardKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B, repeat = 1)
            )
        assertThat(repeated).isTrue()
        assertThat(sentKeys()).isEmpty()
        rig.up(KeyEvent.KEYCODE_B)

        assertThat(sentKeys()).containsExactly(sent(0x8042, up, ctrl or alt or shift))
    }

    @Test
    fun `a key that goes down again forgets that its earlier key up was lost, so it reaches the host whole`() {
        streamingAndCapturing()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_C)
        rig.down(KeyEvent.KEYCODE_B)
        // The key up of the B never reaches the chord code: a virtual key goes to the system.
        rig.game.dispatchKeyEvent(
            keyboardKey(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_B,
                flags = KeyEvent.FLAG_VIRTUAL_HARD_KEY,
            )
        )
        rig.releaseCtrlAltShift()
        forgetCalls()

        rig.tap(KeyEvent.KEYCODE_B)

        // The key down of the new press is the host's, so its key up is too.
        assertThat(sentKeys()).containsExactly(sent(0x8042, down, 0), sent(0x8042, up, 0)).inOrder()
    }

    @Test
    fun `a focus change drops the chord that waits, so the next key does not do it`() {
        streamingAndCapturing()
        val actions = rig.recordSession()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Q)
        rig.up(KeyEvent.KEYCODE_Q)
        assertThat(actions.calls).isEmpty()

        // The modifiers came up while the window had no focus, so the activity does not know them.
        rig.game.onWindowFocusChanged(false)
        rig.up(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.down(KeyEvent.KEYCODE_B)
        rig.up(KeyEvent.KEYCODE_B)

        assertThat(actions.calls).isEmpty()
    }
}
