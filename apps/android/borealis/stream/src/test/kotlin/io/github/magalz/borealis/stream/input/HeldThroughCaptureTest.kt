package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import org.junit.Test

class CaptureEndTest {
    private val end = CaptureEnd()

    @Test
    fun `before the input has been given back nothing began before it`() {
        assertThat(end.began(downTime = 500)).isFalse()
    }

    @Test
    fun `a key down since before the input came back began before it`() {
        end.ended(10_000)

        assertThat(end.began(downTime = 9_999)).isTrue()
    }

    @Test
    fun `a key that went down when or after the input came back did not`() {
        end.ended(10_000)

        assertThat(end.began(downTime = 10_000)).isFalse()
        assertThat(end.began(downTime = 12_000)).isFalse()
    }
}

class HeldSinceCaptureTest {
    private val held = HeldSinceCapture()

    @Test
    fun `no button counts while the chrome has the input`() {
        assertThat(held.filter(START or SELECT, captured = true)).isEqualTo(0)
    }

    @Test
    fun `a button held through the capture does not count until it is let go`() {
        held.filter(START or SELECT, captured = true)

        assertThat(held.filter(START or SELECT, captured = false)).isEqualTo(0)
        assertThat(held.filter(START, captured = false)).isEqualTo(0)
        assertThat(held.filter(0, captured = false)).isEqualTo(0)
        assertThat(held.filter(START, captured = false)).isEqualTo(START)
    }

    @Test
    fun `a button pressed after the capture counts beside one still held`() {
        held.filter(START, captured = true)

        assertThat(held.filter(START or A, captured = false)).isEqualTo(A)
    }

    @Test
    fun `buttons count as they are when nothing was held`() {
        assertThat(held.filter(A, captured = false)).isEqualTo(A)
    }
}
