package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the direct-touch context sends to the host for a sequence of touches: the click that waits
 * out the dead zone, the double tap that does not move the pointer, the long press that turns into
 * a right click, the scroll of the second finger, the clamp to the view, in the exact order and
 * with the exact arguments of the bridge calls. The thresholds sit on both sides of each boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class AbsoluteTouchContextTest {
    private val rig = TouchRig()

    /** The position of the pointer in a view of [width] by [height]. */
    private fun position(x: Int, y: Int, width: Int = 1280, height: Int = 720) =
        "sendMousePosition($x, $y, $width, $height)"

    /**
     * What a touch at [x], [y] at [time] sends once the dead zone has passed, after a first tap at
     * (100, 100) that was lifted at `T0 + 50`.
     */
    private fun secondTouchAfterTap(x: Int, y: Int, time: Long): List<String> {
        val rig = TouchRig()
        val context = rig.absolute()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 50)
        rig.advance(100)
        rig.forget()

        context.start(x, y, time)
        rig.advance(100)
        val sent = rig.sent()
        context.cancelTouch() // the timers of a touch go to the main looper that the next call shares
        return sent
    }

    @Test
    fun `the context knows its pointer`() {
        assertThat(rig.absolute(0).actionIndex).isEqualTo(0)
        assertThat(rig.absolute(1).actionIndex).isEqualTo(1)
    }

    @Test
    fun `the timers run on the main looper, whichever thread built the context`() {
        val built = AtomicReference<AbsoluteTouchContext>()
        Thread { built.set(rig.absolute()) }.apply {
            start()
            join()
        }
        val context = built.get()
        assertThat(context).isNotNull()

        context.start(100, 100)
        rig.advance(100)

        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()
    }

    // The click

    @Test
    fun `a lift within the dead zone clicks at the place of the touch down, released 100 ms later`() {
        val context = rig.absolute()
        context.start(100, 200)
        rig.advance(50)

        context.touchUpEvent(103, 204, T0 + 50)

        assertThat(rig.sent()).containsExactly(position(100, 200), "down LEFT").inOrder()
        rig.advance(99)
        assertThat(rig.sent()).containsExactly(position(100, 200), "down LEFT").inOrder()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly(position(100, 200), "down LEFT", "up LEFT").inOrder()
        rig.advance(2000) // no right click of a long press, no second click
        assertThat(rig.sent()).containsExactly(position(100, 200), "down LEFT", "up LEFT").inOrder()
    }

    @Test
    fun `a touch held past the dead zone of 100 ms presses the left button, the lift releases it`() {
        val context = rig.absolute()
        context.start(100, 200)

        rig.advance(99)
        assertThat(rig.sent()).isEmpty()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly(position(100, 200), "down LEFT").inOrder()

        context.touchUpEvent(100, 200, T0 + 300)
        rig.advance(2000) // the long press was removed, and there is no release to wait for
        assertThat(rig.sent())
            .containsExactly(position(100, 200), "down LEFT", "up LEFT")
            .inOrder()
    }

    @Test
    fun `a move of 20 px or less in the dead zone is ignored, one of more starts the click`() {
        val context = rig.absolute()
        context.start(100, 100)

        context.moveTo(112, 116) // 20 px in a straight line
        assertThat(rig.sent()).isEmpty()

        // The click is at the touch down, then the pointer goes where the finger is
        context.moveTo(112, 117)
        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", position(112, 117))
            .inOrder()
        rig.advance(100) // the timer of the dead zone was removed
        assertThat(rig.sent()).hasSize(3)
    }

    @Test
    fun `a move in the dead zone does not stop the click when the dead zone ends`() {
        val context = rig.absolute()
        context.start(100, 100)
        context.moveTo(110, 100)

        rig.advance(100)

        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()
    }

    @Test
    fun `a far move at once is a click and a pointer move, and no long press`() {
        val context = rig.absolute()
        context.start(100, 100)

        context.moveTo(100, 140)
        rig.advance(2000)

        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", position(100, 140))
            .inOrder()
    }

    @Test
    fun `after the click started every move sends the position`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)
        rig.forget()

        context.moveTo(101, 100)
        context.moveTo(200, 300)

        assertThat(rig.sent()).containsExactly(position(101, 100), position(200, 300)).inOrder()
    }

    @Test
    fun `the position is clamped to the view`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)
        rig.forget()

        context.moveTo(-5, 10)
        context.moveTo(1300, 10)
        context.moveTo(10, -3)
        context.moveTo(10, 800)
        context.moveTo(1280, 720)
        context.moveTo(1279, 719)

        assertThat(rig.sent())
            .containsExactly(
                position(0, 10),
                position(1280, 10),
                position(10, 0),
                position(10, 720),
                position(1280, 720),
                position(1279, 719),
            )
            .inOrder()
    }

    @Test
    fun `the place of the touch down is clamped too`() {
        val context = rig.absolute()
        context.start(-5, 800)

        context.touchUpEvent(-5, 800, T0 + 50)

        assertThat(rig.sent()).containsExactly(position(0, 720), "down LEFT").inOrder()
    }

    @Test
    fun `the position and the size of the view are shorts`() {
        val rig = TouchRig(width = 70000, height = 40000)
        val context = rig.absolute()
        context.start(65540, 35000)

        context.touchUpEvent(65540, 35000, T0 + 50)

        // 65540, 35000, 70000 and 40000 each lose 65536
        assertThat(rig.sent())
            .containsExactly(position(4, -30536, 4464, -25536), "down LEFT")
            .inOrder()
    }

    @Test
    fun `a finger that is not new starts nothing`() {
        val context = rig.absolute()

        assertThat(context.touchDownEvent(100, 100, T0, false)).isTrue()
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a second quick tap replaces the pending release of the first`() {
        val context = rig.absolute()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 50)
        rig.advance(50)

        context.start(100, 100, T0 + 150)
        context.touchUpEvent(100, 100, T0 + 200)

        // The release of the first tap was due now: the second tap removed it
        rig.advance(99)
        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT", "down LEFT").inOrder()
        rig.advance(1)
        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", "down LEFT", "up LEFT")
            .inOrder()
        rig.advance(2000)
        assertThat(rig.sent()).hasSize(4)
    }

    @Test
    fun `a touch down clears what the touch before it left`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(700)
        context.touchUpEvent(100, 100, T0 + 700)
        rig.forget()

        context.start(100, 100, T0 + 2000)
        context.touchUpEvent(100, 100, T0 + 2050)

        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()
    }

    @Test
    fun `a new touch down replaces the pending timers`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(50)
        context.start(100, 100, T0 + 50)

        rig.advance(99) // 149 ms: the dead zone of the first touch ended at 100
        assertThat(rig.sent()).isEmpty()
        rig.advance(1)
        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()

        rig.advance(499) // 649 ms: the long press of the first touch was due at 650
        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()
        rig.advance(51)
        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", "up LEFT", "down RIGHT")
            .inOrder()
    }

    // The double tap

    @Test
    fun `a touch 250 ms after the last lift, and 60 px from it, is a double tap at the pointer`() {
        assertThat(secondTouchAfterTap(110, 100, T0 + 300)).containsExactly("down LEFT")
        assertThat(secondTouchAfterTap(160, 100, T0 + 300)).containsExactly("down LEFT")
        assertThat(secondTouchAfterTap(100, 160, T0 + 300)).containsExactly("down LEFT")
        // 36 and 48 are 60 in a straight line
        assertThat(secondTouchAfterTap(136, 148, T0 + 300)).containsExactly("down LEFT")
    }

    @Test
    fun `a touch 251 ms after the last lift moves the pointer first`() {
        assertThat(secondTouchAfterTap(110, 100, T0 + 301))
            .containsExactly(position(110, 100), "down LEFT")
            .inOrder()
    }

    @Test
    fun `a touch more than 60 px from the last lift moves the pointer first`() {
        assertThat(secondTouchAfterTap(161, 100, T0 + 300))
            .containsExactly(position(161, 100), "down LEFT")
            .inOrder()
        assertThat(secondTouchAfterTap(100, 161, T0 + 300))
            .containsExactly(position(100, 161), "down LEFT")
            .inOrder()
        assertThat(secondTouchAfterTap(136, 149, T0 + 300))
            .containsExactly(position(136, 149), "down LEFT")
            .inOrder()
    }

    // The long press

    @Test
    fun `a touch held for 650 ms turns the left button into the right button, the lift raises it`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)
        val click = listOf(position(100, 100), "down LEFT")

        rig.advance(549)
        assertThat(rig.sent()).containsExactlyElementsIn(click).inOrder()
        rig.advance(1)
        assertThat(rig.sent()).containsExactlyElementsIn(click + listOf("up LEFT", "down RIGHT")).inOrder()

        context.touchUpEvent(100, 100, T0 + 700)
        rig.advance(2000)
        assertThat(rig.sent())
            .containsExactlyElementsIn(click + listOf("up LEFT", "down RIGHT", "up RIGHT"))
            .inOrder()
    }

    @Test
    fun `a move of 30 px from the touch down keeps the long press`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)

        context.moveTo(100, 130)
        rig.advance(550)

        assertThat(rig.sent())
            .containsExactly(
                position(100, 100),
                "down LEFT",
                position(100, 130),
                "up LEFT",
                "down RIGHT",
            )
            .inOrder()
    }

    @Test
    fun `a move of more than 30 px from the touch down, in steps, takes the long press away`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)

        context.moveTo(100, 120)
        context.moveTo(100, 131)
        rig.advance(2000)

        assertThat(rig.sent())
            .containsExactly(
                position(100, 100),
                "down LEFT",
                position(100, 120),
                position(100, 131),
            )
            .inOrder()
    }

    @Test
    fun `a move of 30 px in a straight line from the touch down keeps the long press`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)

        context.moveTo(124, 118) // 24 and 18 make 30
        rig.advance(550)

        assertThat(rig.sent().takeLast(2)).containsExactly("up LEFT", "down RIGHT").inOrder()
    }

    @Test
    fun `a move of just over 30 px in a straight line from the touch down takes the long press away`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)

        context.moveTo(124, 119) // 24 and 19 make 30.6
        rig.advance(2000)

        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", position(124, 119))
            .inOrder()
    }

    // The scroll

    @Test
    fun `the second finger scrolls three times the y delta from its last place`() {
        val context = rig.absolute(1)
        context.start(200, 300)
        rig.advance(2000) // the second finger has no timers

        context.moveTo(200, 310)
        context.moveTo(200, 305)
        context.moveTo(210, 305)
        context.moveTo(210, 11305)

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseHighResScroll(30)",
                "sendMouseHighResScroll(-15)",
                "sendMouseHighResScroll(0)",
                "sendMouseHighResScroll(-32536)", // 11000 * 3 = 33000, which wraps a short
            )
            .inOrder()
    }

    @Test
    fun `the lift of the second finger sends nothing and moves its last place`() {
        val context = rig.absolute(1)
        context.start(200, 300)

        context.touchUpEvent(200, 500, T0 + 50)
        assertThat(rig.sent()).isEmpty()
        context.moveTo(200, 510)

        assertThat(rig.sent()).containsExactly("sendMouseHighResScroll(30)")
    }

    @Test
    fun `a second finger that is not new does not move its last place`() {
        val context = rig.absolute(1)
        context.start(200, 300)

        assertThat(context.touchDownEvent(200, 400, T0 + 1, false)).isTrue()
        context.moveTo(200, 310)

        assertThat(rig.sent()).containsExactly("sendMouseHighResScroll(30)")
    }

    // The cancel

    @Test
    fun `cancelling before the dead zone ends removes the timers and sends nothing`() {
        val context = rig.absolute()
        context.start(100, 100)
        assertThat(context.isCancelled).isFalse()

        context.cancelTouch()
        rig.advance(2000)

        assertThat(context.isCancelled).isTrue()
        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `cancelling a click raises the left button`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)

        context.cancelTouch()

        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT", "up LEFT").inOrder()
    }

    @Test
    fun `cancelling a long press raises the right button`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(650)

        context.cancelTouch()

        assertThat(rig.sent())
            .containsExactly(position(100, 100), "down LEFT", "up LEFT", "down RIGHT", "up RIGHT")
            .inOrder()
    }

    @Test
    fun `a cancelled touch ignores the moves and the lift`() {
        val context = rig.absolute()
        context.start(100, 100)
        rig.advance(100)
        context.cancelTouch()
        rig.forget()

        context.moveTo(200, 200)
        context.touchUpEvent(200, 200, T0 + 300)
        rig.advance(2000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a cancelled touch does not count as the last lift of a double tap`() {
        val context = rig.absolute()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 50)
        rig.advance(100)
        context.start(500, 500, T0 + 100)
        context.cancelTouch()
        context.touchUpEvent(900, 900, T0 + 200)
        rig.forget()

        context.start(110, 100, T0 + 300) // 250 ms after the lift at T0 + 50, 10 px from it
        rig.advance(100)

        assertThat(rig.sent()).containsExactly("down LEFT")
    }

    @Test
    fun `a new finger clears the cancel, one that is not new does not`() {
        val context = rig.absolute()
        context.start(100, 100)
        context.cancelTouch()

        context.touchDownEvent(100, 100, T0 + 100, false)
        assertThat(context.isCancelled).isTrue()
        context.start(100, 100, T0 + 200)

        assertThat(context.isCancelled).isFalse()
    }

    @Test
    fun `a second pointer cancels the first finger and leaves the second one alone`() {
        val first = rig.absolute(0)
        val second = rig.absolute(1)
        first.start(100, 100)
        rig.advance(100)

        first.setPointerCount(1)
        second.setPointerCount(1)
        assertThat(first.isCancelled).isFalse()
        first.setPointerCount(0)
        assertThat(first.isCancelled).isFalse()
        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT").inOrder()

        second.setPointerCount(2)
        assertThat(second.isCancelled).isFalse()
        first.setPointerCount(2)

        assertThat(first.isCancelled).isTrue()
        assertThat(rig.sent()).containsExactly(position(100, 100), "down LEFT", "up LEFT").inOrder()
    }
}
