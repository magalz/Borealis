package io.github.magalz.borealis.stream.game.motion

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * The fingers on the touch screen: each touch goes to the touch context of its pointer, which sends
 * the mouse events to the host. The tests look at the host: where a finger puts the pointer in the
 * direct mode (each touch is a place on the stream view) and how it moves it in the trackpad mode
 * (each touch is a move).
 */
internal class FingerTouchTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT

    private fun direct() = rig.session.setTouchMode(TouchMode.DIRECT)

    private fun touch(view: View, event: MotionEvent) = rig.game.onTouch(view, event)

    private fun finger(
        action: Int,
        x: Float,
        y: Float,
        at: Long = TIME,
        build: MotionSpec.() -> Unit = {},
    ) =
        fingerEvent(action, listOf(x to y)) {
            eventTime = at
            build()
        }

    private fun down(x: Float, y: Float, at: Long = TIME) =
        finger(MotionEvent.ACTION_DOWN, x, y, at)

    private fun up(x: Float, y: Float, at: Long = TIME + 50) =
        finger(MotionEvent.ACTION_UP, x, y, at)

    // ----- The direct mode: a touch is a place -----

    @Test
    fun `a quick tap is a click where the finger was, cut toward zero, and the button goes up soon after`() {
        streaming()
        direct()

        assertThat(touch(streamView, down(100.9f, 50.7f))).isTrue()
        assertThat(sent()).isEmpty()
        assertThat(touch(streamView, up(100.9f, 50.7f))).isTrue()
        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
        forgetSent()

        idle(100)

        assertThat(sent()).containsExactly(release(left))
    }

    @Test
    fun `a finger that stays down clicks after a tenth of a second`() {
        streaming()
        direct()
        touch(streamView, down(100f, 50f))

        idle(99)
        assertThat(sent()).isEmpty()
        idle(1)

        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
    }

    @Test
    fun `a touch on the background view is moved by the origin of the stream view`() {
        streaming()
        direct()

        touch(backgroundView, down(100.9f, 250.7f))
        touch(backgroundView, up(100.9f, 250.7f))

        // The stream view starts 145 pixels down.
        assertThat(sent()).containsExactly(position(100, 105), press(left)).inOrder()
    }

    @Test
    fun `the origin of the stream view moves a touch in x as well, and not a touch on the stream view`() {
        streaming(left = 40)
        direct()

        touch(backgroundView, down(100.9f, 250.7f))
        touch(backgroundView, up(100.9f, 250.7f))
        assertThat(sent()).containsExactly(position(60, 105), press(left)).inOrder()
        forgetSent()
        idle(100)
        forgetSent()

        touch(streamView, down(100.9f, 50.7f, TIME + 1000))
        touch(streamView, up(100.9f, 50.7f, TIME + 1050))
        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
    }

    @Test
    fun `a second tap soon after the first does not place the pointer again, a later one does`() {
        streaming()
        direct()

        touch(streamView, down(100f, 50f, TIME))
        touch(streamView, up(100f, 50f, TIME + 50))
        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
        idle(100)
        forgetSent()

        // 50 milliseconds after the lift of the first tap: the pointer is where it was.
        touch(streamView, down(100f, 50f, TIME + 100))
        touch(streamView, up(100f, 50f, TIME + 150))
        assertThat(sent()).containsExactly(press(left))
        idle(100)
        forgetSent()

        // 350 milliseconds after the lift of the second tap.
        touch(streamView, down(100f, 50f, TIME + 500))
        touch(streamView, up(100f, 50f, TIME + 550))
        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
    }

    @Test
    fun `a touch that ends cancelled is no tap`() {
        streaming()
        direct()
        touch(streamView, down(100f, 50f))

        assertThat(touch(streamView, cancelledUp(100f, 50f))).isTrue()
        idle(500)

        assertThat(sent()).isEmpty()
    }

    private fun cancelledUp(x: Float, y: Float) =
        finger(MotionEvent.ACTION_UP, x, y, TIME + 50) { flags = MotionEvent.FLAG_CANCELED }

    @Test
    fun `a cancel of the gesture lets the button go and ends the touches`() {
        streaming()
        direct()
        touch(streamView, down(100f, 50f))
        idle(100)
        forgetSent()

        assertThat(touch(streamView, finger(MotionEvent.ACTION_CANCEL, 100f, 50f, TIME + 200)))
            .isTrue()

        assertThat(sent()).containsExactly(release(left))
    }

    @Test
    fun `a second finger ends the touch of the first, and scrolls with its own moves`() {
        streaming()
        direct()
        touch(streamView, down(100f, 100f))
        idle(100)
        forgetSent()

        val second =
            fingerEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                listOf(100f to 100f, 200.9f to 100.9f),
                index = 1,
            ) {
                eventTime = TIME + 200
            }
        assertThat(touch(streamView, second)).isTrue()
        // The first finger is not a click any more.
        assertThat(sent()).containsExactly(release(left))
        forgetSent()

        // The second finger goes down 4 and then 6 pixels: the scroll is three times the move.
        val move =
            fingerEvent(MotionEvent.ACTION_MOVE, listOf(100f to 100f, 200.9f to 110.9f)) {
                eventTime = TIME + 220
                earlier(100f to 100f, 200.9f to 104.9f)
            }
        assertThat(touch(streamView, move)).isTrue()

        assertThat(sent()).containsExactly(scrolled(12), scrolled(18)).inOrder()
    }

    // ----- The trackpad mode: a touch is a move -----

    @Test
    fun `a finger that moves is a mouse that moves, the samples of the event first and its own last`() {
        streaming()
        touch(streamView, down(100f, 100f))

        val move =
            fingerEvent(MotionEvent.ACTION_MOVE, listOf(130f to 110f)) {
                eventTime = TIME + 30
                earlier(110f to 100f)
                earlier(120f to 105f)
            }
        assertThat(touch(streamView, move)).isTrue()

        // The stream is 1280 wide on a view of 320: the move is four times as long.
        assertThat(sent()).containsExactly(moveBy(40, 0), moveBy(40, 20), moveBy(40, 20)).inOrder()
    }

    @Test
    fun `a trackpad touch on the background view is not moved by the origin of the stream view`() {
        streaming()
        touch(backgroundView, down(100f, 144.9f))

        touch(backgroundView, finger(MotionEvent.ACTION_MOVE, 100f, 146.2f, TIME + 10))

        // The two whole pixels 144 and 146, not the 0 and 1 that the origin would leave.
        assertThat(sent()).containsExactly(moveBy(0, 8))
    }

    @Test
    fun `the touch mode that decides is the one of the session, and a switch changes it`() {
        rig.preference("checkbox_touchscreen_trackpad", false)
        streaming()
        // The settings say direct touch, the session says trackpad.
        rig.session.setTouchMode(TouchMode.TRACKPAD)
        touch(backgroundView, down(100f, 144.9f))

        touch(backgroundView, finger(MotionEvent.ACTION_MOVE, 100f, 146.2f, TIME + 10))

        assertThat(sent()).containsExactly(moveBy(0, 8))
    }

    @Test
    fun `the touch mode of the session is direct, whatever the settings say`() {
        rig.preference("checkbox_touchscreen_trackpad", true)
        streaming()
        direct()

        touch(backgroundView, down(100.9f, 250.7f))
        touch(backgroundView, up(100.9f, 250.7f))

        assertThat(sent()).containsExactly(position(100, 105), press(left)).inOrder()
    }

    @Test
    fun `a tap on the trackpad is a click when it is short and still`() {
        streaming()

        touch(streamView, down(100f, 100f, TIME))
        touch(streamView, up(100f, 100f, TIME + 250))

        assertThat(sent()).containsExactly(press(left))
    }

    @Test
    fun `a touch that lasts longer than a tap is no click`() {
        streaming()

        touch(streamView, down(100f, 100f, TIME))
        touch(streamView, up(100f, 100f, TIME + 251))

        assertThat(sent()).isEmpty()
    }

    @Test
    fun `when the first of two fingers lifts, the second takes its place as the finger that moves`() {
        streaming()
        touch(streamView, down(100f, 100f, TIME))
        val second =
            fingerEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                listOf(100f to 100f, 200f to 200f),
                index = 1,
            ) {
                eventTime = TIME + 10
            }
        touch(streamView, second)
        val lift =
            fingerEvent(
                MotionEvent.ACTION_POINTER_UP,
                listOf(100f to 100f, 200f to 200f),
                index = 0,
            ) {
                eventTime = TIME + 20
            }
        assertThat(touch(streamView, lift)).isTrue()
        assertThat(sent()).isEmpty()

        // The finger that is left moves ten pixels from where it was: not 110 from the first.
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 210f, 200f, TIME + 30))

        assertThat(sent()).containsExactly(moveBy(40, 0))
    }

    @Test
    fun `when the second of two fingers lifts, the first goes on moving the pointer from where it was`() {
        streaming()
        touch(streamView, down(100f, 100f, TIME))
        val second =
            fingerEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                listOf(100f to 100f, 200f to 200f),
                index = 1,
            ) {
                eventTime = TIME + 10
            }
        touch(streamView, second)
        val lift =
            fingerEvent(
                MotionEvent.ACTION_POINTER_UP,
                listOf(100f to 100f, 200f to 200f),
                index = 1,
            ) {
                eventTime = TIME + 20
            }
        assertThat(touch(streamView, lift)).isTrue()
        // Two fingers that touch and lift soon are a right click.
        assertThat(sent()).containsExactly(press(MouseButtonPacket.BUTTON_RIGHT))
        forgetSent()

        // One finger is left: its move is a move of the pointer, and not a scroll.
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 110f, 100f, TIME + 30))

        assertThat(sent()).containsExactly(moveBy(40, 0))
    }

    @Test
    fun `a finger that lifts as a cancel leaves no finger to take its place`() {
        streaming()
        touch(streamView, down(100f, 100f, TIME))
        val second =
            fingerEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                listOf(100f to 100f, 200f to 200f),
                index = 1,
            ) {
                eventTime = TIME + 10
            }
        touch(streamView, second)
        val lift =
            fingerEvent(
                MotionEvent.ACTION_POINTER_UP,
                listOf(100f to 100f, 200f to 200f),
                index = 0,
            ) {
                eventTime = TIME + 20
                flags = MotionEvent.FLAG_CANCELED
            }
        assertThat(touch(streamView, lift)).isTrue()

        touch(streamView, finger(MotionEvent.ACTION_MOVE, 210f, 200f, TIME + 30))

        assertThat(sent()).isEmpty()
    }

    @Test
    fun `two fingers that move far on the trackpad scroll`() {
        streaming()
        touch(streamView, down(100f, 100f, TIME))
        touch(
            streamView,
            fingerEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                listOf(100f to 100f, 200f to 100f),
                index = 1,
            ) {
                eventTime = TIME + 10
            },
        )

        touch(
            streamView,
            fingerEvent(MotionEvent.ACTION_MOVE, listOf(100f to 130f, 200f to 130f)) {
                eventTime = TIME + 20
            },
        )

        // 30 pixels are 120 on the host, and the scroll is five times that.
        assertThat(sent()).containsExactly(scrolled(600))
    }

    @Test
    fun `a cancel ends the touches of the trackpad, and what moves after it is not a move`() {
        streaming()
        touch(streamView, down(100f, 100f))

        assertThat(touch(streamView, finger(MotionEvent.ACTION_CANCEL, 100f, 100f, TIME + 10)))
            .isTrue()
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 130f, 100f, TIME + 20))

        assertThat(sent()).isEmpty()
    }

    // ----- The touch screen that is off, and the buttons of the on-screen controller -----

    @Test
    fun `a switched-off touch screen is ignored, though the same touch moves the pointer when it is on`() {
        streaming()
        touch(streamView, down(100f, 100f, TIME))
        touch(streamView, up(100f, 100f, TIME + 100))
        assertThat(sent()).containsExactly(press(left))
        idle(100)
        forgetSent()

        rig.session.setTouchMode(TouchMode.OFF)
        assertThat(touch(streamView, down(100f, 100f, TIME + 1000))).isTrue()
        assertThat(touch(streamView, up(100f, 100f, TIME + 1100))).isTrue()
        idle(200)

        assertThat(sent()).isEmpty()
    }

    @Test
    fun `a touch screen that is off does not stop the mouse`() {
        streaming()
        rig.session.setTouchMode(TouchMode.OFF)

        rig.game.onGenericMotion(
            streamView,
            mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, MotionEvent.BUTTON_PRIMARY),
        )

        assertThat(sent()).containsExactly(position(10, 20), press(left)).inOrder()
    }

    private fun configureButton(): Button {
        val frame = streamView.parent as ViewGroup
        return (0 until frame.childCount).map(frame::getChildAt).filterIsInstance<Button>().single()
    }

    @Test
    fun `the touches are ignored while the on-screen controller is being laid out`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        streaming()
        // The controller is active: a tap is a click.
        touch(streamView, down(100f, 100f, TIME))
        touch(streamView, up(100f, 100f, TIME + 100))
        assertThat(sent()).containsExactly(press(left))
        idle(100)
        forgetSent()

        // The configure button moves the buttons, then resizes them.
        for ((step, at) in listOf("move" to 1000L, "resize" to 2000L)) {
            configureButton().performClick()

            assertWithMessage("$step: down")
                .that(touch(streamView, down(100f, 100f, TIME + at)))
                .isTrue()
            assertWithMessage("$step: up")
                .that(touch(streamView, up(100f, 100f, TIME + at + 100)))
                .isTrue()
            idle(200)
            assertWithMessage(step).that(sent()).isEmpty()
        }
    }

    @Test
    fun `the mouse is not stopped while the on-screen controller is being laid out`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        streaming()
        configureButton().performClick()

        rig.game.onGenericMotion(
            streamView,
            mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, MotionEvent.BUTTON_PRIMARY),
        )

        assertThat(sent()).containsExactly(position(10, 20), press(left)).inOrder()
    }

    // ----- What no touch context takes -----

    @Test
    fun `an action that is no touch of a finger is not consumed`() {
        streaming()

        for (action in
            listOf(
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_OUTSIDE,
                MotionEvent.ACTION_SCROLL,
            )) {
            assertWithMessage(MotionEvent.actionToString(action))
                .that(touch(streamView, finger(action, 100f, 100f)))
                .isFalse()
        }
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `only two fingers have a touch context, a pointer past them is not consumed`() {
        streaming()
        val three = listOf(10f to 10f, 20f to 20f, 30f to 30f)
        val four = three + (40f to 40f)

        val fourthDown =
            fingerEvent(MotionEvent.ACTION_POINTER_DOWN, four, index = 3) { eventTime = TIME }
        val thirdUp =
            fingerEvent(MotionEvent.ACTION_POINTER_UP, three, index = 2) { eventTime = TIME }
        val thirdOfFour =
            fingerEvent(MotionEvent.ACTION_POINTER_DOWN, four, index = 2) { eventTime = TIME }

        assertThat(touch(streamView, fourthDown)).isFalse()
        assertThat(touch(streamView, thirdUp)).isFalse()
        assertThat(touch(streamView, thirdOfFour)).isFalse()
        assertThat(sent()).isEmpty()
    }

    private companion object {
        const val TIME = 10_000L
    }
}
