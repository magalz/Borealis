package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the trackpad context sends to the host for a drag (a double tap and hold: a press that
 * follows a tap within the tap time) and for the two-finger scroll (five times the scaled y delta),
 * and what cancelling does to either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class RelativeTouchDragAndScrollTest {
    private val rig = TouchRig()

    /**
     * A tap that lifts at [T0] + 100 and sends its click, with its release and the calls so far
     * forgotten: the press that follows it is the second press of a double tap.
     */
    private fun RelativeTouchContext.firstTap() {
        start(100, 100)
        touchUpEvent(100, 100, T0 + 100)
        setPointerCount(0)
        rig.advance(100)
        rig.forget()
    }

    // The double tap and hold

    @Test
    fun `a second press held past the tap time starts a left drag that the lift ends`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 100)
        // The click of the first tap goes out at once
        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(100)
        rig.forget()

        context.start(100, 100, T0 + 200)
        rig.advance(249)
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
    fun `a second press that moves past the tap box starts the drag with that move`() {
        val context = rig.relative()
        context.firstTap()
        context.start(100, 100, T0 + 200)

        context.moveTo(110, 100)
        assertThat(rig.sent()).containsExactly("sendMouseMove(10, 0)")
        context.moveTo(121, 100)
        assertThat(rig.sent())
            .containsExactly("sendMouseMove(10, 0)", "down LEFT", "sendMouseMove(11, 0)")
            .inOrder()

        context.moveTo(130, 100)
        context.touchUpEvent(130, 100, T0 + 300)
        rig.advance(2000) // the hold timer has gone with the drag: no second button down

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(10, 0)",
                "down LEFT",
                "sendMouseMove(11, 0)",
                "sendMouseMove(9, 0)",
                "up LEFT",
            )
            .inOrder()
    }

    @Test
    fun `a second press whose moves add up to 25 px starts the drag`() {
        val context = rig.relative()
        context.firstTap()
        context.start(100, 100, T0 + 200)

        context.moveTo(110, 100)
        context.moveTo(100, 100)
        context.moveTo(105, 100)

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(10, 0)",
                "sendMouseMove(-10, 0)",
                "down LEFT",
                "sendMouseMove(5, 0)",
            )
            .inOrder()
    }

    @Test
    fun `a second press 250 ms after the first lift is in the window, one 251 ms after is not`() {
        val context = rig.relative()
        context.firstTap()
        context.start(100, 100, T0 + 100 + 250)
        rig.advance(250)
        assertThat(rig.sent()).containsExactly("down LEFT")
        context.touchUpEvent(100, 100, T0 + 700)
        rig.forget()

        context.firstTap()
        context.start(100, 100, T0 + 100 + 251)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a press that starts before the tap lifted is no second press`() {
        val context = rig.relative()
        context.firstTap()

        context.start(100, 100, T0)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second press that lifts quickly is a second click, so a double click works`() {
        val context = rig.relative()
        context.firstTap()

        context.start(100, 100, T0 + 200)
        context.touchUpEvent(100, 100, T0 + 300)
        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(100)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
        rig.advance(2000) // the hold timer went with the lift: no drag
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
    }

    @Test
    fun `the lift of a drag opens no window, and neither does a touch too slow for a tap`() {
        val context = rig.relative()
        context.firstTap()
        context.start(100, 100, T0 + 200)
        rig.advance(250)
        context.touchUpEvent(100, 100, T0 + 700)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
        rig.forget()

        context.start(100, 100, T0 + 800)
        rig.advance(2000)
        assertThat(rig.sent()).isEmpty()
        context.touchUpEvent(100, 100, T0 + 2800)

        context.start(100, 100, T0 + 2900)
        rig.advance(2000)
        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a drag that starts before the click is released flushes the release and keeps its button`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 100)
        // The release of the click is still 100 ms away when the second press moves
        context.start(100, 100, T0 + 130)

        context.moveTo(125, 100)
        assertThat(rig.sent())
            .containsExactly("down LEFT", "up LEFT", "down LEFT", "sendMouseMove(25, 0)")
            .inOrder()

        rig.advance(2000) // the pending release was taken with the flush: no up in the drag
        assertThat(rig.sent())
            .containsExactly("down LEFT", "up LEFT", "down LEFT", "sendMouseMove(25, 0)")
            .inOrder()
        context.touchUpEvent(125, 100, T0 + 400)
        assertThat(rig.sent().last()).isEqualTo("up LEFT")
        assertThat(rig.sent()).hasSize(5)
    }

    @Test
    fun `a second finger that joins the second press takes the drag away`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.firstTap()
        first.start(100, 100, T0 + 200)

        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 210, true)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a right click opens no window for a drag`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)
        second.touchUpEvent(300, 100, T0 + 120)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 140)
        setPointerCount(0, first, second)
        rig.advance(100)
        rig.forget()

        first.start(100, 100, T0 + 200)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a drag does not turn into a scroll when a second finger joins`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.firstTap()
        first.start(100, 100, T0 + 200)
        rig.advance(250)
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
        context.firstTap()
        context.start(100, 100, T0 + 200)
        rig.advance(250)
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
    fun `cancelling the second press before the drag takes the hold timer away`() {
        val context = rig.relative()
        context.firstTap()
        context.start(100, 100, T0 + 200)

        context.cancelTouch()
        rig.advance(2000)

        assertThat(context.isCancelled).isTrue()
        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a press held still sends no button down, however long it lasts`() {
        val context = rig.relative()
        context.start(100, 100)

        rig.advance(2000)
        assertThat(rig.sent()).isEmpty()
        context.touchUpEvent(100, 100, T0 + 2000)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a press that moves 25 px in small steps sends no button down`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(110, 100)
        context.moveTo(100, 100)
        context.moveTo(105, 100)

        rig.advance(2000)

        assertThat(rig.sent().filter { it.startsWith("down") }).isEmpty()
    }

    @Test
    fun `a lift too slow for a tap sends no click and no drag`() {
        val context = rig.relative()
        context.start(100, 100)
        rig.advance(300)

        context.touchUpEvent(100, 100, T0 + 300)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `cancelling a press sends nothing`() {
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

        assertThat(rig.sent())
            .containsExactly("sendMouseHighResScroll(150)", "sendMouseHighResScroll(-50)")
            .inOrder()
    }

    @Test
    fun `a move of two fingers along x alone sends no scroll`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)
        first.touchMoveEvent(100, 130, T0 + 20) // leaves the tap box: the scroll starts

        first.touchMoveEvent(110, 130, T0 + 30)
        first.touchMoveEvent(90, 130, T0 + 40)

        assertThat(rig.sent()).containsExactly("sendMouseHighResScroll(150)")
    }

    @Test
    fun `a y delta that scales to zero sends no scroll and is kept for the next move`() {
        val rig = TouchRig(width = 1280, height = 7200)
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.start(100, 100)
        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 10, true)

        first.touchMoveEvent(100, 130, T0 + 20) // 30 * 0.1 = 3
        first.touchMoveEvent(100, 131, T0 + 30) // 0.1: zero, the last touch stays at 130
        first.touchMoveEvent(100, 136, T0 + 40) // 6 from 130: 0.6, one

        assertThat(rig.sent())
            .containsExactly("sendMouseHighResScroll(15)", "sendMouseHighResScroll(5)")
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
    fun `a view that has no size has no scale, so two fingers send no scroll`() {
        val rig = TouchRig(width = 0, height = 0)
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.start(100, 100)
        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 10, true)

        first.touchMoveEvent(100, 130, T0 + 20)
        first.touchMoveEvent(100, 100, T0 + 30)

        assertThat(rig.sent()).isEmpty()
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
