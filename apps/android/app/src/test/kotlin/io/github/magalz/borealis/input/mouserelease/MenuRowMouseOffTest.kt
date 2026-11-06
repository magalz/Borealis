package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The menu's "Gamepad mouse" row turns the mouse mode off for every pad at once. A pad that holds A
 * or B in that mode holds the PC's left or right button: turning the mode off lets it go, once.
 */
@RunWith(RobolectricTestRunner::class)
class MenuRowMouseOffTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    private fun usbPadInMouseMode() =
        FakeUsbController(1, rig.handler).also {
            rig.handler.deviceAdded(it)
            rig.handler.setMouseEmulation(true)
        }

    private fun systemPadInMouseMode() {
        rig.useSystemPad()
        rig.handler.setMouseEmulation(true)
    }

    @Test
    fun `turning the mouse off while a USB pad holds A lets the left button go`() {
        val pad = usbPadInMouseMode()
        pad.report(a)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `turning the mouse off while a USB pad holds B lets the right button go`() {
        val pad = usbPadInMouseMode()
        pad.report(b)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `turning the mouse off while a USB pad holds both lets each button go once`() {
        val pad = usbPadInMouseMode()
        pad.report(a or b)

        rig.handler.setMouseEmulation(false)

        val events = rig.connection.buttonEvents()
        assertThat(events.filter { it == "up 1" }).hasSize(1)
        assertThat(events.filter { it == "up 3" }).hasSize(1)
    }

    @Test
    fun `the pad that lets go of its button after the mouse went off sends no second up`() {
        val pad = usbPadInMouseMode()
        pad.report(a)
        rig.handler.setMouseEmulation(false)

        pad.report(0)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `turning the mouse off while a system pad holds A lets the left button go`() {
        systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_A)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `turning the mouse off while a system pad holds B lets the right button go`() {
        systemPadInMouseMode()
        rig.press(KEYCODE_BUTTON_B)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `turning the mouse off with no button held sends no mouse event`() {
        usbPadInMouseMode()
        systemPadInMouseMode()

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a pad that let go of its button before the mouse went off sends nothing more`() {
        val pad = usbPadInMouseMode()
        pad.report(a)
        pad.report(0)
        val before = rig.connection.mouse.toList()

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.mouse).isEqualTo(before)
    }

    @Test
    fun `a button the capture already released is not released again`() {
        val pad = usbPadInMouseMode()
        pad.report(a)
        rig.handler.setCaptured(true)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }
}
