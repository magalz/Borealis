package io.github.magalz.borealis.stream.input

import android.view.InputDevice
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The chord that closes a chrome panel: Select and Start held together for one second. */
@RunWith(RobolectricTestRunner::class)
class ChromeChordTest {
    private var chords = 0
    private val chord = ChromeChord { chords++ }

    private fun key(
        action: Int,
        keyCode: Int,
        time: Long,
        repeat: Int = 0,
        device: Int = 0,
    ) = KeyEvent(time, time, action, keyCode, repeat, 0, device, 0, 0, InputDevice.SOURCE_GAMEPAD)

    private fun press(keyCode: Int, time: Long, device: Int = 0) =
        chord.onKey(key(KeyEvent.ACTION_DOWN, keyCode, time, device = device))

    private fun release(keyCode: Int, time: Long, device: Int = 0) =
        chord.onKey(key(KeyEvent.ACTION_UP, keyCode, time, device = device))

    private fun hold(time: Long = 10_000, device: Int = 0) {
        press(KeyEvent.KEYCODE_BUTTON_SELECT, time, device)
        press(KeyEvent.KEYCODE_BUTTON_START, time, device)
    }

    @Test
    fun `Select and Start held for one second complete the chord at the deadline`() {
        hold(10_000)

        assertThat(chord.deadline()).isEqualTo(10_000 + MenuChordDetector.HOLD_MS)
        chord.check(10_000 + MenuChordDetector.HOLD_MS - 1)
        assertThat(chords).isEqualTo(0)
        chord.check(10_000 + MenuChordDetector.HOLD_MS)
        assertThat(chords).isEqualTo(1)
    }

    @Test
    fun `a quick tap does nothing`() {
        hold(10_000)
        release(KeyEvent.KEYCODE_BUTTON_START, 10_300)
        release(KeyEvent.KEYCODE_BUTTON_SELECT, 10_310)

        chord.check(20_000)

        assertThat(chords).isEqualTo(0)
        assertThat(chord.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `a key that went down before the chrome took the input and repeats does not start the chord`() {
        chord.onKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT, 10_000, repeat = 3))
        chord.onKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START, 10_000, repeat = 3))

        chord.check(20_000)

        assertThat(chords).isEqualTo(0)
        assertThat(chord.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `the chord is told once while the buttons stay down`() {
        hold(10_000)
        chord.check(11_000)
        chord.check(12_000)
        press(KeyEvent.KEYCODE_BUTTON_START, 12_500)

        assertThat(chords).isEqualTo(1)
    }

    @Test
    fun `two pads that each hold one button do not make the chord`() {
        press(KeyEvent.KEYCODE_BUTTON_SELECT, 10_000, device = 4)
        press(KeyEvent.KEYCODE_BUTTON_START, 10_000, device = 5)

        chord.check(20_000)

        assertThat(chords).isEqualTo(0)
    }

    @Test
    fun `other keys are left out of the chord`() {
        hold(10_000)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 10_200)

        chord.check(11_000)

        assertThat(chords).isEqualTo(1)
    }

    @Test
    fun `reset forgets the keys held`() {
        hold(10_000)

        chord.reset()
        chord.check(20_000)

        assertThat(chords).isEqualTo(0)
        assertThat(chord.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }
}
