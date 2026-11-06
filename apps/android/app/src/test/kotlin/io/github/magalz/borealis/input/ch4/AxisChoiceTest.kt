package io.github.magalz.borealis.input.ch4

import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.devices.Axes
import io.github.magalz.borealis.input.devices.Axis
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.Quirks
import io.github.magalz.borealis.input.devices.axes
import io.github.magalz.borealis.input.devices.pad
import io.github.magalz.borealis.input.devices.quirks
import org.junit.Test

/**
 * The axes `createInputDeviceContextForDevice` chooses when a pair needs both of its axes: a right
 * stick on Z and RZ, a right stick on RX and RY, and triggers on Z and RZ. `AxesAndDeadzonesTest`
 * has the cases where the axis that is there is the first of its pair; these have the second, and
 * the pad that is left with RX and RY for both its triggers and its right stick.
 */
class AxisChoiceTest : DevicesTestBase() {
    private val x = MotionEvent.AXIS_X
    private val y = MotionEvent.AXIS_Y
    private val z = MotionEvent.AXIS_Z
    private val rz = MotionEvent.AXIS_RZ
    private val rx = MotionEvent.AXIS_RX
    private val ry = MotionEvent.AXIS_RY
    private val sony = 0x054c

    private fun contextOf(vararg axes: Int, vendorId: Int = 0, keys: IntArray = intArrayOf()) =
        pad(1) {
                this.vendorId = vendorId
                this.axes = axes.map { Axis(it) }
                this.keys = keys
            }
            .let { device -> rig(devices = listOf(device)).touch(device) }

    private fun axesOf(vararg axes: Int, vendorId: Int = 0, keys: IntArray = intArrayOf()) =
        contextOf(*axes, vendorId = vendorId, keys = keys).axes()

    @Test
    fun `a pad with only one of Z and RZ has no right stick`() {
        assertThat(axesOf(x, y, z)).isEqualTo(Axes())
        assertThat(axesOf(x, y, rz)).isEqualTo(Axes())
    }

    @Test
    fun `a pad with RY alone uses it for nothing`() {
        assertThat(axesOf(x, y, ry)).isEqualTo(Axes())
    }

    @Test
    fun `a pad with RX and RY and only RZ has digital triggers`() {
        assertThat(axesOf(x, y, rx, ry, rz))
            .isEqualTo(Axes(rightStickX = rx, rightStickY = ry, triggersIdleNegative = true))
    }

    @Test
    fun `an old-mapping DualShock 4 without Z and RZ has its right stick on the axes of its triggers`() {
        val context =
            contextOf(x, y, rx, ry, vendorId = sony, keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_C))

        assertThat(context.axes())
            .isEqualTo(
                Axes(
                    rightStickX = rx,
                    rightStickY = ry,
                    leftTrigger = rx,
                    rightTrigger = ry,
                    triggersIdleNegative = true,
                )
            )
        assertThat(context.quirks())
            .isEqualTo(Quirks(isNonStandardDualShock4 = true, hasSelect = true, hasMode = true))
    }
}
