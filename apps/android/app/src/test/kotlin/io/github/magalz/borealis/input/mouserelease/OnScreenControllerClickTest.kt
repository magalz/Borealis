package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_X
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pad in mouse mode shares controller number 0 with the on-screen controller, which the pad's
 * mouse path fuses into its state: while the on-screen controller holds A, the pad clicks the left
 * button for it. The on-screen controller is never a mouse, so it does not keep the click down: the
 * PC's button goes up once, when the pad's mouse mode ends (the menu row, the Start hold, or the
 * pad leaving), and the later release of the on-screen controller is a gamepad packet with no mouse
 * event.
 */
@RunWith(RobolectricTestRunner::class)
class OnScreenControllerClickTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    private fun oscHolds(buttons: Int) = rig.handler.reportOscState(buttons, 0, 0, 0, 0, 0, 0)

    /**
     * The PC got one down and one up of [button], in that order, and the release of the on-screen
     * controller adds no mouse event.
     */
    private fun assertClickEndedOnce(button: String = "1") {
        val expected = arrayOf("down $button", "up $button")
        assertThat(rig.connection.buttonEvents()).containsExactly(*expected).inOrder()

        oscHolds(0)

        assertThat(rig.connection.buttonEvents()).containsExactly(*expected).inOrder()
    }

    @Test
    fun `a USB pad that holds nothing ends the mouse with the menu row while the on-screen controller holds A`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        // The pad sends its state with what the on-screen controller holds in it
        pad.report(0, leftStickX = 0.5f)

        rig.handler.setMouseEmulation(false)

        assertClickEndedOnce()
    }

    @Test
    fun `a USB pad that holds nothing ends the mouse with the Start hold while the on-screen controller holds A`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        pad.report(0, leftStickX = 0.5f)

        rig.holdStartOnUsbPadAndLetGo(pad)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertClickEndedOnce()
    }

    @Test
    fun `a USB pad that holds nothing leaves while the on-screen controller holds A`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        pad.report(0, leftStickX = 0.5f)

        rig.handler.deviceRemoved(pad)

        assertClickEndedOnce()
    }

    @Test
    fun `a USB pad that holds A ends the mouse with the menu row while the on-screen controller holds A`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        pad.report(a)

        rig.handler.setMouseEmulation(false)

        assertClickEndedOnce()
    }

    @Test
    fun `a USB pad that holds A leaves while the on-screen controller holds A`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        pad.report(a)

        rig.handler.deviceRemoved(pad)

        assertClickEndedOnce()
    }

    @Test
    fun `a USB pad that holds nothing ends the mouse while the on-screen controller holds B`() {
        oscHolds(b)
        val pad = rig.usbPadInMouseMode()
        pad.report(0, leftStickX = 0.5f)

        rig.handler.setMouseEmulation(false)

        assertClickEndedOnce(button = "3")
    }

    @Test
    fun `a system pad that holds nothing ends the mouse with the menu row while the on-screen controller holds A`() {
        oscHolds(a)
        rig.systemPadInMouseMode()
        // The pad sends its state with what the on-screen controller holds in it
        rig.press(KEYCODE_BUTTON_X)

        rig.handler.setMouseEmulation(false)

        assertClickEndedOnce()
    }

    @Test
    fun `a system pad that holds nothing ends the mouse with the Start hold while the on-screen controller holds A`() {
        oscHolds(a)
        rig.systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_X)

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertClickEndedOnce()
    }

    @Test
    fun `a system pad that holds nothing leaves while the on-screen controller holds A`() {
        oscHolds(a)
        rig.systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_X)

        rig.handler.onInputDeviceRemoved(rig.padDeviceId)

        assertClickEndedOnce()
    }

    @Test
    fun `a system pad that holds A leaves while the on-screen controller holds A`() {
        oscHolds(a)
        rig.systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_A)

        rig.handler.onInputDeviceRemoved(rig.padDeviceId)

        assertClickEndedOnce()
    }

    @Test
    fun `a click the pad sent for the on-screen controller goes up with the mouse mode after its A went up`() {
        oscHolds(a)
        val pad = rig.usbPadInMouseMode()
        pad.report(0, leftStickX = 0.5f)
        // A gamepad packet only: the pad still holds the click it sent
        oscHolds(0)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }
}
