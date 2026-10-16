package io.github.magalz.borealis.stream.input

import android.view.InputDevice
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.B
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_DOWN
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_LEFT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_RIGHT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_UP
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import io.github.magalz.borealis.stream.input.GamepadButtons.Y
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsbChromeKeysTest {
    private fun keys(previous: Int, current: Int) =
        UsbChromeKeys.events(previous, current, 5_000).map { it.action to it.keyCode }

    @Test
    fun `pressing a button is a key down`() {
        assertThat(keys(0, A)).containsExactly(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A)
    }

    @Test
    fun `releasing a button is a key up`() {
        assertThat(keys(A, 0)).containsExactly(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_A)
    }

    @Test
    fun `B, Y and Start map to their own keys`() {
        assertThat(keys(0, B or Y or START))
            .containsExactly(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_START,
            )
    }

    @Test
    fun `Select maps to its own key, which the chord that closes a panel needs`() {
        assertThat(keys(0, SELECT))
            .containsExactly(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_SELECT)
        assertThat(keys(SELECT, 0))
            .containsExactly(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_SELECT)
    }

    @Test
    fun `the D-pad maps to the D-pad keys`() {
        assertThat(keys(0, DPAD_UP or DPAD_DOWN or DPAD_LEFT or DPAD_RIGHT))
            .containsExactly(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_RIGHT,
            )
    }

    @Test
    fun `a button that stays down makes no event`() {
        assertThat(keys(A, A)).isEmpty()
    }

    @Test
    fun `one report can release one button and press another`() {
        assertThat(keys(A, B))
            .containsExactly(
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_B,
            )
    }

    @Test
    fun `buttons the menu does not use make no event`() {
        assertThat(keys(0, RIGHT_BUMPER)).isEmpty()
    }

    @Test
    fun `the events are stamped with the time and come from a gamepad`() {
        val event = UsbChromeKeys.events(0, A, 5_000).single()

        assertThat(event.eventTime).isEqualTo(5_000)
        assertThat(event.source and InputDevice.SOURCE_GAMEPAD)
            .isEqualTo(InputDevice.SOURCE_GAMEPAD)
    }
}
