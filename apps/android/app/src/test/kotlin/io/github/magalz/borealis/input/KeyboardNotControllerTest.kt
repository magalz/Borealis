package io.github.magalz.borealis.input

import android.view.InputDevice
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A Bluetooth keyboard can report a JOYSTICK source with X and Y axes (the AULA F75Max does). The
 * stream must not count it as a game controller, or it makes a controller context for the keyboard.
 * These tests read the verdict of `isGameControllerDevice`, whose joystick-axes check is the one
 * rule of `:borealis:designsystem` (`ConnectedGamepadsTest` covers the pads and sticks that count);
 * `getAttachedControllerMask` uses the same joystick check.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardNotControllerTest {
    @Test
    fun `an alphabetic keyboard with joystick X and Y is no game controller device`() {
        val keyboard =
            inputDevice(
                1,
                KEYBOARD or JOYSTICK,
                joystickXy = true,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            )

        assertThat(ControllerHandler.isGameControllerDevice(keyboard)).isFalse()
    }

    @Test
    fun `a gamepad source alone is a game controller device`() {
        assertThat(ControllerHandler.isGameControllerDevice(inputDevice(1, GAMEPAD))).isTrue()
    }

    @Test
    fun `a remote that reports a non-alphabetic keyboard still falls back to a controller`() {
        val remote =
            inputDevice(
                1,
                KEYBOARD or DPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
            )

        assertThat(ControllerHandler.isGameControllerDevice(remote)).isTrue()
    }

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val DPAD = InputDevice.SOURCE_DPAD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
    }
}
