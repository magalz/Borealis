package com.limelight.binding.input.virtual_controller

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Notes what a stick tells its listeners, in order: `click`, `double`, `revoke` and `move`. */
class StickLog(private val name: String = "", private val shared: MutableList<String>? = null) :
    AnalogStick.AnalogStickListener {
    val events = mutableListOf<String>()
    val moves = mutableListOf<Pair<Float, Float>>()

    private fun note(event: String) {
        events += event
        shared?.add("$name:$event")
    }

    override fun onMovement(x: Float, y: Float) {
        moves += x to y
        note("move")
    }

    override fun onClick() = note("click")

    override fun onDoubleClick() = note("double")

    override fun onRevoke() = note("revoke")
}

/**
 * What an analog stick does with a finger: where it takes a touch, how far it says the finger has
 * gone, when it counts a click or a double click, when the dead zone lets go, and what it says when
 * the finger lifts.
 *
 * The stick of these tests is 301 pixels square on a screen 1080 pixels high, which makes the outer
 * circle 142 pixels in radius, the dead zone 45, the stick 30, and the way the stick can go
 * 112. The middle is pixel 150: the stick takes half of its width in whole pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowRecordingBridge::class])
class AnalogStickCharacterizationTest {
    private val kit = ElementRig()
    private val stick =
        kit.add(AnalogStick(kit.controller, kit.rig.activity, 12), 100, 100, 301, 301)
    private val log = StickLog().also { stick.addAnalogStickListener(it) }

    /** Touches the stick at ([dx], [dy]) from its middle, at [time]. */
    private fun at(action: Int, dx: Float, dy: Float, time: Long = FIRST) =
        stick.touch(action, MIDDLE + dx, MIDDLE + dy, time)

    private fun down(dx: Float = 0f, dy: Float = 0f, time: Long = FIRST) =
        at(MotionEvent.ACTION_DOWN, dx, dy, time)

    private fun move(dx: Float, dy: Float, time: Long) = at(MotionEvent.ACTION_MOVE, dx, dy, time)

    private fun up(dx: Float = 0f, dy: Float = 0f, time: Long = FIRST + 100) =
        at(MotionEvent.ACTION_UP, dx, dy, time)

    private fun lastMove(x: Float, y: Float) {
        assertThat(log.moves.last().first).isWithin(TOLERANCE).of(x)
        assertThat(log.moves.last().second).isWithin(TOLERANCE).of(y)
    }

    // Where a touch is taken

    @Test
    fun `a touch in the middle presses the stick and clicks`() {
        assertThat(down()).isTrue()

        assertThat(stick.isPressed).isTrue()
        assertThat(log.events).containsExactly("click")
    }

    @Test
    fun `a touch outside the outer circle is not taken, and nothing is pressed or said`() {
        assertThat(down(dx = 143f)).isFalse()

        assertThat(stick.isPressed).isFalse()
        assertThat(log.events).isEmpty()
    }

    @Test
    fun `a touch on the outer circle itself is taken`() {
        assertThat(down(dx = 142f)).isTrue()

        assertThat(stick.isPressed).isTrue()
        assertThat(log.events).containsExactly("click", "move")
    }

    @Test
    fun `the outer circle is round, not square`() {
        assertThat(down(dx = 100f, dy = 100f)).isTrue() // 141.4 from the middle
        up(100f, 100f)
        assertThat(down(dx = 101f, dy = 101f, time = FIRST + 1_000)).isFalse() // 142.8
    }

    @Test
    fun `a move or a lift outside the circle while the stick is not pressed is not taken either`() {
        assertThat(move(200f, 0f, FIRST)).isFalse()
        assertThat(up(200f, 0f)).isFalse()

        assertThat(log.events).isEmpty()
    }

    @Test
    fun `a stick so small that its stick does not fit takes no touch`() {
        val tiny = kit.add(AnalogStick(kit.controller, kit.rig.activity, 13), 500, 500, 10, 10)

        assertThat(tiny.touch(MotionEvent.ACTION_DOWN, 5f, 5f, FIRST)).isFalse()
        assertThat(tiny.isPressed).isFalse()
    }

    @Test
    fun `the middle of the stick is half of its width in whole pixels`() {
        down()

        move(10f, 0f, FIRST + 200) // at x = 160, 10 pixels from pixel 150

        lastMove(10f / 112f, 0f)
    }

    @Test
    fun `a stick wider than it is high has its circles by the shorter side and its middle in the middle`() {
        val wide = kit.add(AnalogStick(kit.controller, kit.rig.activity, 13), 500, 500, 401, 301)
        val wideLog = StickLog().also { wide.addAnalogStickListener(it) }

        wide.touch(MotionEvent.ACTION_DOWN, 300f, 150f, FIRST) // 100 pixels right of pixel 200

        assertThat(wideLog.moves.single().first).isWithin(TOLERANCE).of(100f / 112f)
        assertThat(wide.touch(MotionEvent.ACTION_UP, 300f, 150f, FIRST + 50)).isTrue()
        // 142 from the middle is taken, 143 is not
        assertThat(wide.touch(MotionEvent.ACTION_DOWN, 342f, 150f, FIRST + 1_000)).isTrue()
        assertThat(wide.touch(MotionEvent.ACTION_UP, 342f, 150f, FIRST + 1_050)).isTrue()
        assertThat(wide.touch(MotionEvent.ACTION_DOWN, 343f, 150f, FIRST + 2_000)).isFalse()
    }

    // How far the finger has gone

    @Test
    fun `the stick says how far the finger is from the middle, to the right and up as positive`() {
        down(dx = 60f, dy = -80f)

        lastMove(60f / 112f, 80f / 112f)
    }

    @Test
    fun `each quarter of the stick gives the signs of its corner`() {
        down(dx = 60f, dy = -80f) // up and right
        lastMove(0.53571f, 0.71429f)

        move(60f, 80f, FIRST + 10) // down and right
        lastMove(0.53571f, -0.71429f)

        move(-60f, 80f, FIRST + 20) // down and left
        lastMove(-0.53571f, -0.71429f)

        move(-60f, -80f, FIRST + 30) // up and left
        lastMove(-0.53571f, 0.71429f)
    }

    @Test
    fun `a finger straight to the right or to the left`() {
        down(dx = 100f)
        lastMove(0.89286f, 0f)

        move(-100f, 0f, FIRST + 10)
        lastMove(-0.89286f, 0f)
    }

    @Test
    fun `a finger on the same row as the middle gives no height`() {
        down(dx = 100f)

        assertThat(log.moves.last().second).isWithin(1e-6f).of(0f)
    }

    @Test
    fun `a finger on the very same column as the middle, up or down, is said to be the other way round`() {
        // Exactly above or below the middle the angle is the one of the opposite side: the finger
        // 100 pixels up is reported as 0.89 down, and the finger 100 pixels down as 0.89 up. A
        // finger one pixel off the column is reported the right way.
        down(dx = 0f, dy = -100f)
        lastMove(0f, -0.89286f)

        move(0f, 100f, FIRST + 10)
        lastMove(0f, 0.89286f)

        move(1f, -100f, FIRST + 20)
        lastMove(0.00893f, 0.89286f)

        move(-1f, 100f, FIRST + 30)
        lastMove(-0.00893f, -0.89286f)
    }

    @Test
    fun `the finger at the end of the way is 1 and beyond it is held at 1`() {
        down(dx = 130f) // inside the outer circle, beyond the 112 the stick can go
        lastMove(1f, 0f)

        move(600f, 0f, FIRST + 10)
        lastMove(1f, 0f)

        move(-300f, -400f, FIRST + 20) // up and left, 500 away
        lastMove(-0.6f, 0.8f)
    }

    @Test
    fun `a held stick says where the finger is on every touch`() {
        down()
        move(60f, 0f, FIRST + 200)
        move(61f, 0f, FIRST + 210)

        assertThat(log.moves).hasSize(2)
        lastMove(61f / 112f, 0f)
    }

    // The dead zone

    @Test
    fun `a finger that stays in the dead zone says nothing for 150 milliseconds`() {
        down(dx = 30f)

        move(30f, 0f, FIRST + 100)
        move(30f, 0f, FIRST + 150)

        assertThat(log.events).containsExactly("click")
    }

    @Test
    fun `a finger that stays in the dead zone speaks after 150 milliseconds`() {
        down(dx = 30f)

        move(30f, 0f, FIRST + 151)

        assertThat(log.events).containsExactly("click", "move").inOrder()
        lastMove(30f / 112f, 0f)
    }

    @Test
    fun `a finger that leaves the dead zone speaks at once`() {
        down(dx = 30f)

        move(46f, 0f, FIRST + 10)

        lastMove(46f / 112f, 0f)
    }

    @Test
    fun `the edge of the dead zone is still in it`() {
        down(dx = 45f)
        assertThat(log.events).containsExactly("click")

        move(45f, 0f, FIRST + 10)
        assertThat(log.events).containsExactly("click")

        move(46f, 0f, FIRST + 20)
        assertThat(log.events).containsExactly("click", "move")
    }

    @Test
    fun `a press outside the dead zone speaks with the press`() {
        down(dx = 60f)

        assertThat(log.events).containsExactly("click", "move").inOrder()
    }

    @Test
    fun `a stick that was active stays active when the finger goes back into the dead zone`() {
        down(dx = 60f)

        move(10f, 0f, FIRST + 10)
        move(0f, 0f, FIRST + 20)

        assertThat(log.moves).hasSize(3)
        lastMove(0f, 0f)
    }

    @Test
    fun `a new press starts in the dead zone again`() {
        down(dx = 60f)
        up(dx = 60f)
        log.events.clear()

        down(dx = 30f, time = FIRST + 1_000)

        assertThat(log.events).containsExactly("click")
    }

    @Test
    fun `the time in the dead zone is counted from the press, not from the last move`() {
        down(dx = 30f)
        move(30f, 0f, FIRST + 140)

        move(30f, 0f, FIRST + 151)

        assertThat(log.events).containsExactly("click", "move")
    }

    // Letting go

    @Test
    fun `lifting the finger says the stick is revoked and then sends the middle`() {
        down(dx = 60f)
        log.events.clear()
        log.moves.clear()

        assertThat(up(dx = 60f)).isTrue()

        assertThat(stick.isPressed).isFalse()
        assertThat(log.events).containsExactly("revoke", "move").inOrder()
        assertThat(log.moves).containsExactly(0f to 0f)
    }

    @Test
    fun `cancelling lets go the same way`() {
        down(dx = 60f)
        log.events.clear()

        assertThat(at(MotionEvent.ACTION_CANCEL, 60f, 0f, FIRST + 10)).isTrue()

        assertThat(stick.isPressed).isFalse()
        assertThat(log.events).containsExactly("revoke", "move").inOrder()
    }

    @Test
    fun `a finger lifted far outside the circle still lets go`() {
        down(dx = 60f)
        log.events.clear()

        assertThat(up(dx = 500f)).isTrue()

        assertThat(log.events).containsExactly("revoke", "move").inOrder()
        assertThat(stick.isPressed).isFalse()
    }

    @Test
    fun `a lift or a move that comes without a press, inside the circle, revokes and sends the middle`() {
        assertThat(up(dx = 10f)).isTrue()
        assertThat(log.events).containsExactly("revoke", "move").inOrder()

        log.events.clear()
        assertThat(move(10f, 0f, FIRST + 10)).isTrue()
        assertThat(log.events).containsExactly("revoke", "move").inOrder()
        assertThat(stick.isPressed).isFalse()
    }

    @Test
    fun `an action that is not a press, a move or a lift is treated as a move while the stick is held`() {
        down(dx = 60f)
        log.events.clear()

        assertThat(at(MotionEvent.ACTION_OUTSIDE, 61f, 0f, FIRST + 10)).isTrue()

        assertThat(log.events).containsExactly("move")
        assertThat(stick.isPressed).isTrue()
    }

    @Test
    fun `an action that is not a press, a move or a lift is treated as a lift when the stick is not held`() {
        assertThat(at(MotionEvent.ACTION_OUTSIDE, 10f, 0f, FIRST)).isTrue()

        assertThat(log.events).containsExactly("revoke", "move").inOrder()
    }

    @Test
    fun `a second finger is ignored, and so is its movement`() {
        down(dx = 60f)
        log.events.clear()

        assertThat(
                stick.deliver(
                    twoFingers(
                        MotionEvent.ACTION_POINTER_DOWN,
                        1,
                        210f to 150f,
                        250f to 150f,
                        FIRST + 10,
                    )
                )
            )
            .isTrue()
        assertThat(
                stick.deliver(
                    twoFingers(
                        MotionEvent.ACTION_POINTER_UP,
                        1,
                        210f to 150f,
                        250f to 150f,
                        FIRST + 20,
                    )
                )
            )
            .isTrue()

        assertThat(log.events).isEmpty()
        assertThat(stick.isPressed).isTrue()
    }

    // Clicks

    @Test
    fun `a first touch clicks when the time is long after the start`() {
        down(time = FIRST)

        assertThat(log.events).containsExactly("click")
    }

    @Test
    fun `a first touch in the first 350 milliseconds is a double click, because the last click is at time 0`() {
        down(time = 300)

        assertThat(log.events).containsExactly("double")
    }

    @Test
    fun `the first touch at 350 is a double click and at 351 is a click`() {
        down(time = 350)
        assertThat(log.events).containsExactly("double")

        val other = kit.add(AnalogStick(kit.controller, kit.rig.activity, 13), 500, 500, 301, 301)
        val otherLog = StickLog().also { other.addAnalogStickListener(it) }
        other.touch(MotionEvent.ACTION_DOWN, MIDDLE, MIDDLE, 351)
        assertThat(otherLog.events).containsExactly("click")
    }

    @Test
    fun `a second touch within 350 milliseconds of the first is a double click`() {
        down(time = 1_000)
        up(time = 1_100)

        down(time = 1_350)

        assertThat(log.events).containsExactly("click", "revoke", "move", "double").inOrder()
    }

    @Test
    fun `a second touch 351 milliseconds after the first is a click again`() {
        down(time = 1_000)
        up(time = 1_100)

        down(time = 1_351)

        assertThat(log.events).containsExactly("click", "revoke", "move", "click").inOrder()
    }

    @Test
    fun `the time to a double click is counted from the press, not from the lift`() {
        down(time = 1_000)
        up(time = 1_300) // 51 before the second press

        down(time = 1_351)

        assertThat(log.events.last()).isEqualTo("click")
    }

    @Test
    fun `a touch after a double click is a click, however quick`() {
        down(time = 1_000)
        up(time = 1_050)
        down(time = 1_100) // double
        up(time = 1_150)

        down(time = 1_200)

        assertThat(log.events.filter { it == "click" || it == "double" })
            .containsExactly("click", "double", "click")
            .inOrder()
    }

    @Test
    fun `the time to a double click is counted from the last touch that was taken`() {
        down(time = 1_000)
        up(time = 1_050)
        down(
            dx = 300f,
            time = 1_340,
        ) // outside the circle: not taken, and does not restart the time

        down(time = 1_360)

        assertThat(log.events.last()).isEqualTo("click")
    }

    // A stick whose way is nothing

    @Test
    fun `when the way the stick can go is nothing, the stick says not a number`() {
        // 20 pixels: a circle of 10 less 2 strokes of 4 is 2 pixels, the stick is 2 pixels, so the
        // 100 percent of the way is 0. The finger can only be in the middle, which divides 0 by 0.
        val small = kit.add(AnalogStick(kit.controller, kit.rig.activity, 13), 500, 500, 20, 20)
        val smallLog = StickLog().also { small.addAnalogStickListener(it) }
        small.touch(MotionEvent.ACTION_DOWN, 10f, 10f, FIRST)

        small.touch(MotionEvent.ACTION_MOVE, 10f, 10f, FIRST + 200)

        assertThat(smallLog.events).containsExactly("click", "move").inOrder()
        assertThat(smallLog.moves.single().first).isNaN()
        assertThat(smallLog.moves.single().second).isNaN()
    }

    // Listeners

    @Test
    fun `every listener is told every event, in the order they were added`() {
        val order = mutableListOf<String>()
        val first = StickLog("first", order)
        val second = StickLog("second", order)
        stick.addAnalogStickListener(first)
        stick.addAnalogStickListener(second)

        down(dx = 60f)
        up(dx = 60f)

        assertThat(order)
            .containsExactly(
                "first:click",
                "second:click",
                "first:move",
                "second:move",
                "first:revoke",
                "second:revoke",
                "first:move",
                "second:move",
            )
            .inOrder()
        assertThat(log.events).containsExactly("click", "move", "revoke", "move").inOrder()
    }

    private companion object {
        const val FIRST = 1_000L
        const val MIDDLE = 150f
        const val TOLERANCE = 1e-4f
    }
}
