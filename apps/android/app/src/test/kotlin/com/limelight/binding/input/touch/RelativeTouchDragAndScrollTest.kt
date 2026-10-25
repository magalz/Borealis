package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the trackpad context sends to the host for a drag (after 650 ms) and for the two-finger
 * scroll (five times the scaled y delta), and what cancelling does to either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class RelativeTouchDragAndScrollTest {
    private val rig = TouchRig()

    @Test
    fun `a touch held for 650 ms starts a left drag that the lift ends`() {
        val context = rig.relative()
        context.start(100, 100)

        rig.advance(649)
        assertThat(rig.sent()).isEmpty()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down LEFT")

        context.moveTo(105, 100)
        context.touchUpEvent(105, 100, T0 + 900)
        rig.advance(2000)

        assertThat(rig.sent())
            .containsExactly("down LEFT", "sendMouseMove(5, 0)", "up LEFT")
            .inOrder()
    }

    @Test
    fun `a move inside the tap box leaves the drag, one outside the box takes it away`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(110, 100)
        rig.advance(650)
        assertThat(rig.sent()).containsExactly("sendMouseMove(10, 0)", "down LEFT").inOrder()
        context.touchUpEvent(110, 100, T0 + 700)
        rig.forget()

        context.start(100, 100)
        context.moveTo(121, 100)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("sendMouseMove(21, 0)")
    }

    @Test
    fun `moves that add up to 25 px take the drag away`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(110, 100)
        context.moveTo(100, 100)
        context.moveTo(105, 100)

        rig.advance(2000)

        assertThat(rig.sent().filter { it.startsWith("down") }).isEmpty()
    }

    @Test
    fun `a new touch down replaces the pending drag timer`() {
        val context = rig.relative()
        context.start(100, 100)
        rig.advance(400)
        context.start(100, 100, T0 + 400)

        rig.advance(649) // 1049 ms: the timer of the first touch was due at 650
        assertThat(rig.sent()).isEmpty()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(2000)
        assertThat(rig.sent()).containsExactly("down LEFT")
    }

    @Test
    fun `a lift too slow for a tap removes the drag timer`() {
        val context = rig.relative()
        context.start(100, 100)
        rig.advance(300)

        context.touchUpEvent(100, 100, T0 + 300)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a drag does not turn into a scroll when a second finger joins`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.start(100, 100)
        rig.advance(650)
        first.moveTo(150, 100) // far from the touch down, but the drag keeps the move away
        rig.forget()

        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 700, true)
        first.moveTo(150, 130)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `cancelling during a drag raises the button, and a cancel stops the touch`() {
        val context = rig.relative()
        context.start(100, 100)
        rig.advance(650)
        assertThat(context.isCancelled).isFalse()

        context.cancelTouch()

        assertThat(context.isCancelled).isTrue()
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
        assertThat(context.touchMoveEvent(150, 150, T0 + 700)).isTrue()
        context.touchUpEvent(150, 150, T0 + 800)
        rig.advance(2000)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
    }

    @Test
    fun `cancelling before the drag removes the timer and sends nothing`() {
        val context = rig.relative()
        context.start(100, 100)

        context.cancelTouch()
        rig.advance(2000)

        assertThat(context.isCancelled).isTrue()
        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a new finger clears the cancel, a finger that takes over does not`() {
        val context = rig.relative()
        context.start(100, 100)
        context.cancelTouch()

        context.touchDownEvent(100, 100, T0 + 100, false)
        assertThat(context.isCancelled).isTrue()
        context.touchDownEvent(100, 100, T0 + 200, true)
        assertThat(context.isCancelled).isFalse()

        context.touchUpEvent(100, 100, T0 + 250)
        assertThat(rig.sent()).containsExactly("down LEFT")
    }

    // The scroll

    @Test
    fun `two fingers that move make a scroll of five times the y delta`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        // The move that leaves the tap box confirms the move, and the scroll with it
        first.touchMoveEvent(100, 130, T0 + 20)
        second.touchMoveEvent(300, 130, T0 + 20)
        first.touchMoveEvent(100, 120, T0 + 30)
        second.touchMoveEvent(300, 120, T0 + 30)
        first.touchMoveEvent(110, 120, T0 + 40)

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseHighResScroll(150)",
                "sendMouseHighResScroll(-50)",
                "sendMouseHighResScroll(0)",
            )
            .inOrder()
    }

    @Test
    fun `the scroll scales the y delta with the view like a move`() {
        val rig = TouchRig(width = 1280, height = 1440)
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.start(100, 100)
        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 10, true)

        first.touchMoveEvent(100, 130, T0 + 20) // 30 * 0.5 = 15
        first.touchMoveEvent(100, 131, T0 + 30) // 0.5 rounds up to 1
        first.touchMoveEvent(100, 130, T0 + 40) // -0.5 rounds to -1

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseHighResScroll(75)",
                "sendMouseHighResScroll(5)",
                "sendMouseHighResScroll(-5)",
            )
            .inOrder()
    }

    @Test
    fun `two fingers that have not left the tap box do not scroll yet and the scroll starts from the last touch`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        first.touchMoveEvent(100, 110, T0 + 20)
        assertThat(rig.sent()).isEmpty()
        first.touchMoveEvent(100, 130, T0 + 30)

        assertThat(rig.sent()).containsExactly("sendMouseHighResScroll(100)")
    }

    @Test
    fun `the scroll is a short`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        first.touchMoveEvent(100, 7100, T0 + 20) // 7000 * 5 = 35000
        first.touchMoveEvent(100, 100, T0 + 30) // -7000 * 5

        assertThat(rig.sent())
            .containsExactly("sendMouseHighResScroll(-30536)", "sendMouseHighResScroll(30536)")
            .inOrder()
    }

    @Test
    fun `the scroll ends with the second finger and the first one moves the pointer again`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)
        first.touchMoveEvent(100, 130, T0 + 20)
        second.touchMoveEvent(300, 130, T0 + 20)

        second.touchUpEvent(300, 130, T0 + 30)
        setPointerCount(1, first, second)
        first.touchMoveEvent(100, 140, T0 + 40)

        assertThat(rig.sent())
            .containsExactly("sendMouseHighResScroll(150)", "sendMouseMove(0, 10)")
            .inOrder()
    }

    @Test
    fun `three pointers are no scroll`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)
        first.touchMoveEvent(100, 130, T0 + 20)

        setPointerCount(3, first, second)
        first.touchMoveEvent(100, 140, T0 + 30)

        assertThat(rig.sent())
            .containsExactly("sendMouseHighResScroll(150)", "sendMouseMove(0, 10)")
            .inOrder()
    }

    @Test
    fun `a touch after a two-finger scroll is a plain tap again`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)
        first.touchMoveEvent(100, 130, T0 + 20)
        second.touchMoveEvent(300, 130, T0 + 20)
        second.touchUpEvent(300, 130, T0 + 30)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 130, T0 + 40)
        setPointerCount(0, first, second)
        rig.forget()

        first.start(100, 100, T0 + 1000)
        first.touchUpEvent(100, 100, T0 + 1100)

        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(100)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
    }
}
