package com.limelight.binding.input.virtual_controller

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.OwnedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Notes the directions that a pad tells its listeners, in order, and with the name of the listener.
 */
class PadLog(private val name: String = "", private val shared: MutableList<String>? = null) :
    DigitalPad.DigitalPadListener {
    val directions = mutableListOf<Int>()

    override fun onDirectionChange(direction: Int) {
        directions += direction
        shared?.add("$name:$direction")
    }
}

/**
 * What a directional pad does with a finger: which of the four directions a touch is in, that each
 * touch tells the listeners, and that a lift lets go of every direction.
 *
 * The pad of these tests is 330 pixels wide and 240 high, so the edges of its side thirds are at
 * 108.9 and 217.8 pixels across and at 79.2 and 158.4 pixels down: a third of the width and of the
 * height are not the same, which shows which one each direction uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowRecordingBridge::class])
class DigitalPadCharacterizationTest {
    @get:Rule val owned = OwnedResources()
    private val kit = owned.own(ElementRig())
    private val pad = kit.add(DigitalPad(kit.controller, kit.rig.activity), 100, 100, 330, 240)
    private val log = PadLog().also { pad.addDigitalPadListener(it) }

    private fun touch(action: Int, x: Float, y: Float) = pad.touch(action, x, y)

    private fun press(x: Float, y: Float) = touch(MotionEvent.ACTION_DOWN, x, y)

    private fun direction(x: Float, y: Float): Int {
        press(x, y)
        return log.directions.last()
    }

    private companion object {
        const val NONE = DigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION
        const val LEFT = DigitalPad.DIGITAL_PAD_DIRECTION_LEFT
        const val UP = DigitalPad.DIGITAL_PAD_DIRECTION_UP
        const val RIGHT = DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT
        const val DOWN = DigitalPad.DIGITAL_PAD_DIRECTION_DOWN
    }

    @Test
    fun `the directions are the bits 1, 2, 4 and 8, and the pad is the element 1`() {
        assertThat(listOf(NONE, LEFT, UP, RIGHT, DOWN)).containsExactly(0, 1, 2, 4, 8).inOrder()
        assertThat(pad.eid).isEqualTo(VirtualControllerElement.EID_DPAD)
    }

    @Test
    fun `a touch in the left third presses left, and the edge of the third is not in it`() {
        assertThat(direction(1f, 120f)).isEqualTo(LEFT)
        assertThat(direction(108.8f, 120f)).isEqualTo(LEFT)
        assertThat(direction(109f, 120f)).isEqualTo(NONE)
    }

    @Test
    fun `a touch in the right third presses right, and the edge of the third is not in it`() {
        assertThat(direction(329f, 120f)).isEqualTo(RIGHT)
        assertThat(direction(217.9f, 120f)).isEqualTo(RIGHT)
        assertThat(direction(217.7f, 120f)).isEqualTo(NONE)
    }

    @Test
    fun `a touch in the top third presses up, and the edge of the third is not in it`() {
        assertThat(direction(165f, 1f)).isEqualTo(UP)
        assertThat(direction(165f, 79.1f)).isEqualTo(UP)
        assertThat(direction(165f, 79.3f)).isEqualTo(NONE)
    }

    @Test
    fun `a touch in the bottom third presses down, and the edge of the third is not in it`() {
        assertThat(direction(165f, 239f)).isEqualTo(DOWN)
        assertThat(direction(165f, 158.5f)).isEqualTo(DOWN)
        assertThat(direction(165f, 158.3f)).isEqualTo(NONE)
    }

    @Test
    fun `a touch on the edge of a third is not in it, where the edge is a whole number of pixels`() {
        // 400 by 300: the thirds are at 132 and 264 across and at 99 and 198 down, exactly
        val round = kit.add(DigitalPad(kit.controller, kit.rig.activity), 500, 500, 400, 300)
        val roundLog = PadLog().also { round.addDigitalPadListener(it) }

        listOf(132f to 150f, 264f to 150f, 200f to 99f, 200f to 198f).forEach { (x, y) ->
            round.touch(MotionEvent.ACTION_DOWN, x, y)
        }
        listOf(131.5f to 150f, 264.5f to 150f, 200f to 98.5f, 200f to 198.5f).forEach { (x, y) ->
            round.touch(MotionEvent.ACTION_DOWN, x, y)
        }

        assertThat(roundLog.directions)
            .containsExactly(NONE, NONE, NONE, NONE, LEFT, RIGHT, UP, DOWN)
            .inOrder()
    }

    @Test
    fun `a touch in the middle presses no direction, and tells it`() {
        assertThat(direction(165f, 120f)).isEqualTo(NONE)
        assertThat(log.directions).containsExactly(0)
    }

    @Test
    fun `the corners press two directions`() {
        assertThat(direction(1f, 1f)).isEqualTo(LEFT or UP)
        assertThat(direction(329f, 1f)).isEqualTo(RIGHT or UP)
        assertThat(direction(1f, 239f)).isEqualTo(LEFT or DOWN)
        assertThat(direction(329f, 239f)).isEqualTo(RIGHT or DOWN)
    }

    @Test
    fun `a press answers that it is handled and a move changes the direction`() {
        assertThat(press(1f, 120f)).isTrue()
        assertThat(touch(MotionEvent.ACTION_MOVE, 329f, 120f)).isTrue()

        assertThat(log.directions).containsExactly(LEFT, RIGHT).inOrder()
    }

    @Test
    fun `every touch tells the direction, even when it did not change`() {
        press(1f, 120f)
        touch(MotionEvent.ACTION_MOVE, 2f, 120f)
        touch(MotionEvent.ACTION_MOVE, 3f, 120f)

        assertThat(log.directions).containsExactly(LEFT, LEFT, LEFT)
    }

    @Test
    fun `a lift lets go of every direction and tells it`() {
        press(1f, 1f)

        assertThat(touch(MotionEvent.ACTION_UP, 1f, 1f)).isTrue()

        assertThat(log.directions).containsExactly(LEFT or UP, NONE).inOrder()
    }

    @Test
    fun `a cancel lets go the same way`() {
        press(329f, 239f)

        assertThat(touch(MotionEvent.ACTION_CANCEL, 329f, 239f)).isTrue()

        assertThat(log.directions).containsExactly(RIGHT or DOWN, NONE).inOrder()
    }

    @Test
    fun `a lift tells no direction wherever the finger is`() {
        touch(MotionEvent.ACTION_UP, 1f, 1f)

        assertThat(log.directions).containsExactly(NONE)
    }

    @Test
    fun `an action that is not a press, a move or a lift is handled and tells nothing`() {
        assertThat(touch(MotionEvent.ACTION_OUTSIDE, 1f, 1f)).isTrue()

        assertThat(log.directions).isEmpty()
    }

    @Test
    fun `a second finger is ignored`() {
        press(1f, 1f)

        assertThat(
                pad.deliver(twoFingers(MotionEvent.ACTION_POINTER_DOWN, 1, 1f to 1f, 329f to 239f))
            )
            .isTrue()
        assertThat(
                pad.deliver(twoFingers(MotionEvent.ACTION_POINTER_UP, 1, 1f to 1f, 329f to 239f))
            )
            .isTrue()

        assertThat(log.directions).containsExactly(LEFT or UP)
    }

    @Test
    fun `the pad is redrawn when it is touched or lifted`() {
        shadowOf(pad).clearWasInvalidated()
        press(1f, 120f)
        assertThat(shadowOf(pad).wasInvalidated()).isTrue()

        shadowOf(pad).clearWasInvalidated()
        touch(MotionEvent.ACTION_UP, 1f, 120f)
        assertThat(shadowOf(pad).wasInvalidated()).isTrue()
    }

    @Test
    fun `every listener is told every direction, in the order they were added`() {
        val order = mutableListOf<String>()
        pad.addDigitalPadListener(PadLog("first", order))
        pad.addDigitalPadListener(PadLog("second", order))

        press(1f, 120f)
        touch(MotionEvent.ACTION_UP, 1f, 120f)

        assertThat(order).containsExactly("first:1", "second:1", "first:0", "second:0").inOrder()
        assertThat(log.directions).containsExactly(LEFT, NONE).inOrder()
    }

    @Test
    fun `the four sides are the four directions`() {
        assertThat(
                direction(1f, 120f) or
                    direction(329f, 120f) or
                    direction(165f, 1f) or
                    direction(165f, 239f)
            )
            .isEqualTo(LEFT or RIGHT or UP or DOWN)
    }
}
