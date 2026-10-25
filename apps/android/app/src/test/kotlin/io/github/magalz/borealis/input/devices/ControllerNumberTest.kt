package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_BUTTON_Y
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Test

/**
 * A pad gets its controller number with its first controller packet: built-in buttons are
 * controller 0, an external pad takes the lowest free number when multi-controller is on, and a
 * device that is only a part of a pad takes the number of the joystick next to it. The handler
 * tells the host about the new controller (the arrival report) before the packet goes out.
 */
class ControllerNumberTest : DevicesTestBase() {
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    private fun DevicesRig.arrivalControllers() = connection.arrivals.map { it.controller }

    private fun DevicesRig.arrivalMasks() = connection.arrivals.map { it.activeMask }

    @Test
    fun `an internal pad is controller 0 and reserves no number`() {
        val internal = pad(1, external = false)
        val rig = rig(multiController = true, devices = listOf(internal))

        rig.press(internal, KEYCODE_BUTTON_A)

        val context = rig.context(internal)
        assertThat(context.short("controllerNumber")).isEqualTo(0)
        assertThat(context.bool("assignedControllerNumber")).isTrue()
        assertThat(context.bool("reservedControllerNumber")).isFalse()
        assertThat(rig.connection.arrivals).hasSize(1)
        assertThat(rig.arrivalControllers()).containsExactly(0)
    }

    @Test
    fun `an internal pad takes no number from the ones other pads reserve`() {
        val internal = pad(1, external = false)
        val external = pad(2)
        val rig = rig(multiController = true, devices = listOf(internal, external))

        rig.press(internal, KEYCODE_BUTTON_A)
        rig.press(external, KEYCODE_BUTTON_A)

        assertThat(rig.context(external).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.context(external).bool("reservedControllerNumber")).isTrue()
    }

    @Test
    fun `an external pad with joystick axes takes the lowest free number`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        val third = pad(3)
        listOf(first, second, third).forEach(rig::connect)

        // Their order of first input, not of device id
        rig.press(second, KEYCODE_BUTTON_A)
        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(third, KEYCODE_BUTTON_A)

