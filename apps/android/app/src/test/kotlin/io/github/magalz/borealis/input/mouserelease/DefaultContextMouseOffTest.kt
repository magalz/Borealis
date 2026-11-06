package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pad that Android reports with device id 0 or with no device (a GPD XD, say) has no context of
 * its own: it uses the handler's default context, the one the on-screen controller shares. Its
 * Start hold turns its own gamepad mouse on and off like any pad's, and the click it holds goes up
 * once when the mode ends: its own held buttons are not "held by another pad". Another pad in mouse
 * mode on its number that holds the button keeps the click down.
 *
 * The events are the ones `Game` hands to the handler; the rig sends them with device id 0.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultContextMouseOffTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val a = ControllerPacket.A_FLAG

    @Test
    fun `a default-context pad that ends its mouse with Start while it holds A lets the left button go once`() {
        rig.holdStartOnSystemPadAndLetGo()
        rig.press(KEYCODE_BUTTON_A)
        assertThat(rig.connection.buttonEvents()).containsExactly("down 1")

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()

        // The pad is a gamepad again: its release is no mouse event
        rig.release(KEYCODE_BUTTON_A)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a default-context pad that ends its mouse while a mouse-mode pad on its number holds A sends no up`() {
        rig.holdStartOnSystemPadAndLetGo()
        val other = rig.usbPadInMouseMode()
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()
        rig.press(KEYCODE_BUTTON_A)
        other.report(a)
        val before = rig.connection.buttonEvents()
        assertThat(before).contains("down 1")

        rig.holdStartOnSystemPadAndLetGo()

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)
    }
}
