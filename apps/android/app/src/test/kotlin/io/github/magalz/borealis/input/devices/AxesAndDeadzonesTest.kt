package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test

/**
 * Pads report their sticks, triggers and hat on different axes, and the handler picks the ones to
 * read from the axes the device has: the first pair of trigger axes that is there, and the right
 * stick on the axes left over. It also chooses the deadzones of the sticks, from the setting, and
 * of the triggers, from what the device reports.
 */
class AxesAndDeadzonesTest : DevicesTestBase() {
    private val x = MotionEvent.AXIS_X
    private val y = MotionEvent.AXIS_Y
    private val z = MotionEvent.AXIS_Z
    private val rz = MotionEvent.AXIS_RZ
    private val rx = MotionEvent.AXIS_RX
    private val ry = MotionEvent.AXIS_RY
    private val lt = MotionEvent.AXIS_LTRIGGER
    private val rt = MotionEvent.AXIS_RTRIGGER
    private val brake = MotionEvent.AXIS_BRAKE
    private val gas = MotionEvent.AXIS_GAS
    private val throttle = MotionEvent.AXIS_THROTTLE
    private val hatX = MotionEvent.AXIS_HAT_X
    private val hatY = MotionEvent.AXIS_HAT_Y

    private fun axes(vararg axes: Int): List<Axis> = axes.map { Axis(it) }

    private fun contextOf(
        device: Pad,
        configure: PreferenceConfiguration.() -> Unit = {},
    ): ContextView = rig(devices = listOf(device), configure = configure).touch(device)

    private fun axesOf(vararg axes: Int, vendorId: Int = 0, keys: IntArray = intArrayOf()): Axes =
        contextOf(pad(1, vendorId = vendorId, axes = axes(*axes), keys = keys)).axes()

    @Test
    fun `a pad with a stick and Z and RZ uses Z and RZ for the right stick`() {
        assertThat(axesOf(x, y, z, rz)).isEqualTo(Axes(rightStickX = z, rightStickY = rz))
    }

    @Test
    fun `a pad with only the left stick has no right stick and no triggers`() {
        assertThat(axesOf(x, y)).isEqualTo(Axes())
    }

    @Test
    fun `a pad without a left stick keeps the left stick axes and has no joystick axes`() {
        val context = contextOf(pad(1, axes = axes(z, rz)))

        assertThat(context.axes()).isEqualTo(Axes(rightStickX = z, rightStickY = rz))
        assertThat(context.bool("hasJoystickAxes")).isFalse()
    }

    @Test
    fun `a pad with only one of X and Y has no joystick axes`() {
        assertThat(contextOf(pad(1, axes = axes(x))).bool("hasJoystickAxes")).isFalse()
        assertThat(contextOf(pad(2, axes = axes(y))).bool("hasJoystickAxes")).isFalse()
        assertThat(contextOf(pad(3, axes = axes(x, y))).bool("hasJoystickAxes")).isTrue()
    }

    @Test
    fun `the left stick may come from the gamepad source`() {
        val context =
            contextOf(pad(1, axes = listOf(x, y).map { Axis(it, source = InputDevice.SOURCE_GAMEPAD) }))

        assertThat(context.bool("hasJoystickAxes")).isTrue()
    }

    @Test
    fun `triggers on LTRIGGER and RTRIGGER are read first`() {
        assertThat(axesOf(x, y, z, rz, lt, rt))
            .isEqualTo(Axes(rightStickX = z, rightStickY = rz, leftTrigger = lt, rightTrigger = rt))
    }

    @Test
    fun `triggers on BRAKE and GAS come next`() {
        assertThat(axesOf(x, y, z, rz, brake, gas))
            .isEqualTo(Axes(rightStickX = z, rightStickY = rz, leftTrigger = brake, rightTrigger = gas))
    }

    @Test
    fun `triggers on BRAKE and THROTTLE come after those`() {
        assertThat(axesOf(x, y, z, rz, brake, throttle))
            .isEqualTo(
                Axes(rightStickX = z, rightStickY = rz, leftTrigger = brake, rightTrigger = throttle)
            )
    }

