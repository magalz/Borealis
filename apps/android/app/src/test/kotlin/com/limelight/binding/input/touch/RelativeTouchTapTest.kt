package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the trackpad context sends to the host for a tap and for the right button of the second
 * finger: the thresholds of the tap box (20 px), of the cumulative move (25 px) and of the time
 * (250 ms), the release 100 ms later, and the pending release that a second tap replaces.
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

    @Test
    fun `the release runnables are indexed by the button number minus one`() {
        val context = rig.relative()
        val field = RelativeTouchContext::class.java.getDeclaredField("buttonUpRunnables")
        field.isAccessible = true
        val runnables = field.get(context) as Array<*>

        runnables.forEach { (it as Runnable).run() }

        assertThat(runnables).hasLength(5)
        assertThat(rig.sent())
            .containsExactly("up LEFT", "up MIDDLE", "up RIGHT", "up X1", "up X2")
            .inOrder()
    }

    // The right button

    @Test
    fun `a tap of the second finger is a right click, and the first finger then clicks nothing`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
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
    fun `a second finger that moves 24 px in small steps still taps`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        second.moveTo(310, 100)
        second.moveTo(300, 100)
        second.moveTo(304, 100)
        second.touchUpEvent(304, 100, T0 + 100)

        assertThat(rig.sent()).containsExactly("down RIGHT")
    }

    @Test
    fun `a second finger that moves 25 px in small steps along x is no tap`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
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
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        second.moveTo(300, 110)
        second.moveTo(300, 100)
        second.moveTo(300, 105)
        second.touchUpEvent(300, 105, T0 + 100)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `the second finger starts no drag and the first one does not drag once a second finger is down`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        first.start(100, 100)
        rig.advance(300)
        setPointerCount(2, first, second)
        second.touchDownEvent(300, 100, T0 + 300, true)

        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `the second finger does not move the pointer`() {
        val first = rig.relative(0)
        val second = rig.relative(1)
        twoFingers(first, second)

        assertThat(second.touchMoveEvent(300, 105, T0 + 20)).isTrue()

        assertThat(rig.sent()).isEmpty()
    }
}
