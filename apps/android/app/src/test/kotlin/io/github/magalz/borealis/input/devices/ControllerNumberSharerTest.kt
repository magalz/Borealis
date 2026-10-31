package io.github.magalz.borealis.input.devices

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import org.junit.Test

/**
 * A pad that goes away while others share its controller number leaves the number to them: the
 * handler sends what the pad that is left holds, which drops what the pad that went held. The pads
 * that are in mouse mode are not fused with the ones that are not, so the pad whose state goes out
 * must be one in the same mode as the pads that hold buttons.
 */
class ControllerNumberSharerTest : DevicesTestBase() {
    private val a = ControllerPacket.A_FLAG

    /** Turns the gamepad mouse on for the pad of this context alone, as its Start hold would. */
    private fun ContextView.mouseOn() {
        context.javaClass
            .getMethod(
                "setMouseEmulation",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            .apply { isAccessible = true }
            .invoke(context, true, false)
    }

    @Test
    fun `removing a pad sends what a pad that is not in mouse mode holds`() {
        val rig = rig(multiController = false)
        // The pad in mouse mode has the lowest id, which is the one the lookup meets first
        val mouse = pad(1)
        val gamepad = pad(2)
        val leaving = pad(3)
        listOf(mouse, gamepad, leaving).forEach(rig::connect)
        rig.press(mouse, KEYCODE_BUTTON_A)
        rig.press(gamepad, KEYCODE_BUTTON_A)
        rig.press(leaving, KEYCODE_BUTTON_B)
        rig.context(mouse).mouseOn()
        val sent = rig.connection.packets.size

        rig.disconnect(leaving)

        // The A of the gamepad stays down on the PC and the B of the pad that went comes up
        assertThat(rig.connection.packets).hasSize(sent + 1)
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = a))
    }

    @Test
    fun `removing a pad still sends the state of a pad in mouse mode when no other pad is left`() {
        val rig = rig(multiController = false)
        val mouse = pad(1)
        val leaving = pad(2)
        listOf(mouse, leaving).forEach(rig::connect)
        rig.press(mouse, KEYCODE_BUTTON_A)
        rig.press(leaving, KEYCODE_BUTTON_B)
        rig.context(mouse).mouseOn()
        val sent = rig.connection.packets.size

        rig.disconnect(leaving)

        // The controller is not zeroed: the pad in mouse mode is on the number, and sends its own
        assertThat(rig.connection.packets).hasSize(sent + 1)
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = 0))
    }
}
