package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test

/**
 * A host that does not take pen events: the activity tries each pen event once and, when the host
 * answers that it is not supported, treats the stylus as a mouse. A stylus is the left button, an
 * eraser the right button, and the pointer keeps away from the place where a stylus just touched.
 */
internal class PenFallbackTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT
    private val right = MouseButtonPacket.BUTTON_RIGHT

    private fun hostWithoutPens() {
        ShadowRecordingBridge.intResult = MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun tool(
        toolType: Int,
        action: Int,
        x: Float,
        y: Float,
        build: MotionSpec.() -> Unit = {},
    ) =
        motionEventOf(InputDevice.SOURCE_STYLUS, action) {
            pointer(x, y, toolType)
            build()
        }

    private fun stylus(action: Int, x: Float = 100f, y: Float = 100f, at: Long = TIME) =
        tool(MotionEvent.TOOL_TYPE_STYLUS, action, x, y) { eventTime = at }

    private fun eraser(action: Int, x: Float = 100f, y: Float = 100f, at: Long = TIME) =
        tool(MotionEvent.TOOL_TYPE_ERASER, action, x, y) { eventTime = at }

    /** What the activity does with [event] besides asking the host for a pen: a call or none. */
    private fun mouseCallsOf(event: MotionEvent): List<Sent> {
        forgetSent()
        assertThat(rig.game.onGenericMotion(streamView, event)).isTrue()
        return sentBesidesPens()
    }

    // ----- One try for each event -----

    @Test
    fun `every event is offered to the host as a pen first, and the host is asked again each time`() {
        hostWithoutPens()
        streaming()

        rig.game.onGenericMotion(streamView, stylus(MotionEvent.ACTION_HOVER_MOVE))
        rig.game.onGenericMotion(
            streamView,
            stylus(MotionEvent.ACTION_HOVER_MOVE, at = TIME + 1000),
        )

        assertThat(pens()).hasSize(2)
        assertThat(sentBesidesPens()).containsExactly(position(100, 100), position(100, 100))
    }

    @Test
    fun `a move of two styluses is tried once, and the host that says no is not asked again`() {
        hostWithoutPens()
        streaming()
        val move =
            motionEventOf(InputDevice.SOURCE_STYLUS, MotionEvent.ACTION_MOVE) {
                pointer(100f, 100f, MotionEvent.TOOL_TYPE_STYLUS)
                pointer(200f, 100f, MotionEvent.TOOL_TYPE_STYLUS)
                eventTime = TIME
            }

        val calls = mouseCallsOf(move)

        assertThat(pens()).hasSize(1)
        assertThat(calls).containsExactly(position(100, 100))
    }

    // ----- Stylus and eraser as buttons -----

    @Test
    fun `a stylus that touches is the left button, and goes up with it`() {
        hostWithoutPens()
        streaming()

        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_DOWN)))
            .containsExactly(position(100, 100), press(left))
            .inOrder()
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_UP, at = TIME + 500)))
            .containsExactly(position(100, 100), release(left))
            .inOrder()
    }

    @Test
    fun `an eraser that touches is the right button, and goes up with it`() {
        hostWithoutPens()
        streaming()

        assertThat(mouseCallsOf(eraser(MotionEvent.ACTION_DOWN)))
            .containsExactly(position(100, 100), press(right))
            .inOrder()
        assertThat(mouseCallsOf(eraser(MotionEvent.ACTION_UP, at = TIME + 500)))
            .containsExactly(position(100, 100), release(right))
            .inOrder()
    }

    @Test
    fun `a cancel of the gesture lets the stylus button go too`() {
        hostWithoutPens()
        streaming()

        val calls = mouseCallsOf(stylus(MotionEvent.ACTION_CANCEL, at = TIME + 500))

        assertThat(calls).containsExactly(position(100, 100), release(left)).inOrder()
        // The host was asked for a cancel of every pen before.
        assertThat(pens().single().type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL.toInt())
    }

    @Test
    fun `a stylus that hovers or moves with no button down is a pointer and nothing else`() {
        hostWithoutPens()
        streaming()

        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_MOVE, 30f, 40f)))
            .containsExactly(position(30, 40))
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 50f, 60f, at = TIME + 500)))
            .containsExactly(position(50, 60))
    }

    @Test
    fun `the buttons of the stylus come before the touch of the stylus`() {
        hostWithoutPens()
        streaming()
        val down =
            tool(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 100f, 100f) {
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY
            }

        // The primary button of the stylus is the right button; the touch is the left one.
        assertThat(mouseCallsOf(down))
            .containsExactly(position(100, 100), press(right), press(left))
            .inOrder()
    }

    @Test
    fun `a stylus among other pointers is not a button, the touch is the one of a stylus alone`() {
        hostWithoutPens()
        streaming()
        fun twoStyluses(action: Int, index: Int) =
            motionEventOf(InputDevice.SOURCE_STYLUS, action) {
                pointer(100f, 100f, MotionEvent.TOOL_TYPE_STYLUS)
                pointer(200f, 100f, MotionEvent.TOOL_TYPE_STYLUS)
                actionIndex = index
                eventTime = TIME
            }

        // A second pointer goes down; and the gesture of both is cancelled, which is the lift of a
        // stylus only when it is alone.
        assertThat(mouseCallsOf(twoStyluses(MotionEvent.ACTION_POINTER_DOWN, 1)))
            .containsExactly(position(100, 100))
        assertThat(mouseCallsOf(twoStyluses(MotionEvent.ACTION_CANCEL, 0)))
            .containsExactly(position(100, 100))
    }

    // ----- The dead zone after a touch down -----

    @Test
    fun `a stylus that has just touched down does not move the pointer for a hundred milliseconds within 20 pixels`() {
        hostWithoutPens()
        streaming()
        mouseCallsOf(stylus(MotionEvent.ACTION_DOWN))

        // Inside: the time is 100 and the distance 20, both limits included.
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 120f, 100f, TIME + 100))).isEmpty()
        // The distance is the straight one: 19.8 is inside and 21.2 is outside.
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 114f, 114f, TIME + 100))).isEmpty()
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 115f, 115f, TIME + 100)))
            .containsExactly(position(115, 115))
        // Outside: a millisecond later, or a pixel further.
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 120f, 100f, TIME + 101)))
            .containsExactly(position(120, 100))
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_MOVE, 121f, 100f, TIME + 100)))
            .containsExactly(position(121, 100))
    }

    @Test
    fun `the lift of a stylus in the dead zone of its touch sends the button but not the position`() {
        hostWithoutPens()
        streaming()
        mouseCallsOf(stylus(MotionEvent.ACTION_DOWN))

        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_UP, 110f, 100f, TIME + 50)))
            .containsExactly(release(left))
    }

    @Test
    fun `the dead zone of a touch is the same for an eraser, and does not hold a hover`() {
        hostWithoutPens()
        streaming()
        mouseCallsOf(eraser(MotionEvent.ACTION_DOWN))

        assertThat(mouseCallsOf(eraser(MotionEvent.ACTION_MOVE, 110f, 100f, TIME + 50))).isEmpty()
        // A hover is held by the dead zone of a lift, not by the one of a touch.
        assertThat(mouseCallsOf(eraser(MotionEvent.ACTION_HOVER_MOVE, 110f, 100f, TIME + 50)))
            .containsExactly(position(110, 100))
    }

    // ----- The dead zone after a lift -----

    private fun touchAndLift() {
        mouseCallsOf(stylus(MotionEvent.ACTION_DOWN))
        mouseCallsOf(stylus(MotionEvent.ACTION_UP, at = TIME + 500))
    }

    @Test
    fun `a stylus that has just lifted does not place the pointer for 150 milliseconds within 50 pixels`() {
        hostWithoutPens()
        streaming()
        touchAndLift()
        val lifted = TIME + 500

        // Inside: the time is 150 and the distance 50, both limits included.
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_ENTER, 150f, 100f, lifted + 150)))
            .isEmpty()
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_MOVE, 100f, 50f, lifted + 150)))
            .isEmpty()
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_EXIT, 100f, 150f, lifted + 150)))
            .isEmpty()
        // Outside: a millisecond later, or a pixel further.
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_MOVE, 150f, 100f, lifted + 151)))
            .containsExactly(position(150, 100))
        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_HOVER_MOVE, 151f, 100f, lifted + 150)))
            .containsExactly(position(151, 100))
    }

    @Test
    fun `a stylus that touches down in the dead zone of its lift presses the button where the pointer is`() {
        hostWithoutPens()
        streaming()
        touchAndLift()

        assertThat(mouseCallsOf(stylus(MotionEvent.ACTION_DOWN, 130f, 100f, TIME + 600)))
            .containsExactly(press(left))
    }

    @Test
    fun `a mouse has no dead zone, whatever the stylus did before`() {
        hostWithoutPens()
        streaming()
        mouseCallsOf(stylus(MotionEvent.ACTION_DOWN))

        val calls = mouseCallsOf(mouseEvent(MotionEvent.ACTION_MOVE, 105f, 100f))

        assertThat(calls).containsExactly(position(105, 100))
    }

    private companion object {
        const val TIME = 10_000L
    }
}
