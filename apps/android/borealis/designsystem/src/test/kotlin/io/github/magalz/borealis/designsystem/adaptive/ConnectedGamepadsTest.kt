package io.github.magalz.borealis.designsystem.adaptive

import android.content.Context
import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Test
import org.junit.runner.RunWith

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