    @Test
    fun `LTRIGGER and RTRIGGER win over BRAKE and GAS`() {
        assertThat(axesOf(x, y, lt, rt, brake, gas).leftTrigger).isEqualTo(lt)
    }

    @Test
    fun `BRAKE and GAS win over BRAKE and THROTTLE`() {
        assertThat(axesOf(x, y, brake, gas, throttle).rightTrigger).isEqualTo(gas)
    }

    @Test
    fun `a trigger axis without its partner is not a trigger`() {
        assertThat(axesOf(x, y, lt).leftTrigger).isEqualTo(NO_AXIS)
        assertThat(axesOf(x, y, rt).rightTrigger).isEqualTo(NO_AXIS)
        assertThat(axesOf(x, y, gas).rightTrigger).isEqualTo(NO_AXIS)
        assertThat(axesOf(x, y, brake).leftTrigger).isEqualTo(NO_AXIS)
        assertThat(axesOf(x, y, throttle).rightTrigger).isEqualTo(NO_AXIS)
    }

    @Test
    fun `triggers that are not read as axes leave the right stick to Z and RZ`() {
        assertThat(axesOf(x, y, z, rz, throttle))
            .isEqualTo(Axes(rightStickX = z, rightStickY = rz))
    }

    @Test
    fun `a pad with RX and RY uses them for the right stick and Z and RZ for triggers`() {
        assertThat(axesOf(x, y, z, rz, rx, ry))
            .isEqualTo(
                Axes(
                    rightStickX = rx,
                    rightStickY = ry,
                    leftTrigger = z,
                    rightTrigger = rz,
                    triggersIdleNegative = true,
                )
            )
    }

    @Test
    fun `a pad with RX and RY and no Z and RZ has digital triggers`() {
        assertThat(axesOf(x, y, rx, ry))
            .isEqualTo(Axes(rightStickX = rx, rightStickY = ry, triggersIdleNegative = true))
    }

    @Test
    fun `a pad with RX and RY and only one of Z and RZ has digital triggers`() {
        assertThat(axesOf(x, y, rx, ry, z))
            .isEqualTo(Axes(rightStickX = rx, rightStickY = ry, triggersIdleNegative = true))
    }

    @Test
    fun `a pad with RX alone uses it for nothing`() {
        assertThat(axesOf(x, y, rx)).isEqualTo(Axes())
    }

    @Test
    fun `RX and RY are the right stick when there is no Z and RZ and triggers are elsewhere`() {
        assertThat(axesOf(x, y, rx, ry, brake, gas))
            .isEqualTo(Axes(rightStickX = rx, rightStickY = ry, leftTrigger = brake, rightTrigger = gas))
    }

    @Test
    fun `a DualShock 4 with the old driver reads its triggers on RX and RY`() {
        val axes =
            axesOf(
                x,
                y,
                z,
                rz,
                rx,
                ry,
                vendorId = 0x054c,
                keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_C),
            )

