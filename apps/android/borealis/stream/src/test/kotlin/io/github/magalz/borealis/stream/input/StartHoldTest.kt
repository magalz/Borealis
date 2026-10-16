package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import org.junit.Test

class StartHoldTest {
    private val hold = StartHold()

    @Test
    fun `letting go of Start after the hold time is a long hold`() {
        assertThat(hold.update(START, 10_000)).isFalse()

        assertThat(hold.update(0, 10_000 + StartHold.HOLD_MS + 1)).isTrue()
    }

    @Test
    fun `letting go of Start at the hold time is not yet a long hold`() {
        hold.update(START, 10_000)

        assertThat(hold.update(0, 10_000 + StartHold.HOLD_MS)).isFalse()
    }

    @Test
    fun `reports while Start stays down are not a long hold`() {
        hold.update(START, 10_000)

        assertThat(hold.update(START or A, 12_000)).isFalse()
        assertThat(hold.update(START, 13_000)).isFalse()
    }

    @Test
    fun `another button coming up is not a long hold`() {
        hold.update(START or A, 10_000)

        assertThat(hold.update(START, 12_000)).isFalse()
    }

    @Test
    fun `a long hold is told once`() {
        hold.update(START, 10_000)
        assertThat(hold.update(0, 12_000)).isTrue()

        assertThat(hold.update(0, 13_000)).isFalse()
    }

    @Test
    fun `a Start that went down again is timed from its latest press`() {
        hold.update(START, 10_000)
        hold.update(0, 10_100)
        hold.update(START, 20_000)

        assertThat(hold.update(0, 20_100)).isFalse()
    }
}
