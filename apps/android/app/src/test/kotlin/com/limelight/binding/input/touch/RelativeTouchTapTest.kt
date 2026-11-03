package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the trackpad context sends to the host for a tap and for the right button of a two-finger
 * tap (whichever finger lifts first): the thresholds of the tap box (20 px), of the cumulative move
 * (25 px) and of the time (250 ms), the release 100 ms later, and the pending release that a second
 * tap replaces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class RelativeTouchTapTest {
    private val rig = TouchRig()

    @Test
    fun `a quick touch without a move is a left click released 100 ms later`() {
        val context = rig.relative()
        context.start(100, 100)

        context.touchUpEvent(100, 100, T0 + 100)

        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(99)
        assertThat(rig.sent()).containsExactly("down LEFT")
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
        rig.advance(2000) // neither a drag nor a second release
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
    }

    @Test
    fun `a touch of 250 ms is a tap and one of 251 ms is not`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 250)
        rig.advance(100)
        assertThat(rig.sent()).containsExactly("down LEFT", "up LEFT").inOrder()
        rig.forget()

        context.start(100, 100, T0 + 1000)
        context.touchUpEvent(100, 100, T0 + 1000 + 251)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a touch that moves 20 px stays a tap and one that moves 21 px is a move`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(120, 100)
        context.touchUpEvent(120, 100, T0 + 100)
        assertThat(rig.sent()).containsExactly("sendMouseMove(20, 0)", "down LEFT").inOrder()
        rig.advance(100)
        rig.forget()

        context.start(100, 100)
        context.moveTo(121, 100)
        context.touchUpEvent(121, 100, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("sendMouseMove(21, 0)")
    }

    @Test
    fun `a touch that moves 20 px on the y axis stays a tap and one that moves 21 px is a move`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(100, 80)
        context.touchUpEvent(100, 80, T0 + 100)
        assertThat(rig.sent()).containsExactly("sendMouseMove(0, -20)", "down LEFT").inOrder()
        rig.advance(100)
        rig.forget()

        context.start(100, 100)
        context.moveTo(100, 79)
        context.touchUpEvent(100, 79, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("sendMouseMove(0, -21)")
    }

    @Test
    fun `small moves that add up to 24 px keep the tap, 25 px make a move`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(110, 100)
        context.moveTo(100, 100)
        context.moveTo(104, 100)
        context.touchUpEvent(104, 100, T0 + 100)
        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(10, 0)",
                "sendMouseMove(-10, 0)",
                "sendMouseMove(4, 0)",
                "down LEFT",
            )
            .inOrder()
        rig.advance(100)
        rig.forget()

        context.start(100, 100)
        context.moveTo(110, 100)
        context.moveTo(100, 100)
        context.moveTo(105, 100)
        context.touchUpEvent(105, 100, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(10, 0)", "sendMouseMove(-10, 0)", "sendMouseMove(5, 0)")
            .inOrder()
    }

    @Test
    fun `the distance moved is the straight line, not the sum of the axes`() {
        val context = rig.relative()
        context.start(100, 100)
        // Four steps of 3 and 4 are 20 px in a straight line, 28 as the sum of the axes
        for (step in 1..4) {
            context.moveTo(100 + 3 * step, 100 + 4 * step)
        }
        context.touchUpEvent(112, 116, T0 + 100)
        assertThat(rig.sent().last()).isEqualTo("down LEFT")
        rig.advance(100)
        rig.forget()

        context.start(100, 100)
        // The fifth step makes 25 px and ends at the edge of the tap box
        for (step in 1..5) {
            context.moveTo(100 + 3 * step, 100 + 4 * step)
        }
        context.touchUpEvent(115, 120, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).hasSize(5)
        assertThat(rig.sent().filter { it.startsWith("down") }).isEmpty()
    }

    @Test
    fun `the distance moved starts from zero with each new touch`() {
        val context = rig.relative()
        context.start(100, 100)
        context.moveTo(110, 100)
        context.moveTo(100, 100) // 20 px in all, short of the 25 that make a move
        context.touchUpEvent(100, 100, T0 + 300)
        rig.forget()

        context.start(100, 100, T0 + 1000)
        context.moveTo(110, 100)
        context.touchUpEvent(110, 100, T0 + 1100)

        assertThat(rig.sent()).containsExactly("sendMouseMove(10, 0)", "down LEFT").inOrder()
    }

    @Test
    fun `a touch that was cancelled is not a tap`() {
        val context = rig.relative()
        context.start(100, 100)

        context.cancelTouch()
        context.touchUpEvent(100, 100, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second tap before the release replaces the pending release`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 50)
        rig.advance(50)

        context.start(100, 100, T0 + 200)
        context.touchUpEvent(100, 100, T0 + 250)

        // The release of the first tap was due now: the second tap removed it
        rig.advance(99)
        assertThat(rig.sent()).containsExactly("down LEFT", "down LEFT").inOrder()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down LEFT", "down LEFT", "up LEFT").inOrder()
        rig.advance(2000)
        assertThat(rig.sent()).containsExactly("down LEFT", "down LEFT", "up LEFT").inOrder()
    }

    // The right button

    @Test
    fun `a tap of the second finger is a right click, and the first finger then clicks nothing`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.touchUpEvent(300, 100, T0 + 120)
        assertThat(rig.sent()).containsExactly("down RIGHT")
        rig.advance(99)
        assertThat(rig.sent()).containsExactly("down RIGHT")
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()

        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 200)
        rig.advance(2000)
        setPointerCount(0, first, second)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a two-finger tap whose first finger lifts first is one right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        liftFirstOfTwoFingers(first, second, T0 + 100)
        // The tap is whole when the second finger lifts, and not before.
        assertThat(rig.sent()).isEmpty()

        first.touchUpEvent(300, 100, T0 + 120)
        assertThat(rig.sent()).containsExactly("down RIGHT")
        rig.advance(99)
        assertThat(rig.sent()).containsExactly("down RIGHT")
        rig.advance(1)
        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
        setPointerCount(0, first, second)
        rig.advance(2000) // neither a second click nor a drag
        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    // The tap time of a two-finger tap runs from the second finger's down (twoFingers lands it at
    // T0 + 10), whichever finger lifts first.

    @Test
    fun `a two-finger tap that lasts more than 250 ms from the second touch is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        liftFirstOfTwoFingers(first, second, T0 + 100)
        first.touchUpEvent(300, 100, T0 + 261)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second finger that lifts first more than 250 ms after landing is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.touchUpEvent(300, 100, T0 + 261)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 270)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a two-finger tap that lasts 250 ms from the second touch is a right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        liftFirstOfTwoFingers(first, second, T0 + 100)
        first.touchUpEvent(300, 100, T0 + 260)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a second finger that lifts first 250 ms after landing is a right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.touchUpEvent(300, 100, T0 + 260)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 270)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a first finger that rested for a second before the second one tapped does not stop the right click`() {
        val (first, second) = rig.twoContexts()
        first.start(100, 100)
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(300, 100, T0 + 1000, true)).isTrue()

        liftFirstOfTwoFingers(first, second, T0 + 1100)
        first.touchUpEvent(300, 100, T0 + 1120)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    // A second finger that lands 150 ms after the first and lifts 120 ms after landing is as good a
    // tap whichever finger lifts first: the window is the same in both orders.

    @Test
    fun `a second finger that lands 150 ms late and lifts first is a right click`() {
        val (first, second) = rig.twoContexts()
        first.start(100, 100)
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(300, 100, T0 + 150, true)).isTrue()

        second.touchUpEvent(300, 100, T0 + 270)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 280)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a second finger that lands 150 ms late and lifts last is a right click`() {
        val (first, second) = rig.twoContexts()
        first.start(100, 100)
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(300, 100, T0 + 150, true)).isTrue()

        liftFirstOfTwoFingers(first, second, T0 + 270)
        first.touchUpEvent(300, 100, T0 + 280)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a two-finger tap whose last finger moves away before it lifts is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        liftFirstOfTwoFingers(first, second, T0 + 100)
        first.moveTo(340, 100)
        first.touchUpEvent(340, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("sendMouseMove(40, 0)")
    }

    @Test
    fun `a two-finger tap whose first finger moved before it lifted is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        first.moveTo(125, 100)
        liftFirstOfTwoFingers(first, second, T0 + 100)
        first.touchUpEvent(300, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a cancel after the first finger lifted ends the two-finger tap`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        liftFirstOfTwoFingers(first, second, T0 + 100)
        first.cancelTouch()
        first.touchUpEvent(300, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a new touch after the first finger lifted starts over as a left click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)
        liftFirstOfTwoFingers(first, second, T0 + 100)
        setPointerCount(0, first, second)

        first.start(500, 500, T0 + 1000)
        first.touchUpEvent(500, 500, T0 + 1050)

        assertThat(rig.sent()).containsExactly("down LEFT")
    }

    @Test
    fun `a third finger that taps after the first finger lifted is the only right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)
        liftFirstOfTwoFingers(first, second, T0 + 20)

        // A new finger goes down in the place of the second context and lifts as a tap: its own
        // right click. The finger that is left lifts soon after.
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(500, 100, T0 + 30, true)).isTrue()
        second.touchUpEvent(500, 100, T0 + 40)
        setPointerCount(1, first, second)
        first.touchUpEvent(300, 100, T0 + 50)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a second finger that moves 24 px in small steps still taps`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(310, 100)
        second.moveTo(300, 100)
        second.moveTo(304, 100)
        second.touchUpEvent(304, 100, T0 + 100)

        assertThat(rig.sent()).containsExactly("down RIGHT")
    }

    @Test
    fun `a second finger that moves 25 px in small steps along x is no tap`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(310, 100)
        second.moveTo(300, 100)
        second.moveTo(305, 100)
        second.touchUpEvent(305, 100, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second finger that moves 25 px in small steps along y is no tap`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(300, 110)
        second.moveTo(300, 100)
        second.moveTo(300, 105)
        second.touchUpEvent(300, 105, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    // Both fingers have to stay in the tap box, whichever lifts first. Each context sees only its
    // own
    // finger, so the contexts share what they saw (TwoFingerTap).

    @Test
    fun `a second finger that slid 21 px before the first finger lifted is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(321, 100, T0 + 40)
        liftFirstOfTwoFingers(first, second, T0 + 100, secondX = 321)
        first.touchUpEvent(321, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second finger that slid 20 px before the first finger lifted still gives the right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(320, 100, T0 + 40)
        liftFirstOfTwoFingers(first, second, T0 + 100, secondX = 320)
        first.touchUpEvent(320, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `a second finger that slid 25 px in small steps before the first finger lifted is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        second.moveTo(310, 100, T0 + 30)
        second.moveTo(300, 100, T0 + 40)
        second.moveTo(305, 100, T0 + 50)
        liftFirstOfTwoFingers(first, second, T0 + 100, secondX = 305)
        first.touchUpEvent(305, 100, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a first finger that scrolled before the second finger tapped is no click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        first.moveTo(100, 130, T0 + 40) // leaves the tap box: the scroll starts
        second.touchUpEvent(300, 100, T0 + 120)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 130, T0 + 140)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("sendMouseHighResScroll(150)")
    }

    @Test
    fun `a first finger that slid 20 px before the second finger tapped still gives the right click`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        first.moveTo(100, 120, T0 + 40)
        second.touchUpEvent(300, 100, T0 + 120)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 120, T0 + 140)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `two fingers that move inside the tap box are a right click when the second lifts first`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        first.moveTo(110, 105, T0 + 30)
        second.moveTo(310, 95, T0 + 30)
        second.touchUpEvent(310, 95, T0 + 100)
        setPointerCount(1, first, second)
        first.touchUpEvent(110, 105, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `two fingers that move inside the tap box are a right click when the first lifts first`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        first.moveTo(110, 105, T0 + 30)
        second.moveTo(310, 95, T0 + 30)
        liftFirstOfTwoFingers(first, second, T0 + 100, secondX = 310, secondY = 95)
        first.touchUpEvent(310, 95, T0 + 120)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `the movement of a two-finger tap that is over does not count for the next one`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)
        second.moveTo(340, 100, T0 + 30)
        second.touchUpEvent(340, 100, T0 + 100)
        setPointerCount(1, first, second)
        first.touchUpEvent(100, 100, T0 + 120)
        setPointerCount(0, first, second)
        rig.advance(2000)
        assertThat(rig.sent()).isEmpty()

        first.start(100, 100, T0 + 1000)
        setPointerCount(2, first, second)
        assertThat(second.touchDownEvent(300, 100, T0 + 1010, true)).isTrue()
        second.touchUpEvent(300, 100, T0 + 1100)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    // A finger that lands at index 0 while the count is 2, after the first finger lifted, is a new
    // second finger for the contexts: it starts the tap time of the two fingers again, 200 ms after
    // the old finger's own start here. The old finger, now at index 1, that stayed still and lifts
    // within the new window clicks right, though the new finger is still down and lifts later.
    @Test
    fun `a new finger at index 0 restarts the tap time, so the old still finger that lifts soon clicks right`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)
        liftFirstOfTwoFingers(first, second, T0 + 20)

        setPointerCount(2, first, second)
        assertThat(first.touchDownEvent(500, 100, T0 + 200, true)).isTrue()
        second.touchUpEvent(300, 100, T0 + 400)
        assertThat(rig.sent()).containsExactly("down RIGHT")
        setPointerCount(1, first, second)
        first.touchUpEvent(500, 100, T0 + 450)
        rig.advance(2000)

        assertThat(rig.sent()).containsExactly("down RIGHT", "up RIGHT").inOrder()
    }

    @Test
    fun `the second finger starts no drag and the first one does not drag once a second finger is down`() {
        val (first, second) = rig.twoContexts()
        first.start(100, 100)
        rig.advance(300)
        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 300, true)

        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `the second finger does not move the pointer`() {
        val (first, second) = rig.twoContexts()
        twoFingers(first, second)

        assertThat(second.touchMoveEvent(300, 105, T0 + 20)).isTrue()

        assertThat(rig.sent()).isEmpty()
    }
}
