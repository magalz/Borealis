package io.github.magalz.borealis.stream.game.keys

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.KeyboardPacket
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.game.GRAB_DELAY_MS
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * What the activity does when the stream chrome takes the input and when it gives it back: it
 * releases what the user holds, forgets the modifiers, lets go of the pointer and of the gamepad,
 * and takes them back, in this order, only when the user had them.
 */
internal class GameChromeInputTest : GameTestBase() {
    private val down = KeyboardPacket.KEY_DOWN
    private val up = KeyboardPacket.KEY_UP

    private fun sent(code: Int, direction: Byte, modifiers: Int = 0) =
        SentKey(code, direction, modifiers.toByte(), NON_NORMALIZED)

    // The keys that the user holds

    @Test
    fun `taking the input releases the keys held down, the last pressed first`() {
        rig.streaming()
        rig.down(KeyEvent.KEYCODE_A)
        rig.down(KeyEvent.KEYCODE_B)
        rig.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.up(KeyEvent.KEYCODE_B)
        forgetCalls()

        rig.openChrome()

        // The release carries no modifiers and no flag: it is the activity's, not a key of the
        // layout.
        assertThat(sentKeys())
            .containsExactly(SentKey(0x80A0, up, 0, 0), SentKey(0x8041, up, 0, 0))
            .inOrder()
    }

    @Test
    fun `a key pressed again counts at its latest press, and a key that came up is not released`() {
        rig.streaming()
        rig.down(KeyEvent.KEYCODE_A)
        rig.down(KeyEvent.KEYCODE_B)
        rig.up(KeyEvent.KEYCODE_A)
        rig.down(KeyEvent.KEYCODE_A)
        rig.down(KeyEvent.KEYCODE_C)
        rig.up(KeyEvent.KEYCODE_C)
        forgetCalls()

        rig.openChrome()

        assertThat(sentKeys())
            .containsExactly(SentKey(0x8041, up, 0, 0), SentKey(0x8042, up, 0, 0))
            .inOrder()
    }

    @Test
    fun `taking the input with no key held sends no key`() {
        rig.streaming()
        rig.tap(KeyEvent.KEYCODE_A)
        forgetCalls()

        rig.openChrome()

        assertThat(sentKeys()).isEmpty()
    }

    @Test
    fun `a key that went down is released once, not again when the chrome takes the input a second time`() {
        rig.streaming()
        rig.down(KeyEvent.KEYCODE_A)
        rig.openChrome()
        rig.closeChrome()
        forgetCalls()

        rig.openChrome()

        assertThat(sentKeys()).isEmpty()
    }

    // The modifier state

    @Test
    fun `taking the input forgets the modifier state, so no key goes on with a stale Shift`() {
        rig.streaming()
        rig.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.down(KeyEvent.KEYCODE_CTRL_RIGHT)
        rig.openChrome()
        rig.closeChrome()
        forgetCalls()

        rig.tap(KeyEvent.KEYCODE_A)

        assertThat(sentKeys()).containsExactly(sent(0x8041, down), sent(0x8041, up)).inOrder()
    }

    @Test
    fun `taking the input forgets a chord that waits, so the next keys are not eaten for it`() {
        rig.streaming()
        rig.holdCtrlAltShift()
        rig.down(KeyEvent.KEYCODE_Z)
        rig.openChrome()
        rig.closeChrome()
        forgetCalls()

        // A modifier would be eaten while a chord waits for the modifiers to come up.
        val shiftDown = rig.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.up(KeyEvent.KEYCODE_SHIFT_LEFT)
        rig.tap(KeyEvent.KEYCODE_B)
        // And the chord would toggle the grab when the modifiers were up.
        idle(250)

        assertThat(shiftDown).isTrue()
        assertThat(sentKeys().map { it.code })
            .containsExactly(0x80A0, 0x80A0, 0x8042, 0x8042)
            .inOrder()
        assertThat(rig.pointerCaptured).isTrue()
    }

    // The pointer

