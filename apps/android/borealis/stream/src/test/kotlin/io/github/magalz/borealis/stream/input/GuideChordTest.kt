package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.B
import io.github.magalz.borealis.stream.input.GamepadButtons.GUIDE
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import org.junit.Test

/** Pins how the Guide button is made from other buttons, as the controller handler did it. */
class GuideChordTest {
    private val none = 0

    private fun down(
        inputMap: Int,
        hasSelect: Boolean = false,
        hasMode: Boolean = false,
        eventTime: Long = 10_000,
        lastRightBumperUp: Long = 0,
    ) = GuideChord.onButtonDown(inputMap, none, hasSelect, hasMode, eventTime, lastRightBumperUp)

    @Test
    fun `Start and Select on a pad with Select and no Mode button become the Guide button`() {
        val state = down(START or SELECT, hasSelect = true)

        assertThat(state.inputMap).isEqualTo(GUIDE)
        assertThat(state.emulatingFlags).isEqualTo(GuideChord.EMULATING_SPECIAL)
    }

    @Test
    fun `Start and Select stay as they are on a pad with a Mode button`() {
        val state = down(START or SELECT, hasSelect = true, hasMode = true)

        assertThat(state).isEqualTo(ButtonState(START or SELECT, none))
    }

    @Test
    fun `Start and the right bumper become the Guide button on a pad without Select`() {
        val state = down(START or RIGHT_BUMPER)

        assertThat(state.inputMap).isEqualTo(GUIDE)
        assertThat(state.emulatingFlags).isEqualTo(GuideChord.EMULATING_SPECIAL)
    }

    @Test
    fun `Start alone within 100 ms of the right bumper going up is the Guide button`() {
        val state = down(START, eventTime = 10_100, lastRightBumperUp = 10_000)

        assertThat(state.inputMap).isEqualTo(GUIDE)
    }

    @Test
    fun `Start alone 101 ms after the right bumper went up is just Start`() {
        val state = down(START, eventTime = 10_101, lastRightBumperUp = 10_000)

        assertThat(state).isEqualTo(ButtonState(START, none))
    }

    @Test
    fun `Start and Select with a third button are not the Guide button`() {
        val state = down(START or SELECT or RIGHT_BUMPER, hasSelect = true)

        assertThat(state.inputMap).isEqualTo(START or SELECT or RIGHT_BUMPER)
    }

    @Test
    fun `the emulated Guide button ends when Start comes up`() {
        val state = GuideChord.onButtonUp(GUIDE, GuideChord.EMULATING_SPECIAL)

        assertThat(state).isEqualTo(ButtonState(0, none))
    }

    @Test
    fun `the emulated Guide button ends when neither Select nor the right bumper is held`() {
        val state = GuideChord.onButtonUp(GUIDE or START, GuideChord.EMULATING_SPECIAL)

        assertThat(state).isEqualTo(ButtonState(START, none))
    }

    @Test
    fun `the emulated Guide button stays while Start and Select are still held`() {
        val state =
            GuideChord.onButtonUp(GUIDE or START or B or SELECT, GuideChord.EMULATING_SPECIAL)

        assertThat(state.inputMap).isEqualTo(GUIDE or START or B or SELECT)
        assertThat(state.emulatingFlags).isEqualTo(GuideChord.EMULATING_SPECIAL)
    }

    @Test
    fun `a Guide button from a real Mode button is left alone`() {
        val state = GuideChord.onButtonUp(GUIDE, none)

        assertThat(state).isEqualTo(ButtonState(GUIDE, none))
    }
}
