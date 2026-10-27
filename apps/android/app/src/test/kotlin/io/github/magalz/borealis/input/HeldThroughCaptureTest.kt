package io.github.magalz.borealis.input

import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A button that went down while the chrome had the input, or before it, does nothing once the input
 * is back until it has been let go: closing a panel with the chord must not toggle the gamepad
 * mouse, and must not start the chord again while the buttons are still held.
 */
@RunWith(RobolectricTestRunner::class)
class HeldThroughCaptureTest {
    @get:Rule val rig = ControllerHandlerRig()
    // Made on first use: a connected USB pad is what isMouseEmulationOnAnyPad reads, not the
    // default context
    private val pad by lazy {
        FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    }
    private val chord = PLAY_FLAG or BACK_FLAG

    /** Takes the capture and gives it back with [held] still down; returns the time it ended. */
    private fun captureWhileHolding(held: () -> Unit): Long {
        rig.handler.setCaptured(true)
        held()
        rig.advanceBy(1_100)
        rig.handler.setCaptured(false)
        return rig.now
    }

    @Test
    fun `a USB pad whose Start went down while captured does not toggle the mouse when it is let go`() {
        captureWhileHolding { pad.report(chord) }

        rig.advanceBy(800)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `a USB pad that keeps Select and Start down after the capture does not start the chord again`() {
        captureWhileHolding { pad.report(chord) }

        pad.report(chord)
        rig.advanceBy(2_000)
        pad.report(PLAY_FLAG)
        rig.advanceBy(2_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a USB pad sends nothing to the PC for buttons held through the capture`() {
        captureWhileHolding { pad.report(chord) }
        val packets = rig.connection.packets.size

        pad.report(chord)
        pad.report(PLAY_FLAG)
        pad.report(0)

        assertThat(rig.connection.packets.drop(packets).map { it.buttons }.filter { it != 0 })
            .isEmpty()
    }

    @Test
    fun `a USB pad presses again after the release, for the chord and for the Start hold`() {
        captureWhileHolding { pad.report(chord) }
        pad.report(0)

        pad.report(chord)
        rig.advanceBy(1_000)
        assertThat(rig.chords).isEqualTo(1)
        pad.report(0)

        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        pad.report(0)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()
    }

    /** What the window sends for a key that went down at [downTime] and is still held. */
    private fun repeat(keyCode: Int, downTime: Long, count: Int = 1) =
        rig.handler.handleButtonDown(
            rig.keyEvent(downTime, rig.now, KeyEvent.ACTION_DOWN, keyCode, count)
        )

    @Test
    fun `a system pad that repeats Select and Start held through the capture does not start the chord`() {
        val downTime = rig.now
        captureWhileHolding {}

        repeat(KEYCODE_BUTTON_SELECT, downTime)
        repeat(KEYCODE_BUTTON_START, downTime)
        rig.advanceBy(1_500)
        repeat(KEYCODE_BUTTON_SELECT, downTime, 2)
        repeat(KEYCODE_BUTTON_START, downTime, 2)
        rig.advanceBy(1_500)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a system pad that lets go of Select and keeps repeating Start does nothing`() {
        val downTime = rig.now
        captureWhileHolding {}

        repeat(KEYCODE_BUTTON_START, downTime)
        rig.advanceBy(1_500)
        repeat(KEYCODE_BUTTON_START, downTime, 2)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `a system pad presses again after the release, for the chord and for the Start hold`() {
        rig.useSystemPad()
        val downTime = rig.now
        captureWhileHolding {}
        repeat(KEYCODE_BUTTON_START, downTime)
        rig.release(KEYCODE_BUTTON_START)
        rig.release(KEYCODE_BUTTON_SELECT)

        rig.press(KEYCODE_BUTTON_START)
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(1_000)
        assertThat(rig.chords).isEqualTo(1)
        rig.release(KEYCODE_BUTTON_START)
        rig.release(KEYCODE_BUTTON_SELECT)

        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(800)
        rig.release(KEYCODE_BUTTON_START)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()
    }
}
