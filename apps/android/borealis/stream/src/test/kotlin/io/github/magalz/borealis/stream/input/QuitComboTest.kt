package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.LEFT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import org.junit.Test

class QuitComboTest {
    private val combo = QuitCombo()
    private val all = START or SELECT or LEFT_BUMPER or RIGHT_BUMPER

    @Test
    fun `it arms on exactly Start, Select and both bumpers`() {
        assertThat(combo.pending()).isFalse()

        assertThat(combo.update(all)).isFalse()

        assertThat(combo.pending()).isTrue()
    }

    @Test
    fun `three of the four buttons do not arm it`() {
        combo.update(START or SELECT or LEFT_BUMPER)
        combo.update(START or SELECT or RIGHT_BUMPER)
        combo.update(SELECT or LEFT_BUMPER or RIGHT_BUMPER)

        assertThat(combo.pending()).isFalse()
        assertThat(combo.update(0)).isFalse()
    }

    @Test
    fun `a fifth button keeps it from arming`() {
        combo.update(all or A)

        assertThat(combo.pending()).isFalse()
        assertThat(combo.update(0)).isFalse()
    }

    @Test
    fun `it fires once, on the report where everything is up`() {
        combo.update(all)

        assertThat(combo.update(0)).isTrue()
        assertThat(combo.pending()).isFalse()
        assertThat(combo.update(0)).isFalse()
    }

    @Test
    fun `it does not fire while a button is still held`() {
        combo.update(all)

        assertThat(combo.update(START or SELECT)).isFalse()
        assertThat(combo.update(START)).isFalse()
        assertThat(combo.pending()).isTrue()
    }

    @Test
    fun `a partial release followed by a re-press fires once`() {
        combo.update(all)
        combo.update(START or SELECT)
        combo.update(all)

        assertThat(combo.update(0)).isTrue()
        assertThat(combo.update(0)).isFalse()
    }

    @Test
    fun `reset disarms it`() {
        combo.update(all)

        combo.reset()

        assertThat(combo.pending()).isFalse()
        assertThat(combo.update(0)).isFalse()
    }

    @Test
    fun `a fifth button pressed and let go while the four stay held still arms it`() {
        combo.update(all or A)
        assertThat(combo.pending()).isFalse()

        combo.update(all)

        assertThat(combo.pending()).isTrue()
        assertThat(combo.update(0)).isTrue()
    }
}
