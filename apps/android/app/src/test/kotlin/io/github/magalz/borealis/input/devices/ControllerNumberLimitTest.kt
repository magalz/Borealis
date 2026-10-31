package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.FakeUsbController
import io.github.magalz.borealis.testing.LimeLogCapture
import org.junit.Rule
import org.junit.Test

/**
 * Only sixteen controller numbers exist, one bit each of the active mask. A pad that asks for one
 * when all of them are taken gets none: the host is told nothing about it and it sends nothing, and
 * the log says so once, until a number frees up and its next event takes it.
 */
class ControllerNumberLimitTest : DevicesTestBase() {
    @get:Rule val log = LimeLogCapture()
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    private fun DevicesRig.arrivalControllers() = connection.arrivals.map { it.controller }

    private fun DevicesRig.arrivalMasks() = connection.arrivals.map { it.activeMask }

    @Test
    fun `the seventeenth pad gets no number because only sixteen exist, and the host hears nothing of it`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { pad(it) }
        pads.forEach(rig::connect)

        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        val numbers = pads.take(16).map { rig.context(it).short("controllerNumber") }
        assertThat(numbers).containsExactlyElementsIn((0..15).toList()).inOrder()
        assertThat(rig.context(pads.last()).bool("reservedControllerNumber")).isFalse()
        assertThat(rig.context(pads.last()).bool("assignedControllerNumber")).isFalse()
        assertThat(rig.connection.arrivals).hasSize(16)
        assertThat(rig.connection.packets).hasSize(16)
        // All sixteen bits set read as a negative short
        assertThat(rig.arrivalMasks().last()).isEqualTo(-1)
        assertThat(rig.arrivalMasks()[14]).isEqualTo(0x7FFF)
    }

    @Test
    fun `the log names a pad that got no number once, however many events it sends`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { pad(it) }
        pads.forEach(rig::connect)
        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        rig.release(pads.last(), KEYCODE_BUTTON_A)
        rig.press(pads.last(), KEYCODE_BUTTON_B)

        assertThat(log.warnings().filter { it.contains("device 17") }).hasSize(1)
        // The pad that waits does not repeat the lines of the others
        assertThat(log.messages.count { it.contains("needs a controller number") }).isEqualTo(17)
    }

    @Test
    fun `a number that frees up goes to the pad that was left out`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { pad(it) }
        pads.forEach(rig::connect)
        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }
        rig.disconnect(pads[2])

        rig.press(pads.last(), KEYCODE_BUTTON_B)

        val context = rig.context(pads.last())
        assertThat(context.bool("assignedControllerNumber")).isTrue()
        assertThat(context.bool("reservedControllerNumber")).isTrue()
        assertThat(context.short("controllerNumber")).isEqualTo(2)
        assertThat(rig.arrivalControllers().last()).isEqualTo(2)
        // What the pad held while it waited goes out with its first packet
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 2, activeMask = -1, buttons = a or b))
    }

    @Test
    fun `the touchpad of a pad that got no number waits for one too`() {
        val rig = rig(multiController = true)
        val pads = (1..16).map { pad(it) }
        val joystick = pad(25) { descriptor = "same" }
        val touchpad =
            pad(24) {
                name = "pad Touchpad"
                sources = MOUSE
                axes = emptyList()
                descriptor = "same"
            }
        (pads + joystick + touchpad).forEach(rig::connect)
        pads.forEach { rig.press(it, KEYCODE_BUTTON_A) }

        rig.press(touchpad, KEYCODE_BUTTON_A)

        assertThat(rig.context(touchpad).bool("assignedControllerNumber")).isFalse()
        assertThat(rig.context(joystick).bool("assignedControllerNumber")).isFalse()
        assertThat(rig.connection.arrivals).hasSize(16)
        assertThat(rig.connection.packets).hasSize(16)
    }

    @Test
    fun `the seventeenth USB pad gets no number because only sixteen exist, and the host hears nothing of it`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { FakeUsbController(it, rig.handler) }
        pads.forEach(rig.handler::deviceAdded)

        pads.forEach { it.report(a) }

        val contexts = pads.map {
            ContextView(checkNotNull(rig.usbContexts().get(it.controllerId)))
        }
        assertThat(contexts.take(16).map { it.short("controllerNumber") })
            .containsExactlyElementsIn((0..15).toList())
            .inOrder()
        assertThat(contexts.last().bool("reservedControllerNumber")).isFalse()
        assertThat(contexts.last().bool("assignedControllerNumber")).isFalse()
        assertThat(rig.connection.arrivals).hasSize(16)
        assertThat(rig.connection.packets).hasSize(16)
        // All sixteen bits set read as a negative short
        assertThat(rig.arrivalMasks()[14]).isEqualTo(0x7FFF)
        assertThat(rig.arrivalMasks()[15]).isEqualTo(-1)
    }

    @Test
    fun `a number that frees up goes to the USB pad that was left out`() {
        val rig = rig(multiController = true)
        val pads = (1..17).map { FakeUsbController(it, rig.handler) }
        pads.forEach(rig.handler::deviceAdded)
        pads.forEach { it.report(a) }
        rig.handler.deviceRemoved(pads[2])

        pads.last().report(a or b)

        val context = ContextView(checkNotNull(rig.usbContexts().get(pads.last().controllerId)))
        assertThat(context.bool("assignedControllerNumber")).isTrue()
        assertThat(context.short("controllerNumber")).isEqualTo(2)
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 2, activeMask = -1, buttons = a or b))
    }

    private companion object {
        const val MOUSE = InputDevice.SOURCE_MOUSE
    }
}
