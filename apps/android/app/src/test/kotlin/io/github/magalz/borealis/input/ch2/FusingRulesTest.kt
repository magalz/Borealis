package io.github.magalz.borealis.input.ch2

import android.util.SparseArray
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.B_FLAG
import io.github.magalz.borealis.input.FakeUsbController
import io.github.magalz.borealis.input.events.EventsRig
import io.github.magalz.borealis.input.events.Pad
import io.github.magalz.borealis.input.events.Sent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rules by which `transmitControllerInputLocked` picks the contexts it fuses into the packet of
 * a number. A system pad and a USB pad are held to three things: an assigned number, the number of
 * the sender and the mouse mode of the sender. The on-screen controller is held to the number
 * alone. Contexts are fused in the order system pads, USB pads, on-screen controller, and on a tie
 * the later one wins.
 */
@RunWith(RobolectricTestRunner::class)
class FusingRulesTest {
    @get:Rule val rig = EventsRig()

    private fun addUsbPad(id: Int = 1) =
        FakeUsbController(id, rig.handler).also { rig.handler.deviceAdded(it) }

    /** The context the handler holds for the USB pad with this controller id. */
    private fun usbContext(id: Int): Pad {
        val contexts = rig.handlerField("usbDeviceContexts") as SparseArray<*>
        return Pad(rig, id, null, checkNotNull(contexts.get(id)))
    }

    @Test
    fun `the on-screen controller is fused whether or not its number is assigned`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.reportOscState(A_FLAG, 100, 0, 0, 0, 0, 0)
        rig.default.set("assignedControllerNumber", false)

        pad.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG or B_FLAG, leftStickX = 100))
    }

    @Test
    fun `the on-screen controller is fused whatever its mouse mode, with a pad that is not in it`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.reportOscState(A_FLAG, 0, 0, 0, 0, 0, 0)
        rig.default.set("mouseEmulationActive", true)

        pad.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG or B_FLAG))
    }

    @Test
    fun `a USB pad with no controller number yet is left out of the packet of a system pad`() {
        val system = rig.addPad(7, controllerNumber = 0)
        val usb = addUsbPad()
        usb.report(B_FLAG, leftStickX = 1f)
        usbContext(1).set("assignedControllerNumber", false)

        system.press(KEYCODE_BUTTON_A)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG))
    }

    @Test
    fun `a pad in mouse mode does not click for the buttons of a pad that is not`() {
        val gamepad = rig.addPad(7, controllerNumber = 0)
        val mouse =
            rig.addPad(8, controllerNumber = 0, fields = mapOf("mouseEmulationActive" to true))
        gamepad.press(KEYCODE_BUTTON_A)

        mouse.motion(mapOf())

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a stick tie between a system pad and a USB pad goes to the USB pad`() {
        rig.addPad(7, controllerNumber = 0, fields = mapOf("leftStickX" to 16383.toShort()))
        val usb = addUsbPad()

        // -0.5 of the travel is -16383: the same strength as the pad's stick, the other way
        usb.report(0, leftStickX = -0.5f)

        assertThat(rig.packets.last().leftStickX).isEqualTo(-16383)
    }

    @Test
    fun `a stick tie between a USB pad and the on-screen controller goes to the on-screen one`() {
        val usb = addUsbPad()
        usb.report(0, leftStickX = -0.5f)
        assertThat(rig.packets.last().leftStickX).isEqualTo(-16383)

        rig.handler.reportOscState(0, 16383, 0, 0, 0, 0, 0)

        assertThat(rig.packets.last().leftStickX).isEqualTo(16383)
    }
}
