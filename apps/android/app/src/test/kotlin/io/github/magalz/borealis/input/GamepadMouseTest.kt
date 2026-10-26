package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_A
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** The menu's "Gamepad mouse" row turns mouse emulation on or off for every pad at once. */
@RunWith(RobolectricTestRunner::class)
class GamepadMouseTest {
    private val rig = ControllerHandlerRig(multiController = true)
    private val padOne = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val padTwo = FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }
    private val a = ControllerPacket.A_FLAG

    @Test
    fun `turning it on makes every pad click the mouse`() {
        rig.handler.setMouseEmulation(true)

        padOne.report(a)
        padOne.report(0)
        padTwo.report(a)

        assertThat(rig.connection.mouse.filter { it == "down 1" }).hasSize(2)
    }

    @Test
    fun `the on-screen controller never becomes a mouse`() {
        rig.handler.setMouseEmulation(true)

        rig.handler.reportOscState(a, 0, 0, 0, 0, 0, 0)
        rig.press(KEYCODE_BUTTON_A)

        assertThat(rig.connection.mouse.none { it.startsWith("down") }).isTrue()
        assertThat(rig.lastButtons()).isEqualTo(a)
    }

    @Test
    fun `it reads as off before it is turned on`() {
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `it reads as on after it is turned on and off after it is turned off`() {
        rig.handler.setMouseEmulation(true)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()

        rig.handler.setMouseEmulation(false)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `it reads as on when any pad has it on`() {
        rig.handler.setMouseEmulation(true)
        rig.handler.deviceRemoved(padOne)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()
    }

    @Test
    fun `it reads as off with no pad connected, and as on for a pad that connects after it was turned on`() {
        val bare = ControllerHandlerRig()

        bare.handler.setMouseEmulation(true)
        assertThat(bare.handler.isMouseEmulationOnAnyPad).isFalse()

        FakeUsbController(5, bare.handler).also { bare.handler.deviceAdded(it) }
        assertThat(bare.handler.isMouseEmulationOnAnyPad).isTrue()
    }

    @Test
    fun `turning it on shows the notice once`() {
        rig.handler.setMouseEmulation(true)

        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: ON")
    }

    @Test
    fun `turning it on twice changes nothing the second time`() {
        rig.handler.setMouseEmulation(true)
        rig.handler.setMouseEmulation(true)

        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        padOne.report(a)
        assertThat(rig.connection.mouse).contains("down 1")
    }

    @Test
    fun `turning it off sends the pads buttons to the game again`() {
        rig.handler.setMouseEmulation(true)
        rig.handler.setMouseEmulation(false)

        padOne.report(a)

        assertThat(rig.connection.mouse.none { it.startsWith("down") }).isTrue()
        assertThat(rig.lastButtons()).isEqualTo(a)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: OFF")
    }

    @Test
    fun `turning it off when it is off changes nothing`() {
        rig.handler.setMouseEmulation(false)

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `a pad connected after it was turned on emulates the mouse too`() {
        rig.handler.setMouseEmulation(true)
        val lateComer = FakeUsbController(3, rig.handler).also { rig.handler.deviceAdded(it) }

        lateComer.report(a)

        assertThat(rig.connection.mouse).contains("down 1")
    }

    @Test
    fun `a pad that connects while it is on joins without a notice`() {
        val bare = ControllerHandlerRig()
        bare.handler.setMouseEmulation(true)
        val notices = ShadowToast.shownToastCount()

        bare.handler.deviceAdded(FakeUsbController(5, bare.handler))

        assertThat(ShadowToast.shownToastCount()).isEqualTo(notices)
    }
}
