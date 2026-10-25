// The package name is the inherited one: ADR 0059 keeps it, and it has an underscore.
@file:Suppress("PackageNaming")

package com.limelight.binding.input.virtual_controller

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the loader makes the controls of the default layout do when they are touched: the buttons
 * press and release their flags (and keep pressing while they are held), the pad presses its four
 * directions, and each change goes to the handler with the retransmits after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class ControlListenersCharacterizationTest {
    private val rig = OscRig().also { it.settings() }
    private val controller = rig.controller

    init {
        controller.refreshLayout()
        rig.layOut()
    }

    private val pad = rig.element(VirtualControllerElement.EID_DPAD) as DigitalPad
    private val a = rig.element(VirtualControllerElement.EID_A)
    private val buttons = { rig.packets.map { it.buttons } }

    private fun inputMap() = controller.controllerInputContext.inputMap.toInt()

    // Buttons

    @Test
    fun `touching a button presses its flag and sends it`() {
        rig.press(a)

        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG)
        assertThat(buttons()).containsExactly(ControllerPacket.A_FLAG)
    }

    @Test
    fun `letting go of a button releases its flag and sends that`() {
        rig.press(a)
        rig.release(a)

        assertThat(inputMap()).isEqualTo(0)
        assertThat(buttons()).containsExactly(ControllerPacket.A_FLAG, 0).inOrder()
    }

    @Test
    fun `a held button keeps its flag when another button is pressed and released`() {
        val lb = rig.element(VirtualControllerElement.EID_LB)
        rig.press(a)

        rig.press(lb)
        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG or ControllerPacket.LB_FLAG)
        rig.release(lb)
        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG)
    }

    @Test
    fun `a button held for three seconds sends its state once more, and again three times after that`() {
        rig.press(a)
        rig.advanceBy(2_999)
        assertThat(rig.packets).hasSize(4) // the press and its three retransmits

        rig.advanceBy(1)
        assertThat(rig.packets).hasSize(5) // the long click

        rig.advanceBy(75)
        assertThat(rig.packets).hasSize(8)
        assertThat(buttons().distinct()).containsExactly(ControllerPacket.A_FLAG)
    }

    @Test
    fun `a button let go before three seconds has no long click`() {
        rig.press(a)
        rig.advanceBy(100)
        rig.release(a)

        rig.advanceBy(10_000)

        // the press and its three, the release and its three
        assertThat(rig.packets).hasSize(8)
        assertThat(inputMap()).isEqualTo(0)
    }

    @Test
    fun `letting go of a button cancels the retransmits of its press`() {
        rig.press(a)
        rig.advanceBy(30)
        assertThat(rig.packets).hasSize(2)

        rig.release(a)
        rig.advanceBy(10_000)

        // the press, one retransmit, the release and its three; the 50 and 75 of the press are gone
        assertThat(buttons()).containsExactly(
            ControllerPacket.A_FLAG, ControllerPacket.A_FLAG, 0, 0, 0, 0,
        ).inOrder()
    }

    @Test
    fun `Y presses the sign bit of the short`() {
        val y = rig.element(VirtualControllerElement.EID_Y)

        rig.press(y)
        assertThat(controller.controllerInputContext.inputMap).isEqualTo(Short.MIN_VALUE)
        assertThat(inputMap()).isEqualTo(-0x8000)

        rig.release(y)
        assertThat(inputMap()).isEqualTo(0)
    }

    @Test
    fun `a button and the others do not change the sticks or the triggers`() {
        rig.press(a)

        val context = controller.controllerInputContext
        assertThat(listOf(context.leftTrigger.toInt(), context.rightTrigger.toInt())).containsExactly(0, 0)
        assertThat(
                listOf(
                    context.leftStickX.toInt(),
                    context.leftStickY.toInt(),
                    context.rightStickX.toInt(),
                    context.rightStickY.toInt(),
                )
            )
            .containsExactly(0, 0, 0, 0)
    }

    // Sliding from one button to another

    /** Where the middle of [element] is on the screen. */
    private fun middleOf(element: VirtualControllerElement) =
        (element.x + element.width / 2) to (element.y + element.height / 2)

    /** Moves the finger that is on [from] to the middle of [to]. */
    private fun slide(from: VirtualControllerElement, to: VirtualControllerElement) {
        val (x, y) = middleOf(to)
        rig.touch(from, MotionEvent.ACTION_MOVE, x - from.x, y - from.y)
    }

    @Test
    fun `a finger slid from one button onto another of the same layer presses it too`() {
        val b = rig.element(VirtualControllerElement.EID_B)
        rig.press(a)

        slide(a, b)

        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG or ControllerPacket.B_FLAG)
    }

    @Test
    fun `a finger slid from a face button onto Back or Start presses nothing there`() {
        rig.press(a)

        slide(a, rig.element(VirtualControllerElement.EID_BACK))
        slide(a, rig.element(VirtualControllerElement.EID_START))

        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG)
    }

    @Test
    fun `a finger slid from Back onto Start presses nothing, because they have layers of their own`() {
        val back = rig.element(VirtualControllerElement.EID_BACK)
        rig.press(back)

        slide(back, rig.element(VirtualControllerElement.EID_START))

        assertThat(inputMap()).isEqualTo(ControllerPacket.BACK_FLAG)
    }

    @Test
    fun `a finger slid from Start onto a face button presses nothing there`() {
        val start = rig.element(VirtualControllerElement.EID_START)
        rig.press(start)

        slide(start, rig.element(VirtualControllerElement.EID_B))

        assertThat(inputMap()).isEqualTo(ControllerPacket.PLAY_FLAG)
    }

    @Test
    fun `a finger slid off a button lets go of the button it pressed by sliding`() {
        val b = rig.element(VirtualControllerElement.EID_B)
        rig.press(a)
        slide(a, b)

        rig.touch(a, MotionEvent.ACTION_MOVE, -1_000f, -1_000f)

        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG)
    }

    // The pad

    private fun padAt(x: Float, y: Float, action: Int = MotionEvent.ACTION_DOWN) = rig.touch(pad, action, x, y)

    @Test
    fun `the pad is as big as the layout made it`() {
        assertThat(pad.width).isEqualTo(450)
        assertThat(pad.height).isEqualTo(450)
    }

    @Test
    fun `touching the left of the pad presses left, the right presses right, the top up and the bottom down`() {
        padAt(10f, 225f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.LEFT_FLAG)
        padAt(440f, 225f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.RIGHT_FLAG)
        padAt(225f, 10f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.UP_FLAG)
        padAt(225f, 440f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.DOWN_FLAG)
    }

    @Test
    fun `the middle of the pad presses nothing`() {
        padAt(225f, 225f)

        assertThat(inputMap()).isEqualTo(0)
        assertThat(buttons()).containsExactly(0)
    }

    @Test
    fun `the corners of the pad press two directions`() {
        padAt(10f, 10f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.LEFT_FLAG or ControllerPacket.UP_FLAG)
        padAt(440f, 10f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.RIGHT_FLAG or ControllerPacket.UP_FLAG)
        padAt(10f, 440f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.LEFT_FLAG or ControllerPacket.DOWN_FLAG)
        padAt(440f, 440f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.RIGHT_FLAG or ControllerPacket.DOWN_FLAG)
    }

    @Test
    fun `sliding on the pad changes the directions and lets go of the ones it left`() {
        padAt(10f, 225f)
        padAt(225f, 10f, MotionEvent.ACTION_MOVE)

        assertThat(inputMap()).isEqualTo(ControllerPacket.UP_FLAG)
        assertThat(buttons()).containsExactly(ControllerPacket.LEFT_FLAG, ControllerPacket.UP_FLAG).inOrder()
    }

    @Test
    fun `lifting the finger from the pad lets go of every direction`() {
        padAt(10f, 10f)

        padAt(10f, 10f, MotionEvent.ACTION_UP)
        assertThat(inputMap()).isEqualTo(0)

        padAt(440f, 440f)
        padAt(440f, 440f, MotionEvent.ACTION_CANCEL)
        assertThat(inputMap()).isEqualTo(0)
    }

    @Test
    fun `the pad leaves the other buttons as they are`() {
        rig.press(a)

        padAt(10f, 225f)
        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG or ControllerPacket.LEFT_FLAG)
        padAt(10f, 225f, MotionEvent.ACTION_UP)
        assertThat(inputMap()).isEqualTo(ControllerPacket.A_FLAG)
    }

    @Test
    fun `each change of the pad is sent with the retransmits after it`() {
        padAt(10f, 225f)
        rig.advanceBy(10_000)

        val left = ControllerPacket.LEFT_FLAG
        assertThat(buttons()).containsExactly(left, left, left, left)
    }

    // Other controls

    @Test
    fun `the triggers set and clear their byte, and send it`() {
        val lt = rig.element(VirtualControllerElement.EID_LT)

        rig.press(lt)
        assertThat(rig.packets.last().leftTrigger).isEqualTo(-1)
        rig.release(lt)
        assertThat(rig.packets.last().leftTrigger).isEqualTo(0)
    }
}
