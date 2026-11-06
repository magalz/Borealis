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
 * Two pads on one controller number (multiple controllers are off), both in mouse mode, send the
 * click of their fused state: each one's last state holds what the other holds. A pad that leaves
 * lets go of what it holds itself, and only what no other pad on its number holds.
 */
@RunWith(RobolectricTestRunner::class)
class SharedNumberLeavesTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val first = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val second = FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    init {
        rig.handler.setMouseEmulation(true)
    }

    @Test
    fun `a pad that holds nothing leaves while the other pad holds A and sends no mouse event`() {
        second.report(a)
        // The first pad sends its state with what the second one holds in it
        first.report(0, leftStickX = 0.5f)
        val before = rig.connection.buttonEvents()
        assertThat(before).contains("down 1")

        rig.handler.deviceRemoved(first)

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)
    }

    @Test
    fun `the click stays down while one of two pads that hold A leaves, and goes up with the other`() {
        first.report(a)
        second.report(a)
        val before = rig.connection.buttonEvents()

        rig.handler.deviceRemoved(first)

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)

        second.report(0)

        assertThat(rig.connection.buttonEvents().filter { it == "up 1" }).hasSize(1)
        assertThat(rig.connection.buttonEvents().last()).isEqualTo("up 1")
    }

    @Test
    fun `a pad that leaves lets go of its own button and not of the one the other pad holds`() {
        first.report(a)
        second.report(b)

        rig.handler.deviceRemoved(first)

        val events = rig.connection.buttonEvents()
        assertThat(events).contains("up 1")
        assertThat(events).doesNotContain("up 3")
    }

    @Test
    fun `a pad that holds nothing leaves while the on-screen controller holds A and sends nothing`() {
        rig.handler.reportOscState(a, 0, 0, 0, 0, 0, 0)
        // The pad sends its state with what the on-screen controller holds in it
        first.report(0, leftStickX = 0.5f)
        val before = rig.connection.buttonEvents()
        assertThat(before).contains("down 1")

        rig.handler.deviceRemoved(first)

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)
    }

    @Test
    fun `the pad that stays can click again after the other pad let its click go`() {
        first.report(a)
        // The second pad sends its state with what the first one holds in it
        second.report(0, leftStickX = 0.5f)
        rig.handler.deviceRemoved(first)
        val before = rig.connection.buttonEvents()

        second.report(a)

        assertThat(rig.connection.buttonEvents().drop(before.size)).containsExactly("down 1")
    }
}
