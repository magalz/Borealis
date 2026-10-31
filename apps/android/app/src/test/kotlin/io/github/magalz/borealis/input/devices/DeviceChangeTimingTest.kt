package io.github.magalz.borealis.input.devices

import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import org.junit.Test

/**
 * Some buttons are told by when the pad last let go of another one: a Start held for 750 ms turns
 * the gamepad mouse on, and a Start right after LB or RB stands in for Select or Guide. A device
 * change replaces the context of the pad, and the new one carries those times over, so what the pad
 * was doing is not forgotten.
 */
class DeviceChangeTimingTest : DevicesTestBase() {
    private val select = ControllerPacket.BACK_FLAG
    private val guide = ControllerPacket.SPECIAL_BUTTON_FLAG
    private val hat = listOf(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y).map { Axis(it) }

    /** Android changes the device: it now has a hat, as one that has a touchpad does. */
    private fun DevicesRig.changeToHat(before: Pad): Pad {
        val after = pad(before.id) { axes = STICKS_AND_Z + hat }
        disconnectPad(before)
        connectPad(after)
        change(after)
        return after
    }

    @Test
    fun `a change keeps when Start went down, so a short press does not turn the gamepad mouse on`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before)) { mouseEmulation = true }
        rig.press(before, KEYCODE_BUTTON_START)
        rig.advanceBy(100)

        val after = rig.changeToHat(before)
        rig.advanceBy(100)
        rig.release(after, KEYCODE_BUTTON_START)

        assertThat(rig.context(after).bool("mouseEmulationActive")).isFalse()
    }

    @Test
    fun `a Start held across a change for more than 750 ms still turns the gamepad mouse on`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before)) { mouseEmulation = true }
        rig.press(before, KEYCODE_BUTTON_START)
        rig.advanceBy(500)

        val after = rig.changeToHat(before)
        rig.advanceBy(300)
        rig.release(after, KEYCODE_BUTTON_START)

        assertThat(rig.context(after).bool("mouseEmulationActive")).isTrue()
    }

    @Test
    fun `a change keeps when LB went up, so a Start right after it still stands in for Select`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        rig.press(before, KEYCODE_BUTTON_L1)
        rig.release(before, KEYCODE_BUTTON_L1)

        val after = rig.changeToHat(before)
        rig.press(after, KEYCODE_BUTTON_START)

        assertThat(rig.connection.packets.last().buttons).isEqualTo(select)
    }

    @Test
    fun `a change keeps when RB went up, so a Start right after it still stands in for Guide`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        rig.press(before, KEYCODE_BUTTON_R1)
        rig.release(before, KEYCODE_BUTTON_R1)

        val after = rig.changeToHat(before)
        rig.press(after, KEYCODE_BUTTON_START)

        assertThat(rig.connection.packets.last().buttons).isEqualTo(guide)
    }
}
