package io.github.magalz.borealis.input

import android.view.InputDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeGamepadSupportTest {
    private val vid = 0x2DC8
    private val pid = 0x310B

    // The sources Android gave the 8BitDo Ultimate 2 for PC dongle: no gamepad.
    private val dongleSources =
        InputDevice.SOURCE_KEYBOARD or
            InputDevice.SOURCE_DPAD or
            InputDevice.SOURCE_STYLUS or
            InputDevice.SOURCE_MOUSE_RELATIVE or
            InputDevice.SOURCE_JOYSTICK

    private fun blocks(vararg devices: InputDeviceFacts) =
        NativeGamepadSupport.hasNativeGamepad(vid, pid, devices.toList())

    @Test
    fun `a keyboard and mouse interface of the same VID and PID does not block the driver`() {
        val keyboard = InputDeviceFacts(vid, pid, dongleSources)
        val mouse = InputDeviceFacts(vid, pid, InputDevice.SOURCE_MOUSE)

        assertThat(blocks(keyboard, mouse)).isFalse()
    }

    @Test
    fun `a joystick with X and Y axes but no gamepad source blocks the driver`() {
        // The controller handler counts it through its joystick axes, so the driver must not too.
        val joystick = InputDeviceFacts(vid, pid, InputDevice.SOURCE_JOYSTICK, hasJoystickXy = true)

        assertThat(blocks(joystick)).isTrue()
    }

    @Test
    fun `the measured 8BitDo XInput dongle device does not block the driver`() {
        // Merged keyboard/mouse device: JOYSTICK source, but its X and Y belong to the mouse and
        // its only joystick axis is GENERIC_1.
        val dongle =
            InputDeviceFacts(
                vid,
                pid,
                InputDevice.SOURCE_KEYBOARD or
                    InputDevice.SOURCE_DPAD or
                    InputDevice.SOURCE_MOUSE or
                    InputDevice.SOURCE_STYLUS or
                    InputDevice.SOURCE_JOYSTICK,
                hasJoystickXy = false,
            )

        assertThat(blocks(dongle)).isFalse()
    }

    @Test
    fun `an alphabetic keyboard with joystick axes does not block the driver`() {
        // A keyboard that reports a JOYSTICK source with X and Y is no pad for the stream, so the
        // driver may claim the pad of this VID/PID.
        val keyboard =
            InputDeviceFacts(
                vid,
                pid,
                InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_JOYSTICK,
                hasJoystickXy = true,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            )

        assertThat(blocks(keyboard)).isFalse()
    }

    @Test
    fun `a pad with a non-alphabetic keyboard type blocks the driver`() {
        val pad =
            InputDeviceFacts(
                vid,
                pid,
                InputDevice.SOURCE_KEYBOARD or
                    InputDevice.SOURCE_GAMEPAD or
                    InputDevice.SOURCE_JOYSTICK,
                hasJoystickXy = true,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
            )

        assertThat(blocks(pad)).isTrue()
    }

    @Test
    fun `a joystick source without X and Y axes does not block the driver`() {
        assertThat(blocks(InputDeviceFacts(vid, pid, InputDevice.SOURCE_JOYSTICK))).isFalse()
    }

    @Test
    fun `X and Y axes without a joystick source do not block the driver`() {
        val mouse = InputDeviceFacts(vid, pid, InputDevice.SOURCE_MOUSE, hasJoystickXy = true)

        assertThat(blocks(mouse)).isFalse()
    }

    @Test
    fun `a gamepad with the same VID and PID blocks the driver`() {
        val pad =
            InputDeviceFacts(vid, pid, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)

        assertThat(blocks(InputDeviceFacts(vid, pid, dongleSources), pad)).isTrue()
    }

    @Test
    fun `a gamepad with another VID or PID does not block the driver`() {
        val other = InputDeviceFacts(0x045E, 0x028E, InputDevice.SOURCE_GAMEPAD)
        val samePidOnly = InputDeviceFacts(0x045E, pid, InputDevice.SOURCE_GAMEPAD)

        assertThat(blocks(other, samePidOnly)).isFalse()
    }

    @Test
    fun `a virtual device with the same VID and PID does not block the driver`() {
        // Not a real device (a virtual device is the system keyboard): it pins that the driver
        // applies the shared rule, which rejects every virtual device.
        val virtual = InputDeviceFacts(vid, pid, InputDevice.SOURCE_GAMEPAD, isVirtual = true)

        assertThat(blocks(virtual)).isFalse()
    }

    @Test
    fun `no input device at all does not block the driver`() {
        assertThat(blocks()).isFalse()
    }
}
