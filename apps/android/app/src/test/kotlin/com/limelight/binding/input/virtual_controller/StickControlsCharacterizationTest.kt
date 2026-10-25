package com.limelight.binding.input.virtual_controller

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the left and the right stick, and the two triggers, put in the controller state and send to
 * the PC: the sticks as numbers up to 0x7FFE (cut toward zero), the clicks as the stick buttons and
 * the triggers as a full byte.
 *
 * The sticks are 301 pixels square, as in the tests of the stick itself: the way the stick can go
 * is 112 pixels, and the middle is pixel 150.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowMoonBridge::class])
class StickControlsCharacterizationTest {
    private val kit = ElementRig()
    private val controller = kit.controller
    private val context = controller.controllerInputContext
    private val left = kit.add(LeftAnalogStick(controller, kit.rig.activity), 100, 100, 301, 301)
    private val right = kit.add(RightAnalogStick(controller, kit.rig.activity), 500, 100, 301, 301)
    private val leftTrigger = kit.add(LeftTrigger(controller, 1, kit.rig.activity), 100, 500, 100, 60)
    private val rightTrigger = kit.add(RightTrigger(controller, 1, kit.rig.activity), 300, 500, 100, 60)

    private fun stick(stick: AnalogStick, action: Int, dx: Float, dy: Float, time: Long) =
        stick.touch(action, MIDDLE + dx, MIDDLE + dy, time)

    private fun leftSticks() = context.leftStickX.toInt() to context.leftStickY.toInt()

    private fun rightSticks() = context.rightStickX.toInt() to context.rightStickY.toInt()

    @Test
    fun `the sticks are the elements 12 and 13 and the triggers 2 and 3`() {
        assertThat(listOf(left.eid, right.eid, leftTrigger.eid, rightTrigger.eid))
            .containsExactly(
                VirtualControllerElement.EID_LS,
                VirtualControllerElement.EID_RS,
                VirtualControllerElement.EID_LT,
                VirtualControllerElement.EID_RT,
            )
            .inOrder()
        assertThat(left).isInstanceOf(AnalogStick::class.java)
        assertThat(right).isInstanceOf(AnalogStick::class.java)
        assertThat(leftTrigger).isInstanceOf(DigitalButton::class.java)
        assertThat(rightTrigger).isInstanceOf(DigitalButton::class.java)
    }

    // The sticks

    @Test
    fun `the left stick puts the way of the finger in the left stick numbers and sends them`() {
        stick(left, MotionEvent.ACTION_DOWN, 100f, 0f, FIRST)

        // 100 / 112 of 0x7FFE, cut: 29255.36
        assertThat(leftSticks()).isEqualTo(29_255 to 0)
        assertThat(rightSticks()).isEqualTo(0 to 0)
        assertThat(kit.rig.packets.last().leftStickX).isEqualTo(29_255)
        assertThat(kit.rig.packets.last().leftStickY).isEqualTo(0)
        assertThat(kit.rig.packets.last().rightStickX).isEqualTo(0)
    }

    @Test
    fun `the right stick puts the way of the finger in the right stick numbers and sends them`() {
        stick(right, MotionEvent.ACTION_DOWN, 100f, 0f, FIRST)

        assertThat(rightSticks()).isEqualTo(29_255 to 0)
        assertThat(leftSticks()).isEqualTo(0 to 0)
        assertThat(kit.rig.packets.last().rightStickX).isEqualTo(29_255)
        assertThat(kit.rig.packets.last().leftStickX).isEqualTo(0)
    }

    @Test
    fun `the end of the way is 0x7FFE, not 0x7FFF, to the right and to the left`() {
        stick(left, MotionEvent.ACTION_DOWN, 130f, 0f, FIRST)
        assertThat(context.leftStickX).isEqualTo(0x7FFE.toShort())

        stick(left, MotionEvent.ACTION_MOVE, -130f, 0f, FIRST + 10)
        assertThat(context.leftStickX).isEqualTo((-0x7FFE).toShort())
    }

    @Test
    fun `a stick is up for a finger that went up, and the numbers are cut toward zero`() {
        stick(left, MotionEvent.ACTION_DOWN, 60f, -80f, FIRST)
        assertThat(leftSticks()).isEqualTo(17_553 to 23_404) // 17553.2 and 23404.3

        stick(left, MotionEvent.ACTION_MOVE, -60f, 80f, FIRST + 10)
        assertThat(leftSticks()).isEqualTo(-17_553 to -23_404) // not -17554 and -23405
    }

