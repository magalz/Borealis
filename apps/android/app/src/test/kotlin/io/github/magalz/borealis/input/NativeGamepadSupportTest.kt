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
    fun `a joystick-only device does not block the driver`() {
        assertThat(blocks(InputDeviceFacts(vid, pid, InputDevice.SOURCE_JOYSTICK))).isFalse()
    }

    @Test
    fun `a gamepad with the same VID and PID blocks the driver`() {
        val pad = InputDeviceFacts(vid, pid, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)

        assertThat(blocks(InputDeviceFacts(vid, pid, dongleSources), pad)).isTrue()
    }

    @Test
    fun `a gamepad with another VID or PID does not block the driver`() {
        val other = InputDeviceFacts(0x045E, 0x028E, InputDevice.SOURCE_GAMEPAD)
        val samePidOnly = InputDeviceFacts(0x045E, pid, InputDevice.SOURCE_GAMEPAD)

        assertThat(blocks(other, samePidOnly)).isFalse()
    }

    @Test
    fun `no input device at all does not block the driver`() {
        assertThat(blocks()).isFalse()
    }
}