        assertThat(rig.arrivalControllers()).containsExactly(0, 1, 2).inOrder()
        assertThat(rig.context(second).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.context(first).short("controllerNumber")).isEqualTo(1)
        assertThat(rig.context(third).short("controllerNumber")).isEqualTo(2)
        assertThat(rig.context(first).bool("reservedControllerNumber")).isTrue()
    }

    @Test
    fun `the active mask grows with each reserved number`() {
        val rig = rig(multiController = true)
        val pads = listOf(pad(1), pad(2), pad(3))
        pads.forEach(rig::connect)

        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        assertThat(rig.arrivalMasks()).containsExactly(0b1, 0b11, 0b111).inOrder()
        assertThat(rig.connection.packets.map { it.activeMask }).containsExactly(1, 3, 7).inOrder()
    }

    @Test
    fun `a pad that never sent input has no number`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)

        rig.press(second, KEYCODE_BUTTON_A)

        assertThat(rig.context(second).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.contextOf(first)).isNull()
        assertThat(rig.connection.arrivals).hasSize(1)
    }

    @Test
    fun `a number is assigned once`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)

        rig.press(first, KEYCODE_BUTTON_A)
        rig.release(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_A)
        rig.press(first, KEYCODE_BUTTON_A)

        assertThat(rig.arrivalControllers()).containsExactly(0, 1).inOrder()
        assertThat(rig.context(first).short("controllerNumber")).isEqualTo(0)
    }

    @Test
    fun `a freed number is the next one taken`() {
        val rig = rig(multiController = true)
        val pads = (1..3).map { pad(it) }
        pads.forEach(rig::connect)
        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        rig.disconnect(pads[1])
        val newcomer = pad(4)
        rig.connect(newcomer)
        rig.press(newcomer, KEYCODE_BUTTON_A)

        assertThat(rig.context(newcomer).short("controllerNumber")).isEqualTo(1)
        assertThat(rig.arrivalControllers().last()).isEqualTo(1)
        assertThat(rig.arrivalMasks().last()).isEqualTo(0b111)
    }

    @Test
    fun `the seventeenth pad shares controller 0 because only sixteen numbers exist`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { pad(it) }
        pads.forEach(rig::connect)

        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        val numbers = pads.map { rig.context(it).short("controllerNumber") }
        assertThat(numbers.take(16)).containsExactlyElementsIn((0..15).toList()).inOrder()
        assertThat(numbers.last()).isEqualTo(0)
        assertThat(rig.context(pads.last()).bool("reservedControllerNumber")).isFalse()
        assertThat(rig.context(pads.last()).bool("assignedControllerNumber")).isTrue()
        assertThat(rig.connection.arrivals).hasSize(17)
        // All sixteen bits set read as a negative short
        assertThat(rig.arrivalMasks().last()).isEqualTo(-1)
        assertThat(rig.arrivalMasks()[14]).isEqualTo(0x7FFF)
    }

    @Test
    fun `without multi-controller every pad is controller 0 and the mask is one`() {
        val rig = rig(multiController = false)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)

        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_A)

        assertThat(rig.arrivalControllers()).containsExactly(0, 0).inOrder()
        assertThat(rig.arrivalMasks()).containsExactly(1, 1).inOrder()
        assertThat(rig.context(second).bool("reservedControllerNumber")).isFalse()
        assertThat(rig.context(second).bool("assignedControllerNumber")).isTrue()
    }

    @Test
    fun `the on-screen controller bit is part of the active mask`() {
        val rig = rig(multiController = true) { onscreenController = true }
        val first = pad(1)
        rig.connect(first)

        rig.press(first, KEYCODE_BUTTON_A)

        assertThat(rig.arrivalMasks()).containsExactly(0b1)
    }

    @Test
    fun `an internal pad reports an empty active mask unless it was attached at the start`() {
        val late = pad(1, external = false)
        val rig = rig(multiController = true)
        rig.connect(late)

        rig.press(late, KEYCODE_BUTTON_A)

        // Nothing reserved a number and no pad was counted when the handler was made
        assertThat(rig.arrivalMasks()).containsExactly(0)
        assertThat(rig.connection.packets.single().activeMask).isEqualTo(0)
    }

    @Test
    fun `a pad attached at the start keeps its bit until its number is taken`() {
        val first = pad(1)
        val second = pad(2)
        val rig = rig(multiController = true, devices = listOf(first, second))

        rig.press(second, KEYCODE_BUTTON_A)

        // Both pads were counted at the start; the first one's bit stays until it sends input
        assertThat(rig.arrivalMasks()).containsExactly(0b11)
        assertThat(rig.context(second).short("controllerNumber")).isEqualTo(0)
    }

    @Test
    fun `removing a reserved pad frees its bit even if it was counted at the start`() {
        val first = pad(1)
        val second = pad(2)
        val rig = rig(multiController = true, devices = listOf(first, second))
        rig.press(first, KEYCODE_BUTTON_A)

        rig.disconnect(first)

        // The bit of the pad that took number 0 left the start set when it took the number
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 0b10, buttons = 0))
    }

    @Test
    fun `removing a pad zeroes its controller on the host`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)
        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_A)

        rig.disconnect(second)

        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 1, activeMask = 0b1, buttons = 0))
    }

    @Test
    fun `removing a pad without a reserved number zeroes controller 0 and keeps its bit`() {
        val rig = rig(multiController = false)
        val only = pad(1)
        rig.connect(only)
        rig.press(only, KEYCODE_BUTTON_A)

        rig.disconnect(only)

        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = 0))
        // The controller stays one the host has heard input from: the capture neutralises it
        rig.connection.packets.clear()
        rig.handler.setCaptured(true)
        assertThat(rig.connection.packets).hasSize(1)
        assertThat(rig.connection.packets.single().controller).isEqualTo(0)
    }

    @Test
    fun `removing a reserved pad forgets its controller for the capture`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)
        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_A)
        rig.disconnect(second)
        rig.connection.packets.clear()

        rig.handler.setCaptured(true)

        assertThat(rig.connection.packets.map { it.controller }).containsExactly(0)
    }

    @Test
    fun `pads that share a controller number send one fused packet`() {
        val rig = rig(multiController = false)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)

        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_B)
        rig.release(first, KEYCODE_BUTTON_A)

        assertThat(rig.connection.packets.map { it.buttons })
            .containsExactly(a, a or b, b)
            .inOrder()
        assertThat(rig.connection.packets.map { it.controller }).containsExactly(0, 0, 0)
    }

    @Test
    fun `pads with a number each send their own packets`() {
        val rig = rig(multiController = true)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)

        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_B)

        assertThat(rig.connection.packets.map { it.controller to it.buttons })
            .containsExactly(0 to a, 1 to b)
            .inOrder()
    }

    @Test
    fun `removing a pad that shares controller 0 zeroes it for the pad that is left`() {
        val rig = rig(multiController = false)
        val first = pad(1)
        val second = pad(2)
        listOf(first, second).forEach(rig::connect)
        rig.press(first, KEYCODE_BUTTON_A)
        rig.press(second, KEYCODE_BUTTON_B)

        rig.disconnect(second)

        // The first pad still holds A, but the zero packet is for controller 0 as a whole
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = 0))
        // Its next event brings its state back
        rig.press(first, KEYCODE_BUTTON_X)
        assertThat(rig.connection.packets.last().buttons).isEqualTo(a or ControllerPacket.X_FLAG)
    }

    @Test
    fun `a touchpad and its joystick are one controller`() {
        val rig = rig(multiController = true)
        val other = pad(1)
        val joystick = pad(5, descriptor = "same")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(other, joystick, touchpad).forEach(rig::connect)
        rig.press(other, KEYCODE_BUTTON_A)

        rig.press(touchpad, KEYCODE_BUTTON_A)
        rig.press(joystick, KEYCODE_BUTTON_B)
        rig.press(other, KEYCODE_BUTTON_Y)

        assertThat(rig.connection.packets.map { it.controller to it.buttons })
            .containsExactly(
                0 to a,
                1 to a,
                1 to (a or b),
                0 to (a or ControllerPacket.Y_FLAG),
            )
            .inOrder()
    }

    @Test
    fun `a pad without joystick axes takes the number of the joystick after it`() {
        val rig = rig(multiController = true)
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        val joystick = pad(5, name = "pad", descriptor = "same")
        listOf(touchpad, joystick).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.context(touchpad).short("controllerNumber")).isEqualTo(0)
        // The joystick got a context of its own, and a number, before the touchpad took it
        assertThat(rig.context(joystick).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.context(joystick).bool("reservedControllerNumber")).isTrue()
        assertThat(rig.context(touchpad).bool("reservedControllerNumber")).isFalse()
        assertThat(rig.arrivalControllers()).containsExactly(0, 0).inOrder()
        assertThat(rig.connection.arrivals).hasSize(2)
    }

    @Test
    fun `the number of the joystick it propagates is the one the joystick already has`() {
        val rig = rig(multiController = true)
        val other = pad(1)
        val joystick = pad(3, descriptor = "same")
        val touchpad = pad(2, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(other, joystick, touchpad).forEach(rig::connect)
        rig.press(other, KEYCODE_BUTTON_A)
        rig.press(joystick, KEYCODE_BUTTON_A)
        val arrivals = rig.connection.arrivals.size

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.context(joystick).short("controllerNumber")).isEqualTo(1)
        assertThat(rig.context(touchpad).short("controllerNumber")).isEqualTo(1)
        // The joystick is not reported again; the touchpad's arrival is the one new report
        assertThat(rig.connection.arrivals).hasSize(arrivals + 1)
        assertThat(rig.connection.arrivals.last().controller).isEqualTo(1)
    }

    @Test
    fun `the associated joystick may come before the device`() {
        val rig = rig(multiController = true)
        val joystick = pad(3, descriptor = "same")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(joystick, touchpad).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.context(joystick).bool("reservedControllerNumber")).isTrue()
        assertThat(rig.context(touchpad).short("controllerNumber")).isEqualTo(0)
    }

    @Test
    fun `the joystick after the device wins over the one before it`() {
        val rig = rig(multiController = true)
        val taken = pad(1)
        val before = pad(3, descriptor = "same")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        val after = pad(5, name = "other pad", descriptor = "same")
        listOf(taken, before, touchpad, after).forEach(rig::connect)
        rig.press(taken, KEYCODE_BUTTON_A)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.contextOf(after)).isNotNull()
        assertThat(rig.contextOf(before)).isNull()
        assertThat(rig.context(touchpad).short("controllerNumber")).isEqualTo(1)
    }

    @Test
    fun `a device is not associated with a neighbor that has no joystick source`() {
        val rig = rig(multiController = true)
        val neighbor = pad(5, sources = GAMEPAD, descriptor = "same", axes = emptyList())
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(touchpad, neighbor).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.contextOf(neighbor)).isNull()
        assertThat(rig.context(touchpad).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.arrivalControllers()).containsExactly(0)
    }

    @Test
    fun `a device is not associated with a neighbor of the same name`() {
        val rig = rig(multiController = true)
        val twin = pad(5, name = "twin", descriptor = "same")
        val touchpad = pad(4, name = "twin", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(touchpad, twin).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.contextOf(twin)).isNull()
    }

    @Test
    fun `a device is not associated with a neighbor of another descriptor`() {
        val rig = rig(multiController = true)
        val stranger = pad(5, name = "stranger", descriptor = "other")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(touchpad, stranger).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.contextOf(stranger)).isNull()
    }

    @Test
    fun `an associated joystick does not reserve a number without multi-controller`() {
        val rig = rig(multiController = false)
        val joystick = pad(5, descriptor = "same")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(touchpad, joystick).forEach(rig::connect)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.context(joystick).bool("assignedControllerNumber")).isTrue()
        assertThat(rig.context(joystick).bool("reservedControllerNumber")).isFalse()
        assertThat(rig.arrivalControllers()).containsExactly(0, 0)
    }

    @Test
    fun `an associated joystick is made without the gamepad mouse the handler has on`() {
        val rig = rig(multiController = true)
        val joystick = pad(5, descriptor = "same")
        val touchpad = pad(4, name = "pad Touchpad", sources = MOUSE, axes = emptyList(), descriptor = "same")
        listOf(touchpad, joystick).forEach(rig::connect)
        rig.handler.setMouseEmulation(true)

        rig.press(touchpad, KEYCODE_BUTTON_A)

        // A context made for an event follows the handler's mouse emulation; this one does not
        assertThat(rig.context(touchpad).bool("mouseEmulationActive")).isTrue()
        assertThat(rig.context(joystick).bool("mouseEmulationActive")).isFalse()
    }

    @Test
    fun `a USB pad takes a number from the same pool as the system pads`() {
        val rig = rig(multiController = true)
        val system = pad(1)
        rig.connect(system)
        rig.press(system, KEYCODE_BUTTON_A)
        val usb = FakeUsbController(9, rig.handler).also { rig.handler.deviceAdded(it) }

        usb.report(a)

        assertThat(rig.arrivalControllers()).containsExactly(0, 1).inOrder()
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 1, activeMask = 0b11, buttons = a))
    }

    @Test
    fun `a USB pad is controller 0 without multi-controller`() {
        val rig = rig(multiController = false)
        val system = pad(1)
        rig.connect(system)
        rig.press(system, KEYCODE_BUTTON_A)
        val usb = FakeUsbController(9, rig.handler).also { rig.handler.deviceAdded(it) }

        usb.report(a)

        assertThat(rig.arrivalControllers()).containsExactly(0, 0).inOrder()
    }

    @Test
    fun `removing a USB pad frees its number`() {
        val rig = rig(multiController = true)
        val first = FakeUsbController(8, rig.handler).also { rig.handler.deviceAdded(it) }
        val second = FakeUsbController(9, rig.handler).also { rig.handler.deviceAdded(it) }
        first.report(a)
        second.report(a)

        rig.handler.deviceRemoved(second)

        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 1, activeMask = 0b1, buttons = 0))
        val third = FakeUsbController(10, rig.handler).also { rig.handler.deviceAdded(it) }
        third.report(a)
        assertThat(rig.arrivalControllers().last()).isEqualTo(1)
    }

    private companion object {
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val MOUSE = InputDevice.SOURCE_MOUSE
    }
}
