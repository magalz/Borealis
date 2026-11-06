package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import io.github.magalz.borealis.input.ControllerHandlerRig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Holding Start turns the mouse mode off for the pad that holds it, on a pad of either kind. A or B
 * held in that mode hold the PC's left or right button: the Start hold lets them go, once.
 */
@RunWith(RobolectricTestRunner::class)
class StartHoldMouseOffTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    @Test
    fun `a USB pad that ends the mouse with the Start hold while it holds A lets the left button go`() {
        val pad = rig.usbPadInMouseMode()
        pad.report(a)

        rig.holdStartOnUsbPadAndLetGo(pad, a)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a USB pad that ends the mouse with the Start hold while it holds B lets the right button go`() {
        val pad = rig.usbPadInMouseMode()
        pad.report(b)

        rig.holdStartOnUsbPadAndLetGo(pad, b)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `a USB pad that ends the mouse while it holds both lets each button go once`() {
        val pad = rig.usbPadInMouseMode()
        pad.report(a or b)

        rig.holdStartOnUsbPadAndLetGo(pad, a or b)

        val events = rig.connection.buttonEvents()
        assertThat(events.filter { it == "up 1" }).hasSize(1)
        assertThat(events.filter { it == "up 3" }).hasSize(1)
    }

    @Test
    fun `a USB pad that lets go of A with Start sends one up`() {
        val pad = rig.usbPadInMouseMode()
        pad.report(a)
        pad.report(a or PLAY_FLAG)
        rig.advanceBy(800)

        pad.report(0)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `the driver thread turns the mouse off on the main thread and the button goes up there`() {
        val pad = rig.usbPadInMouseMode()
        pad.report(a)
        pad.report(a or PLAY_FLAG)
        rig.advanceBy(800)

        // The USB driver reports from its own thread, which has no looper: the toggle is posted.
        Thread { pad.report(a) }
            .apply {
                start()
                join()
            }
        rig.advanceBy(0)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a USB pad that ends the mouse with no button held sends no mouse event`() {
        val pad = rig.usbPadInMouseMode()

        rig.holdStartOnUsbPadAndLetGo(pad)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a system pad that ends the mouse with the Start hold while it holds A lets the left button go`() {
        rig.systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_A)

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a system pad that ends the mouse with the Start hold while it holds B lets the right button go`() {
        rig.systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_B)

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `a system pad that ends the mouse with no button held sends no mouse event`() {
        rig.systemPadInMouseMode()

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.connection.mouse).isEmpty()
    }
}
