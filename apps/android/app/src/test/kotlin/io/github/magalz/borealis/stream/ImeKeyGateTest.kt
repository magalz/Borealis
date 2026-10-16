package io.github.magalz.borealis.stream

import android.app.Application
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImeKeyGateTest {
    private var imeVisible = true
    private val gate = ImeKeyGate { imeVisible }

    private fun key(code: Int, source: Int, action: Int = KeyEvent.ACTION_DOWN) =
        KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, source)

    @Test
    fun `the D-pad of a gamepad goes to the keyboard while it is visible`() {
        for (code in
            listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
            )) {
            assertThat(gate.toIme(key(code, InputDevice.SOURCE_GAMEPAD))).isTrue()
            assertThat(gate.toIme(key(code, InputDevice.SOURCE_DPAD))).isTrue()
        }
    }

    @Test
    fun `Enter and Back of a remote or a gamepad go to the keyboard while it is visible`() {
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_ENTER, InputDevice.SOURCE_DPAD))).isTrue()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_NUMPAD_ENTER, InputDevice.SOURCE_GAMEPAD)))
            .isTrue()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_GAMEPAD))).isTrue()
    }

    @Test
    fun `the key up of such a key goes the same way as the key down`() {
        val up = key(KeyEvent.KEYCODE_DPAD_UP, InputDevice.SOURCE_GAMEPAD, KeyEvent.ACTION_UP)

        assertThat(gate.toIme(up)).isTrue()
    }

    @Test
    fun `nothing is held back while the keyboard is hidden`() {
        imeVisible = false

        assertThat(gate.toIme(key(KeyEvent.KEYCODE_DPAD_UP, InputDevice.SOURCE_GAMEPAD))).isFalse()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_ENTER, InputDevice.SOURCE_DPAD))).isFalse()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_GAMEPAD))).isFalse()
    }

    @Test
    fun `a hardware keyboard is not affected, its arrows and Enter go to the PC`() {
        for (code in
            listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_A,
            )) {
            assertThat(gate.toIme(key(code, InputDevice.SOURCE_KEYBOARD))).isFalse()
        }
    }

    @Test
    fun `other keys of a gamepad are not for the keyboard`() {
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD))).isFalse()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BUTTON_START, InputDevice.SOURCE_GAMEPAD)))
            .isFalse()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_A, InputDevice.SOURCE_GAMEPAD))).isFalse()
    }

    @Test
    fun `the synthetic Back of a mouse right click is not for the keyboard`() {
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_MOUSE))).isFalse()
        assertThat(gate.toIme(key(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_MOUSE_RELATIVE)))
            .isFalse()
    }

    @Test
    fun `a view that is not in a window shows no keyboard`() {
        val view = View(ApplicationProvider.getApplicationContext<Application>())

        assertThat(ImeKeyGate.isImeVisible(view)).isFalse()
    }
}
