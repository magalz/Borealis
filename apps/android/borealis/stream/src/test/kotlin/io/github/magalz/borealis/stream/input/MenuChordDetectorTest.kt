package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    private fun feedLone(buttons: Int, time: Long) =
        detector.update(buttons, time, true, holdLone = true)

    @Test
    fun `a lone Start is hidden from the PC for 60 ms, then the check lets it through`() {
        val pressed = feedLone(START, 100)

        assertThat(pressed.silent).isFalse()
        assertThat(detector.hidden()).isEqualTo(START)
        assertThat(detector.deadline()).isEqualTo(160)
        assertThat(detector.due(159).silent).isTrue()
        assertThat(detector.hidden()).isEqualTo(START)

        val due = detector.due(160)

        assertThat(due.silent).isFalse()
        assertThat(due.fired).isFalse()
        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `a lone Select is held back the same way`() {
        feedLone(SELECT, 0)

        assertThat(detector.hidden()).isEqualTo(SELECT)
        assertThat(detector.due(60).silent).isFalse()
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `the partner within 60 ms starts the chord and nothing was shown to the PC`() {
        feedLone(START, 0)

        val update = feedLone(chord, 59)

        assertThat(update.loneTap).isEqualTo(0)
        assertThat(detector.hidden()).isEqualTo(chord)
        assertThat(detector.deadline()).isEqualTo(1_059)
        assertThat(detector.due(60).silent).isTrue()
        assertThat(detector.check(1_059)).isTrue()
    }

    @Test
    fun `another button sends the held back button at once`() {
        feedLone(START, 0)

        val update = feedLone(START or A, 20)

        assertThat(update.silent).isFalse()
        assertThat(update.loneTap).isEqualTo(0)
        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `letting the button go within 60 ms is a short press of it`() {
        feedLone(START, 0)

        val update = feedLone(0, 30)

        assertThat(update.loneTap).isEqualTo(START)
        assertThat(update.silent).isTrue()
        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `a button that went down after the wait is not held back again by a later event`() {
        feedLone(START, 0)
        detector.due(60)

        feedLone(START, 100)

        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `the partner after the wait still starts a chord`() {
        feedLone(START, 0)
        detector.due(60)

        feedLone(chord, 200)

        assertThat(detector.hidden()).isEqualTo(chord)
        assertThat(detector.check(1_200)).isTrue()
    }

    @Test
    fun `a button held when another one goes down is not a lone press`() {
        feedLone(A, 0)

        feedLone(A or START, 10)

        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `without holdLone a lone Start is sent at once`() {
        feed(START, 0, mask = true)

        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    @Test
    fun `the quit combo built on a held back Start still reaches the PC`() {
        feedLone(START, 0)
        feedLone(chord, 30)
        feedLone(chord or leftBumper, 100)

        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.check(5_000)).isFalse()
    }

    @Test
    fun `reset forgets a held back button`() {
        feedLone(START, 0)

        detector.reset()

        assertThat(detector.hidden()).isEqualTo(0)
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
    }

    /** Start, then Select, then both bumpers, as the quit combo is pressed on a system pad. */
    private fun pressTheQuitCombo() {
        feedLone(START, 0)
        feedLone(chord, 30)
        feedLone(chord or leftBumper, 100)
        feedLone(chord or leftBumper or RIGHT_BUMPER, 150)
    }

    @Test
    fun `Start and Select left held when the bumpers go up do not arm the chord again`() {
        pressTheQuitCombo()

        feedLone(chord or RIGHT_BUMPER, 1_000)
        val rejoined = feedLone(chord, 1_100)

        assertThat(rejoined.fired).isFalse()
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.claims(START)).isFalse()
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `letting go of Start after the quit combo is no early release and reaches the PC`() {
        pressTheQuitCombo()
        feedLone(chord or RIGHT_BUMPER, 1_000)
        feedLone(chord, 1_100)

        val update = feedLone(SELECT, 1_300)

        assertThat(update.earlyRelease).isFalse()
        assertThat(update.fired).isFalse()
        assertThat(update.silent).isFalse()
        assertThat(update.loneTap).isEqualTo(0)
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `the other bumper going up last leaves Start and Select unarmed too`() {
        pressTheQuitCombo()
        feedLone(chord or leftBumper, 1_000)
        feedLone(chord, 1_100)

        val update = feedLone(START, 1_300)

        assertThat(update.earlyRelease).isFalse()
        assertThat(update.silent).isFalse()
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `Start and Select held on for a second after the quit combo do not open the menu`() {
        pressTheQuitCombo()
        feedLone(chord or RIGHT_BUMPER, 1_000)
        feedLone(chord, 1_100)

        assertThat(detector.check(2_100)).isFalse()
        assertThat(feedLone(chord, 2_100).fired).isFalse()
        assertThat(detector.check(9_000)).isFalse()
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `bumpers pressed first and let go last leave Start and Select unarmed`() {
        feedLone(leftBumper, 0)
        feedLone(leftBumper or RIGHT_BUMPER, 20)
        feedLone(leftBumper or RIGHT_BUMPER or START, 40)
        feedLone(leftBumper or RIGHT_BUMPER or chord, 60)

        feedLone(RIGHT_BUMPER or chord, 200)
        feedLone(chord, 300)
        val update = feedLone(START, 500)

        assertThat(update.earlyRelease).isFalse()
        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `no order of letting go of the quit combo arms the chord`() {
        val buttons = listOf(START, SELECT, leftBumper, RIGHT_BUMPER)
        val orders = buttons.permutations()

        for (order in orders) {
            detector.reset()
            pressTheQuitCombo()
            var held = chord or leftBumper or RIGHT_BUMPER
            var time = 1_000L
            for (button in order) {
                held = held and button.inv()
                val update = feedLone(held, time)
                assertWithMessage("order $order after $button")
                    .that(update.earlyRelease || update.fired || update.silent)
                    .isFalse()
                assertWithMessage("order $order after $button").that(detector.hidden()).isEqualTo(0)
                assertWithMessage("order $order after $button")
                    .that(detector.deadline())
                    .isEqualTo(MenuChordDetector.NO_DEADLINE)
                time += 100
            }
        }
    }

    private fun <T> List<T>.permutations(): List<List<T>> =
        if (size <= 1) listOf(this)
        else flatMap { head -> (this - head).permutations().map { listOf(head) + it } }

    @Test
    fun `a button held with Start and Select keeps the chord from arming once it is let go`() {
        feedLone(A, 0)
        feedLone(A or START, 20)
        feedLone(A or chord, 40)

        feedLone(chord, 200)

        assertThat(detector.deadline()).isEqualTo(MenuChordDetector.NO_DEADLINE)
        assertThat(detector.hidden()).isEqualTo(0)
    }

    @Test
    fun `after every button was released Start and Select arm the chord again`() {
        pressTheQuitCombo()
        feedLone(chord or RIGHT_BUMPER, 1_000)
        feedLone(chord, 1_100)
        feedLone(0, 1_200)

        feedLone(START, 2_000)
        feedLone(chord, 2_030)

        assertThat(detector.hidden()).isEqualTo(chord)
        assertThat(detector.deadline()).isEqualTo(3_030)
        assertThat(detector.check(3_030)).isTrue()
    }

    @Test
    fun `after the quit combo is released a lone Start is held back again`() {
        pressTheQuitCombo()
        feedLone(chord or RIGHT_BUMPER, 1_000)
        feedLone(chord, 1_100)
        feedLone(0, 1_200)

        feedLone(START, 2_000)

        assertThat(detector.hidden()).isEqualTo(START)
        assertThat(detector.deadline()).isEqualTo(2_060)
    }
}
