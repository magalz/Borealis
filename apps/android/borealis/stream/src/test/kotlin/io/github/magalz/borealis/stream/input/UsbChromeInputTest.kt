package io.github.magalz.borealis.stream.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_LEFT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_RIGHT
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** A key as the chrome received it: its action, its code and how often it repeated. */
private data class Seen(val action: Int, val keyCode: Int, val repeat: Int)

private const val DOWN = KeyEvent.ACTION_DOWN
private const val UP = KeyEvent.ACTION_UP

@RunWith(RobolectricTestRunner::class)
class UsbChromeInputTest {
    private val seen = mutableListOf<Seen>()
    private var capturing = true
    private val input =
        UsbChromeInput(Handler(Looper.getMainLooper())) {
            if (capturing) {
                ChromeKeySink {
                    seen += Seen(it.action, it.keyCode, it.repeatCount)
                    true
                }
            } else {
                null
            }
        }

    private fun advanceBy(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    private fun stick(x: Float, y: Float = 0f, buttons: Int = 0) {
        input.report(buttons, x, y)
        advanceBy(0)
    }

    @Test
    fun `the stick pushed right presses D-pad right and returning to neutral releases it`() {
        stick(1f)
        stick(0f)

        assertThat(seen)
            .containsExactly(
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                Seen(UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
            )
            .inOrder()
    }

    @Test
    fun `the stick moves focus in all four directions, and down is positive`() {
        val keys =
            listOf(
                (-1f to 0f) to KeyEvent.KEYCODE_DPAD_LEFT,
                (1f to 0f) to KeyEvent.KEYCODE_DPAD_RIGHT,
                (0f to -1f) to KeyEvent.KEYCODE_DPAD_UP,
                (0f to 1f) to KeyEvent.KEYCODE_DPAD_DOWN,
            )
        for ((position, keyCode) in keys) {
            seen.clear()
            stick(position.first, position.second)
            assertThat(seen).containsExactly(Seen(DOWN, keyCode, 0))
            stick(0f)
        }
    }

    @Test
    fun `a stick inside the deadzone presses nothing`() {
        stick(0.3f)
        stick(0f, -0.3f)
        stick(-0.3f, 0.3f)

        assertThat(seen).isEmpty()
    }

    @Test
    fun `a stick that settles near neutral releases the key and does not press another`() {
        stick(1f)
        stick(0.3f)

        assertThat(seen)
            .containsExactly(
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                Seen(UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
            )
            .inOrder()
    }

    @Test
    fun `a diagonal goes the way the stick is pushed further`() {
        stick(0.6f, 0.9f)

        assertThat(seen).containsExactly(Seen(DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0))
    }

    @Test
    fun `a held stick repeats the key after the delay and then at the interval`() {
        stick(1f)

        advanceBy(UsbChromeInput.REPEAT_DELAY_MS - 1)
        assertThat(seen).hasSize(1)
        advanceBy(1)
        assertThat(seen.last()).isEqualTo(Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1))
        advanceBy(UsbChromeInput.REPEAT_INTERVAL_MS)
        assertThat(seen.last()).isEqualTo(Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 2))
        assertThat(seen).hasSize(3)
    }

    @Test
    fun `a stick that is held and reported again does not press again`() {
        stick(1f)
        stick(0.9f)
        stick(1f)

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `no repeat follows the release`() {
        stick(1f)
        stick(0f)
        val before = seen.size

        advanceBy(5_000)

        assertThat(seen).hasSize(before)
        assertThat(seen).isNotEmpty()
    }

    @Test
    fun `the opposite direction releases the first key and presses the other, and only that repeats`() {
        stick(1f)
        stick(-1f)

        assertThat(seen)
            .containsExactly(
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                Seen(UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0),
            )
            .inOrder()
        seen.clear()

        advanceBy(UsbChromeInput.REPEAT_DELAY_MS)

        assertThat(seen).containsExactly(Seen(DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 1))
    }

    @Test
    fun `the D-pad buttons repeat like the stick`() {
        stick(0f, buttons = DPAD_RIGHT)
        advanceBy(UsbChromeInput.REPEAT_DELAY_MS)

        assertThat(seen)
            .containsExactly(
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1),
            )
            .inOrder()
    }

    @Test
    fun `the D-pad and the stick pushed the same way are one key`() {
        stick(1f, buttons = DPAD_RIGHT)
        stick(0f, buttons = DPAD_RIGHT)

        assertThat(seen).containsExactly(Seen(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
    }

    @Test
    fun `the face buttons do not repeat`() {
        stick(0f, buttons = A)
        advanceBy(5_000)

        assertThat(seen).containsExactly(Seen(DOWN, KeyEvent.KEYCODE_BUTTON_A, 0))
    }

    @Test
    fun `the buttons reach the chrome with the stick`() {
        stick(1f, buttons = A or DPAD_LEFT)

        assertThat(seen.map { it.keyCode })
            .containsExactly(
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            )
    }

    @Test
    fun `nothing reaches the chrome while it does not have the input`() {
        capturing = false

        stick(1f, buttons = A)
        advanceBy(5_000)
        stick(0f)

        assertThat(seen).isEmpty()
    }

    @Test
    fun `a stick held when the chrome takes the input presses nothing and releases nothing`() {
        capturing = false
        stick(1f)
        capturing = true

        stick(1f)
        advanceBy(5_000)
        stick(0f)

        assertThat(seen).isEmpty()
    }

    @Test
    fun `the repeat stops when the chrome gives the input back`() {
        stick(1f)
        capturing = false

        advanceBy(5_000)

        assertThat(seen).hasSize(1)
    }
}
