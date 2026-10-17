package io.github.magalz.borealis.designsystem.adaptive

import android.content.Context
import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectedGamepadsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the gamepad source alone counts, as the 8BitDo pads report it`() {
        val sources = KEYBOARD or GAMEPAD or JOYSTICK

        assertThat(isGameController(sources, isVirtual = false, hasJoystickXy = false)).isTrue()
    }

    @Test
    fun `a joystick with X and Y counts`() {
        assertThat(isGameController(JOYSTICK, isVirtual = false, hasJoystickXy = true)).isTrue()
    }

    @Test
    fun `a joystick without X and Y does not count`() {
        assertThat(isGameController(JOYSTICK, isVirtual = false, hasJoystickXy = false)).isFalse()
    }

    @Test
    fun `a virtual device does not count, even with a D-pad`() {
        assertThat(isGameController(KEYBOARD or DPAD, isVirtual = true, hasJoystickXy = false))
            .isFalse()
        assertThat(isGameController(GAMEPAD, isVirtual = true, hasJoystickXy = true)).isFalse()
    }

    @Test
    fun `a keyboard does not count, and neither does a remote`() {
        assertThat(isGameController(KEYBOARD, isVirtual = false, hasJoystickXy = false)).isFalse()
        assertThat(isGameController(KEYBOARD or DPAD, isVirtual = false, hasJoystickXy = false))
            .isFalse()
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

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val DPAD = InputDevice.SOURCE_DPAD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
    }
}
