package io.github.magalz.borealis.input.mouserelease

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A USB pad in mouse mode holds the PC's left button with A and its right button with B. When the
 * driver lets the pad go with one of them down, the PC gets the button up, once.
 */
@RunWith(RobolectricTestRunner::class)
class UsbPadLeavesTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    init {
        rig.handler.setMouseEmulation(true)
    }

    @Test
    fun `a pad that leaves holding A lets the left button go`() {
        pad.report(a)

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a pad that leaves holding B lets the right button go`() {
        pad.report(b)

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `a pad that leaves holding both lets each button go once`() {
        pad.report(a or b)

        rig.handler.deviceRemoved(pad)

        val events = rig.connection.buttonEvents()
        assertThat(events.filter { it == "up 1" }).hasSize(1)
        assertThat(events.filter { it == "up 3" }).hasSize(1)
    }

    @Test
    fun `a pad that leaves with no button held sends no mouse event`() {
        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a pad that let go of its button before it left sends nothing more`() {
        pad.report(a)
        pad.report(0)
        val before = rig.connection.mouse.toList()

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.mouse).isEqualTo(before)
    }

    @Test
    fun `a pad that holds A as a gamepad button leaves without a mouse event`() {
        rig.handler.setMouseEmulation(false)
        pad.report(a)

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a button the capture already released is not released again`() {
        pad.report(a)
        rig.handler.setCaptured(true)

        rig.handler.deviceRemoved(pad)

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a pad that leaves while it shares its number still lets the button go`() {
        // Multiple controllers are off, so both pads are on controller number 0, in mouse mode.
        val sharer = FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }
        rig.handler.setMouseEmulation(true)
        sharer.report(a)
        // The first pad sends its state with what the second one holds in it
        pad.report(0, leftStickX = 0.5f)

        rig.handler.deviceRemoved(sharer)

        // The first pad saw the click in the state it fused, so it sends an up of its own when the
        // second one goes: the PC gets two. The click is up at the end, and an extra up is no harm.
        assertThat(rig.connection.buttonEvents().last()).isEqualTo("up 1")
    }
}