    @Test
    fun `the numbers are cut toward zero, also where the part cut off is more than a half`() {
        stick(left, MotionEvent.ACTION_DOWN, 70f, 0f, FIRST)
        assertThat(leftSticks()).isEqualTo(20_478 to 0) // 0.625 of 0x7FFE is 20478.75, not 20479

        stick(left, MotionEvent.ACTION_MOVE, -70f, 0f, FIRST + 10)
        assertThat(leftSticks()).isEqualTo(-20_478 to 0)
    }

    @Test
    fun `the angle is the arc tangent of a quotient of floats, in each quarter`() {
        // The four fingers have the same quotient, 82 over 103. With the quotient in double
        // precision, the other number of each pair would be one less (or one more, below zero).
        stick(left, MotionEvent.ACTION_DOWN, 103f, -82f, FIRST)
        assertThat(leftSticks()).isEqualTo(25_634 to 20_408)

        stick(left, MotionEvent.ACTION_MOVE, 82f, 103f, FIRST + 10)
        assertThat(leftSticks()).isEqualTo(20_408 to -25_634)

        stick(left, MotionEvent.ACTION_MOVE, -103f, 82f, FIRST + 20)
        assertThat(leftSticks()).isEqualTo(-25_634 to -20_408)

        stick(left, MotionEvent.ACTION_MOVE, -82f, -103f, FIRST + 30)
        assertThat(leftSticks()).isEqualTo(-20_408 to 25_634)
    }

    @Test
    fun `lifting the finger puts the sticks back to zero, and what it sends is the middle`() {
        stick(right, MotionEvent.ACTION_DOWN, 100f, 0f, FIRST)
        val sent = kit.rig.packets.size

        stick(right, MotionEvent.ACTION_UP, 100f, 0f, FIRST + 100)

        assertThat(rightSticks()).isEqualTo(0 to 0)
        // the click state goes out first, then the middle
        assertThat(kit.rig.packets.drop(sent).map { it.rightStickX }).containsExactly(29_255, 0).inOrder()
    }

