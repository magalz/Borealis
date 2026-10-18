package io.github.magalz.borealis.input

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * A Bluetooth keyboard can report a JOYSTICK source with X and Y axes (the AULA F75Max does). The
 * stream must not count it as a game controller, or it makes a controller context for the keyboard.
 * A pad with a non-alphabetic keyboard type, or none, still counts. These tests read the verdict of
 * `isGameControllerDevice`: `getAttachedControllerMask` and the context creation need the native
 * library, which unit tests do not have.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardNotControllerTest {
    @Test
    fun `an alphabetic keyboard with joystick X and Y is no game controller device`() {
        val keyboard =
            device(1, KEYBOARD or JOYSTICK, joystickXy = true, keyboardType = ALPHABETIC)

        assertThat(ControllerHandler.isGameControllerDevice(keyboard)).isFalse()
    }

    @Test
    fun `an 8BitDo pad with a non-alphabetic keyboard is a game controller device`() {
        val pad =
            device(
                1,
                KEYBOARD or GAMEPAD or JOYSTICK,
                joystickXy = true,
                keyboardType = NON_ALPHABETIC,
            )

        assertThat(ControllerHandler.isGameControllerDevice(pad)).isTrue()
    }

    @Test
    fun `a gamepad source alone is a game controller device`() {
        assertThat(ControllerHandler.isGameControllerDevice(device(1, GAMEPAD))).isTrue()
    }

    @Test
    fun `a joystick with X and Y that is no alphabetic keyboard is a game controller device`() {
        val flightStick = device(1, JOYSTICK, joystickXy = true, keyboardType = NON_ALPHABETIC)
        val noKeyboard = device(2, JOYSTICK, joystickXy = true, keyboardType = NO_KEYBOARD)

        assertThat(ControllerHandler.isGameControllerDevice(flightStick)).isTrue()
        assertThat(ControllerHandler.isGameControllerDevice(noKeyboard)).isTrue()
    }

    @Test
    fun `a remote that reports a non-alphabetic keyboard still falls back to a controller`() {
        val remote = device(1, KEYBOARD or DPAD, keyboardType = NON_ALPHABETIC)

        assertThat(ControllerHandler.isGameControllerDevice(remote)).isTrue()
    }

    /** An input device as the framework reports it, without the native code a real one reads. */
    @Suppress("DEPRECATION") // the Robolectric helper; InputDevice.Builder is not in the public SDK
    private fun device(
        id: Int,
        sources: Int,
        joystickXy: Boolean = false,
        keyboardType: Int = NO_KEYBOARD,
    ): InputDevice {
        val device = ShadowInputDevice.makeInputDeviceNamed("test-device-$id")
        ReflectionHelpers.setField(device, "mId", id)
        ReflectionHelpers.setField(device, "mSources", sources)
        ReflectionHelpers.setField(device, "mKeyboardType", keyboardType)
        if (joystickXy) {
            for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
                ReflectionHelpers.callInstanceMethod<Unit>(
                    device,
                    "addMotionRange",
                    ClassParameter.from(Int::class.javaPrimitiveType, axis),
                    ClassParameter.from(Int::class.javaPrimitiveType, JOYSTICK),
                    ClassParameter.from(Float::class.javaPrimitiveType, -1f),
                    ClassParameter.from(Float::class.javaPrimitiveType, 1f),
                    ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                    ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                    ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                )
            }
        }
        return device
    }

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val DPAD = InputDevice.SOURCE_DPAD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
        const val NO_KEYBOARD = InputDevice.KEYBOARD_TYPE_NONE
        const val NON_ALPHABETIC = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
        const val ALPHABETIC = InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }
}
