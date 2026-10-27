package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.session
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The entry points of the motion path: what the views of the stream report to the activity, and
 * what the activity does before it looks at the event (the unbuffered dispatch of a touch) and
 * after it (the answer of the activity to what it did not take).
 */
internal class MotionEntryPointsTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT

    private fun press() = mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, MotionEvent.BUTTON_PRIMARY)

    /** Whether a touch asked the system to deliver the next events unbuffered, and forgets it. */
    private fun unbufferedDispatchRequested(): Boolean {
        val info = ReflectionHelpers.getField<Any>(streamView, "mAttachInfo")
        val requested = ReflectionHelpers.getField<Boolean>(info, "mUnbufferedDispatchRequested")
        ReflectionHelpers.setField(info, "mUnbufferedDispatchRequested", false)
        return requested
    }

    // ----- What the views report -----

    @Test
    fun `the stream view reports the generic motion of its events to the activity`() {
        streaming()

        val handled = streamView.dispatchGenericMotionEvent(press())

        assertThat(handled).isTrue()
        assertThat(sent()).containsExactly(position(10, 20), press(left)).inOrder()
    }

    @Test
    fun `the background view reports its touches to the activity, and the stream view does not`() {
        streaming()
        rig.session.setTouchMode(TouchMode.DIRECT)
        val down = fingerEvent(MotionEvent.ACTION_DOWN, listOf(100f to 200f))
        val up = fingerEvent(MotionEvent.ACTION_UP, listOf(100f to 200f))

        assertThat(streamView.dispatchTouchEvent(down)).isFalse()
        assertThat(sent()).isEmpty()
        assertThat(backgroundView.dispatchTouchEvent(down)).isTrue()
        assertThat(backgroundView.dispatchTouchEvent(up)).isTrue()

        // The touch is the background view's: its coordinates are moved by the stream view's
        // origin.
        assertThat(sent()).containsExactly(position(100, 55), press(left)).inOrder()
    }

    @Test
    fun `the stream view reports a captured pointer to the activity as a move of the mouse`() {
        streaming()
        val event =
            motionEventOf(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_MOVE) {
                pointer(5.9f, -6.9f)
            }

        val handled = capturedPointerListener().onCapturedPointer(streamView, event)

        assertThat(handled).isTrue()
        assertThat(sent()).containsExactly(moveBy(5, -6))
    }

    // ----- The unbuffered dispatch -----

    @Test
    fun `a touch down asks the system for unbuffered input`() {
        streaming()
        val down = fingerEvent(MotionEvent.ACTION_DOWN, listOf(100f to 50f))

        rig.game.onTouch(streamView, down)

        assertThat(unbufferedDispatchRequested()).isTrue()
    }

    @Test
    fun `a touch that is not a touch down does not ask for it`() {
        streaming()
        rig.game.onTouch(streamView, fingerEvent(MotionEvent.ACTION_DOWN, listOf(100f to 50f)))
        unbufferedDispatchRequested()

        rig.game.onTouch(streamView, fingerEvent(MotionEvent.ACTION_MOVE, listOf(110f to 50f)))
        assertThat(unbufferedDispatchRequested()).isFalse()
        rig.game.onTouch(streamView, fingerEvent(MotionEvent.ACTION_UP, listOf(110f to 50f)))

        assertThat(unbufferedDispatchRequested()).isFalse()
    }

    @Test
    fun `the touch is asked for unbuffered input even when the activity ignores it`() {
        streaming()
        rig.session.setTouchMode(TouchMode.OFF)

        val handled =
            rig.game.onTouch(streamView, fingerEvent(MotionEvent.ACTION_DOWN, listOf(1f to 1f)))

        assertThat(handled).isTrue()
        assertThat(unbufferedDispatchRequested()).isTrue()
    }

    // ----- The answer of the activity -----

    @Test
    fun `the activity takes what its views did not take, and leaves the rest to the system`() {
        streaming()

        val taken = rig.game.onGenericMotionEvent(press())
        val keyboard =
            motionEventOf(InputDevice.SOURCE_KEYBOARD, MotionEvent.ACTION_MOVE) {
                pointer(1f, 1f, MotionEvent.TOOL_TYPE_UNKNOWN)
            }
        val untaken = rig.game.onGenericMotionEvent(keyboard)

        assertThat(taken).isTrue()
        assertThat(untaken).isFalse()
    }

    @Test
    fun `a joystick that reaches the activity itself is taken like one that reaches the view`() {
        streaming()
        val joystick =
            motionEventOf(InputDevice.SOURCE_JOYSTICK, MotionEvent.ACTION_MOVE) {
                pointer(0f, 0f, MotionEvent.TOOL_TYPE_UNKNOWN, mapOf(Axes.Y to -1f))
            }

        assertThat(rig.game.onGenericMotionEvent(joystick)).isTrue()

        // Up on the stick is down on Android's Y axis, and the packet has it up.
        val packet = sent().single()
        assertThat(packet.name).isEqualTo("sendMultiControllerInput")
        assertThat(packet.args[6]).isEqualTo(32766.toShort())
    }
}
