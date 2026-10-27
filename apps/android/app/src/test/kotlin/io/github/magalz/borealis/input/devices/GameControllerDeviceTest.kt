package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
import android.view.InputDevice.KEYBOARD_TYPE_NONE
import android.view.InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import io.github.magalz.borealis.designsystem.adaptive.isGameController
import io.github.magalz.borealis.testing.ANDROID_10
import io.github.magalz.borealis.testing.ANDROID_11
import io.github.magalz.borealis.testing.ANDROID_12
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * `ControllerHandler.isGameControllerDevice` is the handler's verdict on whether an input device is
 * a game controller, and it differs from the rule of `:borealis:designsystem` (`isGameController`)
 * in ways that the stream relies on: a device the rule rejects can still be a controller here. The
 * tests pin every difference, because the handler's rule moves next to the layout's.
 */
class GameControllerDeviceTest : DevicesTestBase() {
    private fun isController(device: InputDevice?) =
        ControllerHandler.isGameControllerDevice(device)

    /** The layout's verdict for a device, to show where the handler differs. */
    private fun layoutSays(device: InputDevice) =
        isGameController(
            device.sources,
            device.isVirtual,
            device.getMotionRange(android.view.MotionEvent.AXIS_X, JOYSTICK) != null,
            device.keyboardType,
        )

    @Test
    fun `no device at all is a controller`() {
        assertThat(isController(null)).isTrue()
    }

    @Test
    fun `a gamepad source is a controller without any axis`() {
        val gamepad =
            pad(1) {
                sources = GAMEPAD
                keyboardType = KEYBOARD_TYPE_NONE
                axes = emptyList()
            }

        assertThat(isController(gamepad.device)).isTrue()
    }

    @Test
    fun `a gamepad source is a controller whatever its keyboard type`() {
        val keyboardPad =
            pad(1) {
                sources = KEYBOARD or GAMEPAD or JOYSTICK
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }

        assertThat(isController(keyboardPad.device)).isTrue()
    }

    @Test
    fun `a joystick source with the X and Y axes is a controller`() {
        val stick =
            pad(1) {
                sources = JOYSTICK
                keyboardType = KEYBOARD_TYPE_NONE
                axes = LEFT_STICK
            }

        assertThat(isController(stick.device)).isTrue()
    }

    @Test
    fun `the axes may come from the gamepad source`() {
        val stick =
            pad(1) {
                sources = JOYSTICK
                keyboardType = KEYBOARD_TYPE_NON_ALPHABETIC
                axes = LEFT_STICK.map { it.copy(source = GAMEPAD) }
            }

        assertThat(isController(stick.device)).isTrue()
    }

    @Test
    fun `an alphabetic keyboard with joystick axes is no controller`() {
        val keyboard =
            pad(1) {
                sources = KEYBOARD or JOYSTICK
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = LEFT_STICK
            }

        assertThat(isController(keyboard.device)).isFalse()
    }

    @Test
    fun `an alphabetic keyboard is no controller`() {
        val keyboard =
            pad(1) {
                sources = KEYBOARD
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }

        assertThat(isController(keyboard.device)).isFalse()
    }

    @Test
    fun `a dpad source alone does not make an alphabetic keyboard a controller`() {
        // The class bits of the sources overlap: DPAD shares its button class with GAMEPAD.
        val dpad =
            pad(1) {
                sources = DPAD
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }

        assertThat(isController(dpad.device)).isFalse()
    }

    @Test
    fun `a remote with a non-alphabetic keyboard is a controller`() {
        val remote =
            pad(1) {
                sources = KEYBOARD or DPAD
                keyboardType = KEYBOARD_TYPE_NON_ALPHABETIC
                axes = emptyList()
            }

        assertThat(isController(remote.device)).isTrue()
    }

