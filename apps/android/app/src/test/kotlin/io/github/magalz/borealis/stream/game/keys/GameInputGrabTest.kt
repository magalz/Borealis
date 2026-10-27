package io.github.magalz.borealis.stream.game.keys

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.KeyboardPacket
import io.github.magalz.borealis.stream.game.GRAB_DELAY_MS
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.Test

/**
 * The grab of the input: the pointer capture and the cursor that follow it, the keys that the host
 * gets only while the input is grabbed, and the rule that nothing grabs while the chrome is modal.
 */
internal class GameInputGrabTest : GameTestBase() {
    private fun sent(code: Int, direction: Byte) = SentKey(code, direction, 0, NON_NORMALIZED)

    @Test
    fun `nothing grabs the input until the first frame, because the chrome covers the stream`() {
        rig.create()
        rig.game.connectionStarted()

        // The grab that the connection asks for after 500 ms finds the chrome modal.
        idle(GRAB_DELAY_MS)

        assertThat(rig.pointerCaptured).isFalse()
        assertThat(rig.cursorShown).isTrue()

        rig.firstFrame()
        idle()

        // The chrome gives the input back and the pointer is grabbed.
        assertThat(rig.pointerCaptured).isTrue()
        assertThat(rig.cursorShown).isFalse()
        forgetCalls()
        assertThat(rig.tap(KeyEvent.KEYCODE_A)).containsExactly(true, true)
        assertThat(sentKeys())
            .containsExactly(
                sent(0x8041, KeyboardPacket.KEY_DOWN),
                sent(0x8041, KeyboardPacket.KEY_UP),
            )
            .inOrder()
    }

    @Test
    fun `grabbing the input captures the pointer, which hides the cursor`() {
        rig.streaming()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        val capture = rig.recordCapture()

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(capture.calls).containsExactly("enableCapture", "hideCursor").inOrder()
        assertThat(rig.pointerCaptured).isTrue()
    }

    @Test
    fun `grabbing the input shows the cursor again when the user asked for it, after the capture hid it`() {
        rig.streaming()
        rig.chord(KeyEvent.KEYCODE_C)
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        val capture = rig.recordCapture()

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(capture.calls)
            .containsExactly("enableCapture", "hideCursor", "showCursor")
            .inOrder()
    }

    @Test
    fun `letting the input go releases the pointer and shows the cursor`() {
        rig.streaming()
        val capture = rig.recordCapture()

        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)

        assertThat(capture.calls).containsExactly("disableCapture", "showCursor").inOrder()
        assertThat(rig.pointerCaptured).isFalse()
    }

    @Test
    fun `the cursor chord only moves the cursor while the input is grabbed`() {
        rig.streaming()
        val capture = rig.recordCapture()

        rig.chord(KeyEvent.KEYCODE_C)
        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(capture.calls).containsExactly("showCursor", "hideCursor").inOrder()
    }

    @Test
    fun `the cursor chord grabs the input first when it was let go`() {
        rig.streaming()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        val capture = rig.recordCapture()

        rig.chord(KeyEvent.KEYCODE_C)

        assertThat(capture.calls)
            .containsExactly("enableCapture", "hideCursor", "showCursor")
            .inOrder()
    }

    @Test
    fun `a toggle that comes while the chrome is modal does not grab, and the chrome grabs when it ends`() {
        rig.streaming()
        // The toggle is asked for, and the menu opens before it runs.
        rig.chord(KeyEvent.KEYCODE_Z)
        rig.openChrome()
        val capture = rig.recordCapture()

        idle(250)

        // The toggle would grab (the chrome let the input go), and the modal chrome refuses.
        assertThat(capture.calls).isEmpty()
        assertThat(rig.pointerCaptured).isFalse()

        rig.closeChrome()

        // The input was grabbed when the chrome took it, so the chrome gives it back grabbed.
        assertThat(capture.calls).containsExactly("enableCapture", "hideCursor").inOrder()
        assertThat(rig.pointerCaptured).isTrue()
    }

    @Test
    fun `the window focus clears the modifiers that were held and tells the capture provider`() {
        rig.streaming()
        val capture = rig.recordCapture()
        rig.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.down(KeyEvent.KEYCODE_CTRL_LEFT)
        forgetCalls()

        rig.game.onWindowFocusChanged(false)
        rig.game.onWindowFocusChanged(true)
        rig.tap(KeyEvent.KEYCODE_A)

        // A modifier that came up while the window had no focus is not held any more.
        assertThat(sentKeys())
            .containsExactly(
                sent(0x8041, KeyboardPacket.KEY_DOWN),
                sent(0x8041, KeyboardPacket.KEY_UP),
            )
            .inOrder()
        assertThat(capture.calls).containsExactly("focus false", "focus true").inOrder()
    }
}
