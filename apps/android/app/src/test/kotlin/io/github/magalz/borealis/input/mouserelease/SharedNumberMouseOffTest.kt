package io.github.magalz.borealis.input.mouserelease

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two pads on one controller number (multiple controllers are off), both in mouse mode, send the
 * click of their fused state. When the mouse mode goes off for a pad, the PC's button goes up with
 * the last pad that holds it, as it does when a pad leaves: never for a button another pad holds,
 * and never twice.
 */
@RunWith(RobolectricTestRunner::class)
class SharedNumberMouseOffTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val first = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val second = FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }
    private val a = ControllerPacket.A_FLAG

    init {
        rig.handler.setMouseEmulation(true)
    }

    @Test
    fun `two pads that hold A let the left button go once when the mouse goes off for both`() {
        first.report(a)
        second.report(a)

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents().filter { it == "up 1" }).hasSize(1)
        assertThat(rig.connection.buttonEvents().last()).isEqualTo("up 1")
    }

    @Test
    fun `a pad that holds nothing ends the mouse while the other pad holds A and sends no up`() {
        second.report(a)
        // The first pad sends its state with what the second one holds in it
        first.report(PLAY_FLAG)
        rig.advanceBy(800)
        val before = rig.connection.buttonEvents()
        assertThat(before).contains("down 1")

        first.report(0)

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)
    }

    @Test
    fun `the click goes up with the pad that still holds it after the other pad left the mouse`() {
        second.report(a)
        first.report(PLAY_FLAG)
        rig.advanceBy(800)
        first.report(0)

        second.report(0)

        assertThat(rig.connection.buttonEvents().filter { it == "up 1" }).hasSize(1)
        assertThat(rig.connection.buttonEvents().last()).isEqualTo("up 1")
    }

    @Test
    fun `a pad that holds nothing ends the mouse while the on-screen controller holds A and sends no up`() {
        rig.handler.reportOscState(a, 0, 0, 0, 0, 0, 0)
        // The pad sends its state with what the on-screen controller holds in it
        first.report(0, leftStickX = 0.5f)
        val before = rig.connection.buttonEvents()
        assertThat(before).contains("down 1")

        rig.handler.setMouseEmulation(false)

        assertThat(rig.connection.buttonEvents()).isEqualTo(before)
    }
}