    @Test
    fun `a double click on the left stick presses its button until the finger lifts`() {
        stick(left, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST)
        stick(left, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 50)
        assertThat(context.inputMap.toInt()).isEqualTo(0)

        stick(left, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST + 100)
        assertThat(context.inputMap.toInt()).isEqualTo(ControllerPacket.LS_CLK_FLAG)
        assertThat(kit.rig.lastButtons()).isEqualTo(ControllerPacket.LS_CLK_FLAG)

        stick(left, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 150)
        assertThat(context.inputMap.toInt()).isEqualTo(0)
        assertThat(kit.rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `a double click on the right stick presses its button until the finger lifts`() {
        stick(right, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST)
        stick(right, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 50)
        stick(right, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST + 100)
        assertThat(context.inputMap.toInt()).isEqualTo(ControllerPacket.RS_CLK_FLAG)

        stick(right, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 150)
        assertThat(context.inputMap.toInt()).isEqualTo(0)
    }

    @Test
    fun `a single click presses nothing`() {
        stick(left, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST)

        assertThat(context.inputMap.toInt()).isEqualTo(0)
        assertThat(kit.rig.packets).isEmpty()
    }

    @Test
    fun `the stick clicks keep the other buttons, and the sign bit of the short`() {
        context.inputMap = ControllerPacket.Y_FLAG.toShort() // 0x8000, the sign bit
        context.inputMap = (context.inputMap.toInt() or ControllerPacket.A_FLAG).toShort()
        val others = context.inputMap

        stick(left, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST)
        stick(left, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 50)
        stick(left, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST + 100)
        assertThat(context.inputMap).isEqualTo((others.toInt() or ControllerPacket.LS_CLK_FLAG).toShort())
        assertThat(context.inputMap.toInt()).isLessThan(0)

        stick(left, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 150)
        assertThat(context.inputMap).isEqualTo(others)

        stick(right, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST + 500)
        stick(right, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 550)
        stick(right, MotionEvent.ACTION_DOWN, 0f, 0f, FIRST + 600)
        assertThat(context.inputMap).isEqualTo((others.toInt() or ControllerPacket.RS_CLK_FLAG).toShort())
        stick(right, MotionEvent.ACTION_UP, 0f, 0f, FIRST + 650)
        assertThat(context.inputMap).isEqualTo(others)
    }

    @Test
    fun `a stick that is too small to move says not a number, which the left stick sends as zero`() {
        val small = kit.add(LeftAnalogStick(controller, kit.rig.activity), 500, 500, 20, 20)
        small.touch(MotionEvent.ACTION_DOWN, 10f, 10f, FIRST)

        small.touch(MotionEvent.ACTION_MOVE, 10f, 10f, FIRST + 200)

        assertThat(leftSticks()).isEqualTo(0 to 0)
        assertThat(kit.rig.packets).isNotEmpty()
    }

    @Test
    fun `the sticks leave the triggers and the buttons alone`() {
        stick(left, MotionEvent.ACTION_DOWN, 100f, 0f, FIRST)
        stick(right, MotionEvent.ACTION_DOWN, 0f, 100f, FIRST)

        assertThat(context.inputMap.toInt()).isEqualTo(0)
        assertThat(context.leftTrigger.toInt()).isEqualTo(0)
        assertThat(context.rightTrigger.toInt()).isEqualTo(0)
    }

    // The triggers

    @Test
    fun `the left trigger presses its byte full, sends it, and clears it when released`() {
        kit.rig.press(leftTrigger)
        assertThat(context.leftTrigger).isEqualTo(0xFF.toByte())
        assertThat(kit.rig.packets.last().leftTrigger).isEqualTo(-1)

        kit.rig.release(leftTrigger)
        assertThat(context.leftTrigger).isEqualTo(0.toByte())
        assertThat(kit.rig.packets.last().leftTrigger).isEqualTo(0)
    }

    @Test
    fun `the right trigger presses its byte full, sends it, and clears it when released`() {
        kit.rig.press(rightTrigger)
        assertThat(context.rightTrigger).isEqualTo(0xFF.toByte())
        assertThat(kit.rig.packets.last().rightTrigger).isEqualTo(-1)
        assertThat(context.leftTrigger).isEqualTo(0.toByte())

        kit.rig.release(rightTrigger)
        assertThat(context.rightTrigger).isEqualTo(0.toByte())
        assertThat(kit.rig.packets.last().rightTrigger).isEqualTo(0)
    }

    @Test
    fun `a trigger does not press a button or move a stick`() {
        kit.rig.press(leftTrigger)
        kit.rig.press(rightTrigger)

        assertThat(context.inputMap.toInt()).isEqualTo(0)
        assertThat(leftSticks()).isEqualTo(0 to 0)
        assertThat(rightSticks()).isEqualTo(0 to 0)
    }

    @Test
    fun `a trigger held for three seconds changes nothing, where a button would long click`() {
        kit.rig.press(leftTrigger)
        val sent = kit.rig.packets.size

        kit.rig.advanceBy(10_000)

        // only the retransmits of the press: three more, of the same state
        assertThat(kit.rig.packets.size - sent).isEqualTo(3)
        assertThat(kit.rig.packets.map { it.leftTrigger }.distinct()).containsExactly(-1)
    }

    @Test
    fun `a right trigger held for three seconds changes nothing either`() {
        kit.rig.press(rightTrigger)
        val sent = kit.rig.packets.size

        kit.rig.advanceBy(10_000)

        assertThat(kit.rig.packets.size - sent).isEqualTo(3)
        assertThat(kit.rig.packets.map { it.rightTrigger }.distinct()).containsExactly(-1)
    }

    @Test
    fun `a finger slid onto a trigger of its layer presses it, and one of another layer does not`() {
        val id = VirtualControllerElement.EID_A
        val button = kit.add(DigitalButton(controller, id, 1, kit.rig.activity), 100, 700, 100, 60)
        val other = kit.add(LeftTrigger(controller, 2, kit.rig.activity), 300, 700, 100, 60)
        kit.rig.press(button)

        // the middle of the trigger of layer 1, then of the one of layer 2, in the coordinates of the button
        button.touch(MotionEvent.ACTION_MOVE, leftTrigger.x - button.x + 50f, leftTrigger.y - button.y + 30f)
        assertThat(context.leftTrigger).isEqualTo(0xFF.toByte())
        button.touch(MotionEvent.ACTION_MOVE, other.x - button.x + 50f, other.y - button.y + 30f)

        assertThat(leftTrigger.isPressed).isFalse() // the finger left it
        assertThat(other.isPressed).isFalse()
        assertThat(context.leftTrigger).isEqualTo(0.toByte())
    }

    private companion object {
        const val FIRST = 1_000L
        const val MIDDLE = 150f
    }
}
