package io.github.magalz.borealis.designsystem.adaptive

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.disconnect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(AndroidJUnit4::class)
class ConnectedGamepadsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun counts(
        sources: Int,
        isVirtual: Boolean = false,
        hasJoystickXy: Boolean = false,
        keyboardType: Int = NO_KEYBOARD,
    ) = isGameController(sources, isVirtual, hasJoystickXy, keyboardType)

    @Test
    fun `the gamepad source alone counts, as the 8BitDo pads report it`() {
        assertThat(counts(KEYBOARD or GAMEPAD or JOYSTICK)).isTrue()
    }

    @Test
    fun `a joystick with X and Y counts`() {
        assertThat(counts(JOYSTICK, hasJoystickXy = true)).isTrue()
    }

    @Test
    fun `a joystick without X and Y does not count`() {
        assertThat(counts(JOYSTICK)).isFalse()
    }

    @Test
    fun `a virtual device does not count, even with a D-pad`() {
        assertThat(counts(KEYBOARD or DPAD, isVirtual = true)).isFalse()
        assertThat(counts(GAMEPAD, isVirtual = true, hasJoystickXy = true)).isFalse()
    }

    @Test
    fun `a keyboard does not count, and neither does a remote`() {
        assertThat(counts(KEYBOARD)).isFalse()
        assertThat(counts(KEYBOARD or DPAD)).isFalse()
        assertThat(counts(KEYBOARD or DPAD, keyboardType = NON_ALPHABETIC)).isFalse()
    }

    @Test
    fun `an alphabetic keyboard with joystick X and Y is no game controller`() {
        // The AULA F75Max Bluetooth keyboard reports a JOYSTICK source with X and Y.
        assertThat(counts(KEYBOARD or JOYSTICK, hasJoystickXy = true, keyboardType = ALPHABETIC))
            .isFalse()
        assertThat(counts(JOYSTICK, hasJoystickXy = true, keyboardType = ALPHABETIC)).isFalse()
    }

    @Test
    fun `a pad that reports a non-alphabetic keyboard counts, as the 8BitDo Ultimate 2 does`() {
        assertThat(
                counts(
                    KEYBOARD or GAMEPAD or JOYSTICK,
                    hasJoystickXy = true,
                    keyboardType = NON_ALPHABETIC,
                )
            )
            .isTrue()
    }

    @Test
    fun `a gamepad source counts whatever the keyboard type, as the Switch Pro pad`() {
        assertThat(counts(GAMEPAD)).isTrue()
        assertThat(counts(GAMEPAD, keyboardType = NON_ALPHABETIC)).isTrue()
        assertThat(counts(GAMEPAD or JOYSTICK, keyboardType = ALPHABETIC)).isTrue()
    }

    @Test
    fun `a joystick that is no alphabetic keyboard counts, such as a flight stick`() {
        assertThat(counts(JOYSTICK, hasJoystickXy = true, keyboardType = NON_ALPHABETIC)).isTrue()
        assertThat(counts(JOYSTICK, hasJoystickXy = true, keyboardType = NO_KEYBOARD)).isTrue()
    }

    @Test
    fun `no device means no gamepad`() {
        assertThat(isGamepadConnected()).isFalse()
    }

    @Test
    fun `a connected pad is found among the devices Android has`() {
        connect(context, inputDevice(id = 3, sources = KEYBOARD or GAMEPAD or JOYSTICK))

        assertThat(isGamepadConnected()).isTrue()
    }

    @Test
    fun `a keyboard and a virtual device alone are no gamepad`() {
        connect(context, inputDevice(id = 3, sources = KEYBOARD))
        connect(context, inputDevice(id = -1, sources = KEYBOARD or DPAD))

        assertThat(isGamepadConnected()).isFalse()
    }

    @Test
    fun `a joystick counts only with its X and Y ranges`() {
        connect(context, inputDevice(id = 3, sources = JOYSTICK))
        assertThat(isGamepadConnected()).isFalse()

        connect(context, inputDevice(id = 4, sources = JOYSTICK, joystickXy = true))
        assertThat(isGamepadConnected()).isTrue()
    }

    @Test
    fun `an alphabetic keyboard with joystick axes is not a connected gamepad`() {
        connect(
            context,
            inputDevice(
                id = 3,
                sources = KEYBOARD or JOYSTICK,
                joystickXy = true,
                keyboardType = ALPHABETIC,
            ),
        )

        assertThat(isGamepadConnected()).isFalse()
    }

    @Test
    fun `a pad is still found next to an alphabetic keyboard with joystick axes`() {
        connect(
            context,
            inputDevice(
                id = 3,
                sources = KEYBOARD or JOYSTICK,
                joystickXy = true,
                keyboardType = ALPHABETIC,
            ),
        )
        connect(
            context,
            inputDevice(
                id = 4,
                sources = KEYBOARD or GAMEPAD or JOYSTICK,
                joystickXy = true,
                keyboardType = NON_ALPHABETIC,
            ),
        )

        assertThat(isGamepadConnected()).isTrue()
    }

    @Test
    fun `the controller joystick axes need the joystick source and X and Y`() {
        val axes = inputDevice(id = 3, sources = JOYSTICK, joystickXy = true)
        val noAxes = inputDevice(id = 4, sources = JOYSTICK)
        val noJoystickSource = inputDevice(id = 5, sources = KEYBOARD, joystickXy = true)

        assertThat(axes.hasControllerJoystickAxes()).isTrue()
        assertThat(noAxes.hasControllerJoystickAxes()).isFalse()
        assertThat(noJoystickSource.hasControllerJoystickAxes()).isFalse()
    }

    @Test
    fun `the controller joystick axes are not those of an alphabetic keyboard`() {
        fun joystick(id: Int, keyboardType: Int) =
            inputDevice(id, sources = JOYSTICK, joystickXy = true, keyboardType = keyboardType)

        assertThat(joystick(3, ALPHABETIC).hasControllerJoystickAxes()).isFalse()
        assertThat(joystick(4, NON_ALPHABETIC).hasControllerJoystickAxes()).isTrue()
        assertThat(joystick(5, NO_KEYBOARD).hasControllerJoystickAxes()).isTrue()
    }

    // The stream's rule, `isStreamControllerDevice`: the verdicts below are those of
    // `ControllerHandler.isGameControllerDevice` before the rule moved here, difference by
    // difference.

    /** The layout's verdict for a device, to show where the stream's rule differs. */
    private fun layoutSays(device: InputDevice) =
        isGameController(
            device.sources,
            device.isVirtual,
            device.hasJoystickXy(),
            device.keyboardType,
        )

    private fun connectPad(id: Int = 3) =
        connect(context, deviceOf(id, sources = KEYBOARD or GAMEPAD or JOYSTICK))

    private fun virtualKeyboard() = deviceOf(id = -1, sources = KEYBOARD, keyboardType = ALPHABETIC)

    /**
     * [inputDevice] needs Robolectric's `InputDeviceBuilder`, which is Android 14 and later. The
     * Android 10 to 12 tests build the device with the framework's hidden constructor instead,
     * whose arguments the Android level sets.
     */
    private fun deviceOf(
        id: Int,
        sources: Int,
        joystickXy: Boolean = false,
        keyboardType: Int = NO_KEYBOARD,
    ): InputDevice {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return inputDevice(id, sources, joystickXy, keyboardType)
        }
        val int = Int::class.javaPrimitiveType
        val boolean = Boolean::class.javaPrimitiveType
        val arguments =
            mutableListOf(
                ClassParameter.from(int, id),
                ClassParameter.from(int, 0),
                ClassParameter.from(int, 0),
                ClassParameter.from(String::class.java, "test-device-$id"),
                ClassParameter.from(int, 0),
                ClassParameter.from(int, 0),
                ClassParameter.from(String::class.java, "descriptor-$id"),
                ClassParameter.from(boolean, false),
                ClassParameter.from(int, sources),
                ClassParameter.from(int, keyboardType),
                ClassParameter.from(KeyCharacterMap::class.java, null),
                ClassParameter.from(boolean, false),
                ClassParameter.from(boolean, false),
                ClassParameter.from(boolean, false),
            )
        // Android 12 added the sensor and battery flags.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arguments += ClassParameter.from(boolean, false)
            arguments += ClassParameter.from(boolean, false)
        }
        val device =
            ReflectionHelpers.callConstructor(InputDevice::class.java, *arguments.toTypedArray())
        if (joystickXy) {
            for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
                ReflectionHelpers.callInstanceMethod<Unit>(
                    device,
                    "addMotionRange",
                    ClassParameter.from(int, axis),
                    ClassParameter.from(int, JOYSTICK),
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

    @Test
    fun `no device at all is a stream controller device`() {
        assertThat(isStreamControllerDevice(null)).isTrue()
    }

    @Test
    fun `a gamepad source is a stream controller device whatever its axes and keyboard type`() {
        val gamepad = inputDevice(id = 1, sources = GAMEPAD)
        val keyboardPad =
            inputDevice(
                id = 2,
                sources = KEYBOARD or GAMEPAD or JOYSTICK,
                keyboardType = ALPHABETIC,
            )

        for (device in listOf(gamepad, keyboardPad)) {
            assertThat(isStreamControllerDevice(device)).isTrue()
            assertThat(layoutSays(device)).isTrue()
        }
    }

    @Test
    fun `a joystick source with X and Y is a stream controller device`() {
        val stick = inputDevice(id = 1, sources = JOYSTICK, joystickXy = true)

        assertThat(isStreamControllerDevice(stick)).isTrue()
        assertThat(layoutSays(stick)).isTrue()
    }

    @Test
    fun `an alphabetic keyboard is no stream controller device, with or without joystick axes`() {
        val keyboard = inputDevice(id = 1, sources = KEYBOARD, keyboardType = ALPHABETIC)
        val keyboardWithAxes =
            inputDevice(
                id = 2,
                sources = KEYBOARD or JOYSTICK,
                joystickXy = true,
                keyboardType = ALPHABETIC,
            )

        for (device in listOf(keyboard, keyboardWithAxes)) {
            assertThat(isStreamControllerDevice(device)).isFalse()
            assertThat(layoutSays(device)).isFalse()
        }
    }

    @Test
    fun `a dpad source alone does not make an alphabetic keyboard a stream controller device`() {
        // The class bits of the sources overlap: DPAD shares its button class with GAMEPAD.
        val dpad = inputDevice(id = 1, sources = DPAD, keyboardType = ALPHABETIC)

        assertThat(isStreamControllerDevice(dpad)).isFalse()
        assertThat(layoutSays(dpad)).isFalse()
    }

    @Test
    fun `joystick axes without the joystick source do not make an alphabetic keyboard a controller`() {
        val mouse =
            inputDevice(id = 1, sources = MOUSE, joystickXy = true, keyboardType = ALPHABETIC)

        assertThat(isStreamControllerDevice(mouse)).isFalse()
        assertThat(layoutSays(mouse)).isFalse()
    }

    @Test
    fun `a remote with a non-alphabetic keyboard is a stream controller device, but no pad`() {
        val remote = inputDevice(id = 1, sources = KEYBOARD or DPAD, keyboardType = NON_ALPHABETIC)

        assertThat(isStreamControllerDevice(remote)).isTrue()
        assertThat(layoutSays(remote)).isFalse()
    }

    @Test
    fun `a device with no keyboard is a stream controller device, though it is a mouse or a touchscreen`() {
        val mouse = inputDevice(id = 1, sources = MOUSE)
        val touchscreen = inputDevice(id = 2, sources = TOUCHSCREEN)

        for (device in listOf(mouse, touchscreen)) {
            assertThat(isStreamControllerDevice(device)).isTrue()
            assertThat(layoutSays(device)).isFalse()
        }
    }

    @Test
    fun `a virtual device with gamepad buttons or joystick axes is a stream controller device, but no pad`() {
        val virtualGamepad = inputDevice(id = -1, sources = GAMEPAD)
        val virtualStick =
            inputDevice(
                id = -2,
                sources = JOYSTICK,
                joystickXy = true,
                keyboardType = NON_ALPHABETIC,
            )

        for (device in listOf(virtualGamepad, virtualStick)) {
            assertThat(isStreamControllerDevice(device)).isTrue()
            assertThat(layoutSays(device)).isFalse()
        }
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 the virtual device is a stream controller device while a pad is connected`() {
        connectPad()
        val virtual = virtualKeyboard()

        assertThat(isStreamControllerDevice(virtual)).isTrue()
        assertThat(layoutSays(virtual)).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 a connected pad with gamepad buttons alone or with axes alone is enough`() {
        connect(context, deviceOf(id = 3, sources = GAMEPAD))
        assertThat(isStreamControllerDevice(virtualKeyboard())).isTrue()

        disconnect(context, 3)
        assertThat(isStreamControllerDevice(virtualKeyboard())).isFalse()

        connect(context, deviceOf(id = 4, sources = JOYSTICK, joystickXy = true))
        assertThat(isStreamControllerDevice(virtualKeyboard())).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 the virtual device is no stream controller device without a connected pad`() {
        connect(context, deviceOf(id = 3, sources = KEYBOARD, keyboardType = ALPHABETIC))
        connect(
            context,
            deviceOf(
                id = 4,
                sources = KEYBOARD or JOYSTICK,
                joystickXy = true,
                keyboardType = ALPHABETIC,
            ),
        )

        assertThat(isStreamControllerDevice(virtualKeyboard())).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 only the device with id minus one gets the workaround`() {
        connectPad()
        val otherVirtual = deviceOf(id = -2, sources = KEYBOARD, keyboardType = ALPHABETIC)
        val real = deviceOf(id = 1, sources = KEYBOARD, keyboardType = ALPHABETIC)

        assertThat(isStreamControllerDevice(otherVirtual)).isFalse()
        assertThat(isStreamControllerDevice(real)).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_10])
    fun `before Android 11 the virtual device is no stream controller device with a pad connected`() {
        connectPad()

        assertThat(isStreamControllerDevice(virtualKeyboard())).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `after Android 11 the virtual device is no stream controller device with a pad connected`() {
        connectPad()

        assertThat(isStreamControllerDevice(virtualKeyboard())).isFalse()
    }

    private companion object {
        const val ANDROID_10 = 29
        const val ANDROID_11 = 30
        const val ANDROID_12 = 31
        const val MOUSE = InputDevice.SOURCE_MOUSE
        const val TOUCHSCREEN = InputDevice.SOURCE_TOUCHSCREEN
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val DPAD = InputDevice.SOURCE_DPAD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
        const val NO_KEYBOARD = InputDevice.KEYBOARD_TYPE_NONE
        const val NON_ALPHABETIC = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
        const val ALPHABETIC = InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }
}