    @Test
    fun `a device with no keyboard is a controller even when it is a mouse or a touchscreen`() {
        val mouse =
            pad(1) {
                sources = MOUSE
                keyboardType = KEYBOARD_TYPE_NONE
                axes = emptyList()
            }
        val touchscreen =
            pad(2) {
                sources = TOUCHSCREEN
                keyboardType = KEYBOARD_TYPE_NONE
                axes = emptyList()
            }

        assertThat(isController(mouse.device)).isTrue()
        assertThat(isController(touchscreen.device)).isTrue()
    }

    @Test
    fun `a virtual device with gamepad buttons is a controller`() {
        val virtual =
            pad(-1) {
                sources = GAMEPAD
                keyboardType = KEYBOARD_TYPE_NONE
                axes = emptyList()
            }

        assertThat(isController(virtual.device)).isTrue()
    }

    @Test
    fun `a virtual device with joystick axes is a controller`() {
        val virtual =
            pad(-1) {
                sources = JOYSTICK
                keyboardType = KEYBOARD_TYPE_NON_ALPHABETIC
                axes = LEFT_STICK
            }

        assertThat(isController(virtual.device)).isTrue()
        assertThat(layoutSays(virtual.device)).isFalse()
    }

    @Test
    fun `joystick axes without the joystick source are no controller of their own`() {
        // Only a non-alphabetic keyboard type keeps this one in
        val mouse =
            pad(1) {
                sources = MOUSE
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = LEFT_STICK
            }

        assertThat(isController(mouse.device)).isFalse()
    }

    @Test
    fun `the layout rule rejects what the handler accepts`() {
        val virtualGamepad =
            pad(-1) {
                    sources = GAMEPAD
                    axes = emptyList()
                }
                .device
        val mouse =
            pad(1) {
                    sources = MOUSE
                    keyboardType = KEYBOARD_TYPE_NONE
                    axes = emptyList()
                }
                .device
        val remote =
            pad(2) {
                    sources = KEYBOARD or DPAD
                    keyboardType = KEYBOARD_TYPE_NON_ALPHABETIC
                    axes = emptyList()
                }
                .device

        for (device in listOf(virtualGamepad, mouse, remote)) {
            assertThat(layoutSays(device)).isFalse()
            assertThat(isController(device)).isTrue()
        }
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 the virtual device is a controller while a gamepad is connected`() {
        connectPad(pad(3))
        val virtual = virtualKeyboard()

        assertThat(isController(virtual)).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 a connected gamepad button source is enough`() {
        connectPad(
            pad(3) {
                sources = GAMEPAD
                axes = emptyList()
            }
        )

        assertThat(isController(virtualKeyboard())).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 the virtual device is no controller when no gamepad is connected`() {
        connectPad(
            pad(3) {
                sources = KEYBOARD
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }
        )

        assertThat(isController(virtualKeyboard())).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 a connected alphabetic keyboard with axes does not count as a gamepad`() {
        connectPad(
            pad(3) {
                sources = KEYBOARD or JOYSTICK
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
            }
        )

        assertThat(isController(virtualKeyboard())).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 only the device with id minus one gets the workaround`() {
        connectPad(pad(3))
        val other =
            pad(-2) {
                sources = KEYBOARD
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }

        assertThat(isController(other.device)).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_10])
    fun `before Android 11 the virtual device is no controller even with a gamepad connected`() {
        connectPad(pad(3))

        assertThat(isController(virtualKeyboard())).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `after Android 11 the virtual device is no controller even with a gamepad connected`() {
        connectPad(pad(3))

        assertThat(isController(virtualKeyboard())).isFalse()
    }

    private fun virtualKeyboard() =
        pad(-1) {
                sources = KEYBOARD
                keyboardType = KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }
            .device

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val DPAD = InputDevice.SOURCE_DPAD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
        const val MOUSE = InputDevice.SOURCE_MOUSE
        const val TOUCHSCREEN = InputDevice.SOURCE_TOUCHSCREEN
    }
}
