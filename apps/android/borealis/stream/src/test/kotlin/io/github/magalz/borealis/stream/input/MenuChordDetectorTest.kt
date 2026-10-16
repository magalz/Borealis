package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import org.junit.Test

class MenuChordDetectorTest {
    private val detector = MenuChordDetector()
    private val chord = START or SELECT
    private val leftBumper = 0x0100

    private fun feed(buttons: Int, time: Long, mask: Boolean = false) =
        detector.update(buttons, time, mask)

    @Test
    fun `the chord fires at one second and not before`() {
        feed(chord, 0)

        assertThat(feed(chord, 999).fired).isFalse()
        assertThat(feed(chord, 1_000).fired).isTrue()
    }

    @Test
    fun `the scheduled check fires at one second and not before`() {
        feed(chord, 500)

        assertThat(detector.deadline()).isEqualTo(1_500)
        assertThat(detector.check(1_499)).isFalse()
        assertThat(detector.check(1_500)).isTrue()
    }

    @Test
    fun `the hold starts when both buttons are down, whichever came first`() {
        feed(START, 0)
        feed(chord, 300)

        assertThat(detector.check(1_299)).isFalse()
        assertThat(detector.check(1_300)).isTrue()
    }

    @Test
    fun `there is no deadline until the chord is held`() {
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        feed(START, 0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `a third button cancels the chord`() {
        feed(chord, 0)

        val update = feed(chord or A, 400)

        assertThat(update.fired).isFalse()
        assertThat(update.earlyRelease).isFalse()
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.check(5_000)).isFalse()
        assertThat(feed(chord or A, 5_000).fired).isFalse()
    }

    @Test
    fun `the quit combo never fires the chord`() {
        feed(START, 0)
        feed(START or SELECT, 100)
        feed(START or SELECT or leftBumper, 200)
        feed(START or SELECT or leftBumper or RIGHT_BUMPER, 300)

        assertThat(detector.check(2_000)).isFalse()
        assertThat(feed(START or SELECT or leftBumper or RIGHT_BUMPER, 2_000).fired).isFalse()
    }

    @Test
    fun `releasing either button before one second cancels the chord and says so`() {
        feed(chord, 0)

        val update = feed(START, 600)

        assertThat(update.earlyRelease).isTrue()
        assertThat(update.fired).isFalse()
        assertThat(detector.check(5_000)).isFalse()
    }

    @Test
    fun `releasing both buttons together before one second is an early release too`() {
        feed(chord, 0)

        assertThat(feed(0, 600).earlyRelease).isTrue()
    }

    @Test
    fun `an event after the deadline fires the chord before it is processed`() {
        feed(chord, 0)

        val update = feed(START, 1_400)

        assertThat(update.fired).isTrue()
        assertThat(update.earlyRelease).isFalse()
    }

    @Test
    fun `the chord fires once and then stays quiet while it is held`() {
        feed(chord, 0)
        assertThat(feed(chord, 1_000).fired).isTrue()

        assertThat(feed(chord, 1_100).fired).isFalse()
        assertThat(detector.check(5_000)).isFalse()
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `after firing the two buttons stay hidden until each is released`() {
        feed(chord, 0)
        feed(chord, 1_000)

        assertThat(detector.hidden()).isEqualTo(chord)
        feed(SELECT, 1_300)
        assertThat(detector.hidden()).isEqualTo(SELECT)
        feed(0, 1_400)
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `releasing a hidden button needs no packet`() {
        feed(chord, 0)
        feed(chord, 1_000)

        assertThat(feed(SELECT, 1_300).silent).isTrue()
        assertThat(feed(0, 1_400).silent).isTrue()
    }

    @Test
    fun `an event that moves a stick, not a button, is not silent`() {
        feed(chord, 0)
        feed(chord, 1_000)

        assertThat(feed(chord, 1_100).silent).isFalse()
    }

    @Test
    fun `a button pressed while the chord is hidden is not silent and not hidden`() {
        feed(chord, 0)
        feed(chord, 1_000)

        val update = feed(chord or A, 1_100)

        assertThat(update.silent).isFalse()
        assertThat(detector.hidden()).isEqualTo(chord)
    }

    @Test
    fun `pressing the buttons again after the chord fired does not start another chord`() {
        feed(chord, 0)
        feed(chord, 1_000)
        feed(START, 1_200)

        feed(chord, 1_300)

        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.check(9_000)).isFalse()
    }

    @Test
    fun `the chord can fire again once every button was released`() {
        feed(chord, 0)
        feed(chord, 1_000)
        feed(0, 1_500)

        feed(chord, 2_000)

        assertThat(detector.check(3_000)).isTrue()
    }

    @Test
    fun `without masking a pending chord is not hidden from the PC`() {
        feed(chord, 0, mask = false)

        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `with masking a pending chord is hidden from the PC from the start`() {
        feed(START, 0, mask = true)
        assertThat(detector.hidden()).isEqualTo(0)

        feed(chord, 100, mask = true)

        assertThat(detector.hidden()).isEqualTo(chord)
    }

    @Test
    fun `an early release keeps the other button hidden until it is released`() {
        feed(chord, 0, mask = true)

        feed(START, 300, mask = true)
        assertThat(detector.hidden()).isEqualTo(START)
        assertThat(feed(0, 400, mask = true).silent).isTrue()
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `a chord cancelled by a third button stops hiding its buttons`() {
        feed(chord, 0, mask = true)

        feed(chord or A, 200, mask = true)

        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `Start belongs to the chord from the moment it is pending until it is released`() {
        feed(START, 0)
        assertThat(detector.claims(START)).isFalse()

        feed(chord, 100)
        assertThat(detector.claims(START)).isTrue()
        assertThat(detector.claims(SELECT)).isTrue()
        assertThat(detector.claims(A)).isFalse()

        feed(SELECT, 1_300)
        assertThat(detector.claims(START)).isFalse()
        assertThat(detector.claims(SELECT)).isTrue()
    }

    @Test
    fun `Start does not belong to a cancelled chord`() {
        feed(chord, 0)

        feed(chord or A, 100)

        assertThat(detector.claims(START)).isFalse()
    }

    @Test
    fun `reset forgets a pending chord`() {
        feed(chord, 0)

        detector.reset()

        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.check(5_000)).isFalse()
        assertThat(detector.hidden()).isEqualTo(0)
    }
}
