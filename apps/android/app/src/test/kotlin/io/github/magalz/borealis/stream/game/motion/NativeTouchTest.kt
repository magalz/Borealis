package io.github.magalz.borealis.stream.game.motion

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * The fingers in the Native touch mode: each pointer that goes down, moves, goes up or is cancelled
 * reaches the host as a touch event, with the place it has on the stream view normalized to 0..1,
 * and nothing reaches it as a mouse. A finger that the host took and that is still down when the
 * chrome takes the input, or the mode leaves Native, is cancelled on the host once. The other modes
 * send no touch events.
 */
internal class NativeTouchTest : MotionTestBase() {
    private fun native() = rig.session.setTouchMode(TouchMode.NATIVE)

    // Two fingers; the action is for the pointer [index].
    private fun twoFingers(action: Int, index: Int, build: MotionSpec.() -> Unit = {}) =
        fingerEvent(action, listOf(80f to 45f, 240f to 135f), index) {
            eventTime = TIME
            build()
        }

    // Three fingers; the action is for the pointer [index].
    private fun threeFingers(action: Int, index: Int, at: Long = TIME) =
        fingerEvent(action, listOf(80f to 45f, 160f to 90f, 240f to 135f), index) { eventTime = at }

    /** A touch event as it reached the native library, read from the arguments of the call. */
    private class TouchCall(private val args: List<Any?>) {
        val type: Int
            get() = (args[0] as Byte).toInt()

        val pointerId: Int
            get() = args[1] as Int

        val x: Float
            get() = args[2] as Float

        val y: Float
            get() = args[3] as Float

        val pressure: Float
            get() = args[4] as Float

        val major: Float
            get() = args[5] as Float

        val minor: Float
            get() = args[6] as Float

        val rotation: Int
            get() = (args[7] as Short).toInt()
    }

    private fun touches() = ShadowRecordingBridge.named("sendTouchEvent").map { TouchCall(it.args) }

    // The touch events that reached the host, which are [count] of them.
    private fun touches(count: Int): List<TouchCall> {
        assertThat(touches()).hasSize(count)
        return touches()
    }

    // ----- The place of a touch -----