    @Test
    fun `taking the input lets the pointer go and shows the cursor`() {
        rig.streaming()
        val capture = rig.recordCapture(capturing = true)

        rig.openChrome()

        assertThat(capture.calls).containsExactly("disableCapture", "showCursor").inOrder()
        assertThat(rig.pointerCaptured).isFalse()
    }

    @Test
    fun `giving the input back grabs the pointer again when the user had it grabbed`() {
        rig.streaming()
        rig.openChrome()
        val capture = rig.recordCapture()

        rig.closeChrome()

        assertThat(capture.calls).containsExactly("enableCapture", "hideCursor").inOrder()
        assertThat(rig.pointerCaptured).isTrue()
        forgetCalls()
        assertThat(rig.tap(KeyEvent.KEYCODE_A)).containsExactly(true, true)
        assertThat(sentKeys()).hasSize(2)
    }

    @Test
    fun `giving the input back grabs only when the user had it grabbed before`() {
        rig.streaming()
        // The user let the input go with the chord.
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        assertThat(rig.pointerCaptured).isFalse()
        rig.openChrome()
        val capture = rig.recordCapture()

        rig.closeChrome()

        assertThat(capture.calls).isEmpty()
        assertThat(rig.pointerCaptured).isFalse()
        forgetCalls()
        assertThat(rig.tap(KeyEvent.KEYCODE_A)).containsExactly(false, false)
        assertThat(sentCalls()).isEmpty()
    }

    @Test
    fun `the chrome that takes the input again remembers the grab that the user had the second time`() {
        rig.streaming()
        rig.openChrome()
        rig.closeChrome()
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        assertThat(rig.pointerCaptured).isFalse()

        // The second time, the input is not grabbed, and the chrome gives it back as it took it.
        rig.openChrome()
        rig.closeChrome()
        assertThat(rig.pointerCaptured).isFalse()

        // The user grabs it again, and the chrome gives it back grabbed.
        rig.chord(KeyEvent.KEYCODE_Z)
        idle(250)
        rig.openChrome()
        rig.closeChrome()
        assertThat(rig.pointerCaptured).isTrue()
    }

    @Test
    fun `a chrome that gives the input back before the connection is up does not grab`() {
        rig.create()
        // The session streams without the activity having seen the connection start.
        rig.session.onConnectionStarted(SESSION)
        rig.firstFrame()

        idle()

        assertThat(rig.pointerCaptured).isFalse()

        // The connection starts: the grab that it asks for after 500 ms is not refused.
        rig.game.connectionStarted()
        idle(GRAB_DELAY_MS)

        assertThat(rig.pointerCaptured).isTrue()
    }

    // The gamepad

    @Test
    fun `the chrome takes and gives back the gamepad`() {
        rig.streaming()
        rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.A_FLAG)
        forgetCalls()

        rig.openChrome()

        // The pad that the host knows is left with no button down.
        assertThat(sentPadButtons()).containsExactly(0)
        forgetCalls()

        rig.closeChrome()
        rig.game.handleKeyDown(padKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))

        // The pad is the stream's again.
        assertThat(sentPadButtons()).containsExactly(ControllerPacket.B_FLAG)
    }

    @Test
    fun `the gamepad gives the gamepad back before the pointer is grabbed again`() {
        rig.streaming()
        rig.openChrome()
        val capture = rig.recordCapture()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        var padWasBack: Boolean? = null
        capture.onEnable = {
            // The pad handler answers a state report with a packet only when it is not captured.
            val before = sentPadButtons().size
            handler.reportOscState(0, 0, 0, 0, 0, 0, 0)
            padWasBack = sentPadButtons().size > before
        }

        rig.closeChrome()

        assertThat(capture.calls).containsExactly("enableCapture", "hideCursor").inOrder()
        assertThat(padWasBack).isTrue()
    }

    private companion object {
        val SESSION =
            SessionInfo(
                width = 1920,
                height = 1080,
                frameRate = 60,
                codec = "H.264",
                hdr = false,
                decoder = null,
                bitrateLimitKbps = 0,
            )
    }
}
