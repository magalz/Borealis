package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The stream ends with a pad in mouse mode holding A or B: the PC gets the button up, once, whether
 * the handler is stopped or destroyed, and whatever kind of pad holds it.
 */
@RunWith(RobolectricTestRunner::class)
class StopReleasesMouseTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    private fun usbPadHolding(buttons: Int): FakeUsbController {
        val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        rig.handler.setMouseEmulation(true)
        pad.report(buttons)
        return pad
    }

    @Test
    fun `a USB pad that holds A when the handler stops lets the left button go`() {
        usbPadHolding(a)

        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a USB pad that holds B when the handler stops lets the right button go`() {
        usbPadHolding(b)

        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `a system pad that holds A when the handler stops lets the left button go`() {
        rig.useSystemPad()
        rig.handler.setMouseEmulation(true)
        rig.press(KEYCODE_BUTTON_A)

        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a system pad that holds B when the handler stops lets the right button go`() {
        rig.useSystemPad()
        rig.handler.setMouseEmulation(true)
        rig.press(KEYCODE_BUTTON_B)

        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `the default context that a Start hold turned into a mouse lets its button go on stop`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(900)
        rig.release(KEYCODE_BUTTON_START)
        rig.press(KEYCODE_BUTTON_A)
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1")

        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `destroying a handler that is still running lets the button go`() {
        usbPadHolding(a)

        rig.destroy()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `destroying a handler that was stopped does not release the button again`() {
        usbPadHolding(a)
        rig.handler.stop()

        rig.destroy()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `stopping twice releases the button once`() {
        usbPadHolding(a)

        rig.handler.stop()
        rig.handler.stop()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a USB pad the driver lets go after the stop does not release the button again`() {
        val pad = usbPadHolding(a)
        rig.handler.stop()

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `stopping with no button held sends no mouse event`() {
        usbPadHolding(0)
        rig.useSystemPad()
        rig.handler.setMouseEmulation(true)

        rig.handler.stop()

        assertThat(rig.connection.mouse).isEmpty()
    }
}