    @Test
    fun `a finger that goes down is a touch down with its pointer id and its place on the stream view`() {
        streaming()
        native()

        assertThat(touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))).isTrue()

        // Nothing reaches the host as a mouse.
        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent")
        val down = touches(1).single()
        assertThat(down.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_DOWN.toInt())
        assertThat(down.pointerId).isEqualTo(0)
        assertThat(down.x).isWithin(EPSILON).of(0.5f)
        assertThat(down.y).isWithin(EPSILON).of(0.5f)
        assertThat(down.rotation).isEqualTo(MoonBridge.LI_ROT_UNKNOWN.toInt())
    }

    @Test
    fun `a touch on the background view is moved by the origin of the stream view`() {
        streaming(left = 40)
        native()

        touch(backgroundView, finger(MotionEvent.ACTION_DOWN, 200f, 235f))

        // 160 pixels from the left of the stream view, which starts at 40, and 90 from its top.
        val down = touches(1).single()
        assertThat(down.x).isWithin(EPSILON).of(0.5f)
        assertThat(down.y).isWithin(EPSILON).of(0.5f)
    }

    @Test
    fun `a place past the edge of the stream view is cut to the edge`() {
        streaming()
        native()

        touch(streamView, finger(MotionEvent.ACTION_DOWN, 400f, 400f))
        touch(streamView, finger(MotionEvent.ACTION_MOVE, -10f, -10f, TIME + 10))

        val (farEdge, nearEdge) = touches(2)
        assertThat(farEdge.x).isEqualTo(1f)
        assertThat(farEdge.y).isEqualTo(1f)
        assertThat(nearEdge.x).isEqualTo(0f)
        assertThat(nearEdge.y).isEqualTo(0f)
    }

    // ----- Down, move, up and cancel -----

    @Test
    fun `a move is a touch move of every pointer with its own id, and the samples before it are not sent`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
        forgetSent()

        val move =
            twoFingers(MotionEvent.ACTION_MOVE, 0) {
                earlier(70f to 40f, 230f to 130f)
                eventTime = TIME + 20
            }
        assertThat(touch(streamView, move)).isTrue()

        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent", "sendTouchEvent")
        val (first, second) = touches(2)
        assertThat(first.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_MOVE.toInt())
        assertThat(first.pointerId).isEqualTo(0)
        assertThat(first.x).isWithin(EPSILON).of(0.25f)
        assertThat(first.y).isWithin(EPSILON).of(0.25f)
        assertThat(second.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_MOVE.toInt())
        assertThat(second.pointerId).isEqualTo(1)
        assertThat(second.x).isWithin(EPSILON).of(0.75f)
        assertThat(second.y).isWithin(EPSILON).of(0.75f)
    }

    @Test
    fun `a second finger that goes down is a touch down of that finger only`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        forgetSent()

        assertThat(touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))).isTrue()

        val down = touches(1).single()
        assertThat(down.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_DOWN.toInt())
        assertThat(down.pointerId).isEqualTo(1)
        assertThat(down.x).isWithin(EPSILON).of(0.75f)
        assertThat(down.y).isWithin(EPSILON).of(0.75f)
    }

    @Test
    fun `a finger that goes up is a touch up, and the one that Android cancelled is a touch cancel`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        forgetSent()

        assertThat(touch(streamView, finger(MotionEvent.ACTION_UP, 160f, 90f, TIME + 50))).isTrue()
        val cancelled =
            finger(MotionEvent.ACTION_UP, 160f, 90f, TIME + 100) {
                flags = MotionEvent.FLAG_CANCELED
            }
        assertThat(touch(streamView, cancelled)).isTrue()

        val (up, cancel) = touches(2)
        assertThat(up.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_UP.toInt())
        assertThat(cancel.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL.toInt())
        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent", "sendTouchEvent")
    }

    @Test
    fun `a second finger that goes up is a touch up of that finger only`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
        forgetSent()

        assertThat(touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_UP, 1))).isTrue()

        val up = touches(1).single()
        assertThat(up.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_UP.toInt())
        assertThat(up.pointerId).isEqualTo(1)
    }

    @Test
    fun `a cancel of the gesture is a cancel of every touch, with nothing known about them`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        forgetSent()

        assertThat(touch(streamView, finger(MotionEvent.ACTION_CANCEL, 160f, 90f, TIME + 50)))
            .isTrue()

        val cancel = touches(1).single()
        assertThat(cancel.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL.toInt())
        assertThat(cancel.pointerId).isEqualTo(0)
        assertThat(cancel.x).isEqualTo(0f)
        assertThat(cancel.y).isEqualTo(0f)
        assertThat(cancel.pressure).isEqualTo(0f)
        assertThat(cancel.major).isEqualTo(0f)
        assertThat(cancel.minor).isEqualTo(0f)
        assertThat(cancel.rotation).isEqualTo(MoonBridge.LI_ROT_UNKNOWN.toInt())
        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent")
    }

    // ----- Three fingers -----

    @Test
    fun `a third finger that goes down is a touch down of that finger, not a gesture of three fingers`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
        forgetSent()

        assertThat(touch(streamView, threeFingers(MotionEvent.ACTION_POINTER_DOWN, 2))).isTrue()

        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent")
        val down = touches(1).single()
        assertThat(down.type).isEqualTo(MoonBridge.LI_TOUCH_EVENT_DOWN.toInt())
        assertThat(down.pointerId).isEqualTo(2)
        assertThat(down.x).isWithin(EPSILON).of(0.75f)
        assertThat(down.y).isWithin(EPSILON).of(0.75f)
    }

    @Test
    fun `a gesture of three fingers reaches the host whole, no finger moves or lifts without having gone down`() {
        streaming()
        native()

        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
        touch(streamView, threeFingers(MotionEvent.ACTION_POINTER_DOWN, 2, TIME + 10))
        touch(streamView, threeFingers(MotionEvent.ACTION_MOVE, 0, TIME + 20))
        touch(streamView, threeFingers(MotionEvent.ACTION_POINTER_UP, 2, TIME + 30))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_UP, 1))
        touch(streamView, finger(MotionEvent.ACTION_UP, 80f, 45f, TIME + 40))

        assertThat(touches().map { it.type to it.pointerId })
            .containsExactly(
                DOWN to 0,
                DOWN to 1,
                DOWN to 2,
                MOVE to 0,
                MOVE to 1,
                MOVE to 2,
                UP to 2,
                UP to 1,
                UP to 0,
            )
            .inOrder()
        assertThat(sent().map { it.name }.toSet()).containsExactly("sendTouchEvent")
    }

    // ----- A finger that is still down when the chrome takes the input -----

    private fun touchTypes() = touches().map { it.type }

    // The chrome gives the input back and takes it again, as the menu does each time it opens.
    private fun reopenMenu() {
        rig.session.closePanel()
        idle(1)
        rig.session.openMenu()
        assertThat(rig.session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a finger the host took, still down when the chrome takes the input, is cancelled on the host`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        forgetSent()

        rig.session.openMenu()

        // The chrome gets what follows, so the up of the finger never reaches the host.
        assertThat(sent().map { it.name }).containsExactly("sendTouchEvent")
        val cancel = touches(1).single()
        assertThat(cancel.type).isEqualTo(CANCEL_ALL)
        assertThat(cancel.pointerId).isEqualTo(0)
        assertThat(cancel.x).isEqualTo(0f)
        assertThat(cancel.y).isEqualTo(0f)
        assertThat(cancel.pressure).isEqualTo(0f)
        assertThat(cancel.rotation).isEqualTo(MoonBridge.LI_ROT_UNKNOWN.toInt())
    }

    @Test
    fun `the cancel goes once, however often the chrome takes the input after it`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        forgetSent()

        rig.session.openMenu()
        reopenMenu()
        reopenMenu()

        assertThat(touchTypes()).containsExactly(CANCEL_ALL)
    }

    @Test
    fun `a finger that is still down when its mate is lifted is cancelled with the chrome taking the input`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 80f, 45f))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
        touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_UP, 1))
        forgetSent()

        rig.session.openMenu()

        assertThat(touchTypes()).containsExactly(CANCEL_ALL)
    }

    @Test
    fun `with no finger down the chrome taking the input sends the host no touch event`() {
        streaming()
        native()
        rig.session.openMenu()
        assertThat(touches()).isEmpty()

        // Fingers that went up, and a gesture that Android cancelled, leave nothing down either.
        rig.session.closePanel()
        idle(1)
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        touch(streamView, finger(MotionEvent.ACTION_UP, 160f, 90f, TIME + 50))
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f, TIME + 500))
        touch(streamView, finger(MotionEvent.ACTION_CANCEL, 160f, 90f, TIME + 550))
        forgetSent()
        rig.session.openMenu()

        assertThat(touches()).isEmpty()
    }

    // ----- A mode change away from Native with a finger down -----

    @Test
    fun `a mode change away from Native cancels the finger that the host took, once`() {
        streaming()

        for (mode in listOf(TouchMode.TRACKPAD, TouchMode.DIRECT, TouchMode.OFF)) {
            native()
            touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
            forgetSent()

            rig.session.setTouchMode(mode)
            rig.session.setTouchMode(mode)

            assertWithMessage(mode.name).that(touchTypes()).containsExactly(CANCEL_ALL)
        }
    }

    @Test
    fun `picking Native while in Native, and a mode change with no finger down, cancel nothing`() {
        streaming()
        native()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
        forgetSent()

        native()
        assertThat(touches()).isEmpty()

        // The finger goes on where it is.
        touch(streamView, finger(MotionEvent.ACTION_UP, 160f, 90f, TIME + 50))
        forgetSent()
        rig.session.setTouchMode(TouchMode.DIRECT)
        assertThat(touches()).isEmpty()
    }

    // ----- The other modes -----

    @Test
    fun `the trackpad and the direct mode send no touch event when the chrome takes the input`() {
        streaming()

        for (mode in listOf(TouchMode.TRACKPAD, TouchMode.DIRECT)) {
            rig.session.setTouchMode(mode)
            touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
            touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
            forgetSent()

            rig.session.openMenu()
            reopenMenu()
            rig.session.closePanel()
            idle(1)

            assertWithMessage(mode.name).that(touches()).isEmpty()
        }
    }

    @Test
    fun `the trackpad and the direct mode send no touch events`() {
        streaming()

        for (mode in listOf(TouchMode.TRACKPAD, TouchMode.DIRECT)) {
            rig.session.setTouchMode(mode)
            touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f))
            touch(streamView, finger(MotionEvent.ACTION_MOVE, 170f, 90f, TIME + 10))
            touch(streamView, twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1))
            touch(streamView, finger(MotionEvent.ACTION_CANCEL, 160f, 90f, TIME + 20))
            touch(streamView, finger(MotionEvent.ACTION_DOWN, 160f, 90f, TIME + 500))
            touch(streamView, finger(MotionEvent.ACTION_UP, 160f, 90f, TIME + 550))
            idle(200)

            assertThat(touches()).isEmpty()
        }
    }

    private companion object {
        const val EPSILON = 1e-6f
        val CANCEL_ALL = MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL.toInt()
        val DOWN = MoonBridge.LI_TOUCH_EVENT_DOWN.toInt()
        val MOVE = MoonBridge.LI_TOUCH_EVENT_MOVE.toInt()
        val UP = MoonBridge.LI_TOUCH_EVENT_UP.toInt()
    }
}