        assertThat(axes)
            .isEqualTo(
                Axes(
                    rightStickX = z,
                    rightStickY = rz,
                    leftTrigger = rx,
                    rightTrigger = ry,
                    triggersIdleNegative = true,
                )
            )
    }

    @Test
    fun `a DualShock 4 with the Linux mapping reads its triggers on Z and RZ`() {
        val axes = axesOf(x, y, z, rz, rx, ry, vendorId = 0x054c)

        assertThat(axes)
            .isEqualTo(
                Axes(
                    rightStickX = rx,
                    rightStickY = ry,
                    leftTrigger = z,
                    rightTrigger = rz,
                    triggersIdleNegative = true,
                )
            )
    }

    @Test
    fun `a pad with a hat uses it`() {
        assertThat(axesOf(x, y, z, rz, hatX, hatY))
            .isEqualTo(Axes(rightStickX = z, rightStickY = rz, hatX = hatX, hatY = hatY))
    }

    @Test
    fun `a pad with only one hat axis has no hat`() {
        assertThat(axesOf(x, y, hatX).hatX).isEqualTo(NO_AXIS)
        assertThat(axesOf(x, y, hatY).hatY).isEqualTo(NO_AXIS)
    }

    @Test
    fun `the triggers may come from the gamepad source`() {
        val device =
            pad(
                1,
                axes =
                    axes(x, y) +
                        listOf(lt, rt).map { Axis(it, source = InputDevice.SOURCE_GAMEPAD, flat = 0.2f) },
            )

        val context = contextOf(device)

        assertThat(context.axes().leftTrigger).isEqualTo(lt)
        assertThat(context.float("triggerDeadzone")).isEqualTo(0.2f)
    }

    @Test
    fun `the joystick source is read before the gamepad source`() {
        val device =
            pad(
                1,
                axes =
                    axes(x, y) +
                        listOf(
                            Axis(lt, source = InputDevice.SOURCE_JOYSTICK, flat = 0.2f),
                            Axis(rt, source = InputDevice.SOURCE_JOYSTICK, flat = 0.2f),
                            Axis(lt, source = InputDevice.SOURCE_GAMEPAD, flat = 0.25f),
                            Axis(rt, source = InputDevice.SOURCE_GAMEPAD, flat = 0.25f),
                        ),
            )

        assertThat(contextOf(device).float("triggerDeadzone")).isEqualTo(0.2f)
    }

    private fun triggerDeadzone(flatLeft: Float, flatRight: Float = flatLeft): Float =
        contextOf(pad(1, axes = axes(x, y) + listOf(Axis(lt, flat = flatLeft), Axis(rt, flat = flatRight))))
            .float("triggerDeadzone")

    @Test
    fun `a trigger deadzone the device reports between 13 and 30 percent is kept`() {
        assertThat(triggerDeadzone(0.13f)).isEqualTo(0.13f)
        assertThat(triggerDeadzone(0.2f)).isEqualTo(0.2f)
        assertThat(triggerDeadzone(0.3f)).isEqualTo(0.3f)
    }

    @Test
    fun `a trigger deadzone below 13 percent or above 30 percent is 13 percent`() {
        assertThat(triggerDeadzone(0f)).isEqualTo(0.13f)
        assertThat(triggerDeadzone(0.05f)).isEqualTo(0.13f)
        assertThat(triggerDeadzone(0.31f)).isEqualTo(0.13f)
        assertThat(triggerDeadzone(1f)).isEqualTo(0.13f)
    }

    @Test
    fun `the larger deadzone of the two triggers counts`() {
        assertThat(triggerDeadzone(0.1f, 0.25f)).isEqualTo(0.25f)
        assertThat(triggerDeadzone(0.25f, 0.1f)).isEqualTo(0.25f)
    }

    @Test
    fun `the deadzone of a trigger is read as a magnitude`() {
        assertThat(triggerDeadzone(-0.25f)).isEqualTo(0.25f)
    }

    @Test
    fun `a pad without triggers has no trigger deadzone`() {
        assertThat(contextOf(pad(1, axes = axes(x, y, z, rz))).float("triggerDeadzone")).isEqualTo(0f)
    }

    @Test
    fun `the ADT-1 and the ASUS Gamepad have a trigger deadzone of 30 percent`() {
        val adt = pad(1, vendorId = 0x18d1, productId = 0x2c40)
        val asus = pad(2, name = "ASUS Gamepad", keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_START))
        val asusTriggers =
            pad(
                3,
                name = "ASUS Gamepad",
                axes = axes(x, y) + listOf(Axis(lt, flat = 0.05f), Axis(rt, flat = 0.05f)),
            )

        assertThat(contextOf(adt).float("triggerDeadzone")).isEqualTo(0.3f)
        assertThat(contextOf(asus).float("triggerDeadzone")).isEqualTo(0.3f)
        assertThat(contextOf(asusTriggers).float("triggerDeadzone")).isEqualTo(0.3f)
    }

    private fun deadzone(percent: Int): Double = (if (percent <= 0) 1 else percent) / 100.0

    @Test
    fun `the stick deadzone is the setting as a fraction`() {
        val context = contextOf(pad(1)) { deadzonePercentage = 7 }

        assertThat(context.float("leftStickDeadzoneRadius")).isEqualTo(deadzone(7).toFloat())
        assertThat(context.float("rightStickDeadzoneRadius")).isEqualTo(deadzone(7).toFloat())
    }

    @Test
    fun `a setting of zero or less is a deadzone of 1 percent`() {
        assertThat(contextOf(pad(1)) { deadzonePercentage = 0 }.float("leftStickDeadzoneRadius"))
            .isEqualTo(0.01f)
        assertThat(contextOf(pad(2)) { deadzonePercentage = -5 }.float("leftStickDeadzoneRadius"))
            .isEqualTo(0.01f)
        assertThat(contextOf(pad(3)) { deadzonePercentage = 1 }.float("leftStickDeadzoneRadius"))
            .isEqualTo(0.01f)
    }

    @Test
    fun `the largest setting is a deadzone of the whole stick`() {
        assertThat(contextOf(pad(1)) { deadzonePercentage = 100 }.float("leftStickDeadzoneRadius"))
            .isEqualTo(1f)
    }

    @Test
    fun `the left stick always has the deadzone and the right stick only when it exists`() {
        val none = contextOf(pad(1, axes = axes(x, y))) { deadzonePercentage = 20 }
        val withoutLeft = contextOf(pad(2, axes = axes(z, rz))) { deadzonePercentage = 20 }

        assertThat(none.float("leftStickDeadzoneRadius")).isEqualTo(deadzone(20).toFloat())
        assertThat(none.float("rightStickDeadzoneRadius")).isEqualTo(0f)
        assertThat(withoutLeft.float("leftStickDeadzoneRadius")).isEqualTo(deadzone(20).toFloat())
        assertThat(withoutLeft.float("rightStickDeadzoneRadius")).isEqualTo(deadzone(20).toFloat())
    }

    @Test
    fun `the default context has the standard axes and the stick deadzone`() {
        val context = rig { deadzonePercentage = 25 }.defaultContext()

        assertThat(context.axes())
            .isEqualTo(
                Axes(
                    rightStickX = z,
                    rightStickY = rz,
                    leftTrigger = brake,
                    rightTrigger = gas,
                    hatX = hatX,
                    hatY = hatY,
                )
            )
        assertThat(context.float("leftStickDeadzoneRadius")).isEqualTo(0.25f)
        assertThat(context.float("rightStickDeadzoneRadius")).isEqualTo(0.25f)
        assertThat(context.float("triggerDeadzone")).isEqualTo(0f)
    }

    @Test
    fun `the default context is controller 0 and internal from the start`() {
        val context = rig().defaultContext()

        assertThat(context.short("controllerNumber")).isEqualTo(0)
        assertThat(context.bool("assignedControllerNumber")).isTrue()
        assertThat(context.bool("reservedControllerNumber")).isFalse()
        assertThat(context.bool("external")).isFalse()
        assertThat(context.bool("ignoreBack")).isTrue()
    }

    @Test
    fun `the default deadzone setting of zero is 1 percent for the default context too`() {
        val context = rig { deadzonePercentage = 0 }.defaultContext()

        assertThat(context.float("leftStickDeadzoneRadius")).isEqualTo(0.01f)
    }

    @Test
    fun `touchpad ranges come from the touchpad source`() {
        val touchpad =
            listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_PRESSURE).map {
                Axis(it, source = InputDevice.SOURCE_TOUCHPAD)
            }
        val context =
            contextOf(pad(1, sources = PAD_SOURCES or InputDevice.SOURCE_TOUCHPAD, axes = axes(x, y) + touchpad))

        assertThat(context["touchpadXRange"]).isNotNull()
        assertThat(context["touchpadYRange"]).isNotNull()
        assertThat(context["touchpadPressureRange"]).isNotNull()
    }

    @Test
    fun `a pad without a touchpad has no touchpad ranges`() {
        val context = contextOf(pad(1))

        assertThat(context["touchpadXRange"]).isNull()
        assertThat(context["touchpadYRange"]).isNull()
        assertThat(context["touchpadPressureRange"]).isNull()
    }

    @Test
    fun `each touchpad range is read on its own`() {
        val onlyX = contextOf(pad(1, axes = axes(x, y) + Axis(x, source = InputDevice.SOURCE_TOUCHPAD)))

        assertThat(onlyX["touchpadXRange"]).isNotNull()
        assertThat(onlyX["touchpadYRange"]).isNull()
        assertThat(onlyX["touchpadPressureRange"]).isNull()
    }
}
