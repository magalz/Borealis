package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.GUIDE
import io.github.magalz.borealis.stream.input.GamepadButtons.LEFT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import io.github.magalz.borealis.stream.input.MenuChordDetector.Companion.NO_DEADLINE
import io.github.magalz.borealis.stream.input.MenuChordDetector.Update
import org.junit.Test

private const val CHORD = START or SELECT

/**
 * Whole sequences of events, with what each answers and what the detector holds after it. A step is
 * checked against the state it leaves, so a change to the order in which `update` settles its
 * fields shows even when the answer stays the same.
 */
class MenuChordDetectorTableTest {
    @Test
    fun `chords held, released early, cancelled and fired again`() =
        ChordTable.replayAll(ChordTable.CHORD_CASES)

    @Test
    fun `a lone Start or Select waits for its partner`() =
        ChordTable.replayAll(ChordTable.LONE_CASES)

    @Test
    fun `what is left of the quit combo never arms the chord`() =
        ChordTable.replayAll(ChordTable.QUIT_COMBO_CASES)

    @Test
    fun `reset forgets the chord, the wait and the buttons held`() =
        ChordTable.replayAll(ChordTable.RESET_CASES)
}

/** The events, the expected state and the cases of the table, apart from other tests' names. */
private object ChordTable {
    sealed interface Event

    /** The pad reports that it holds [buttons] at [time]. */
    data class Press(val buttons: Int, val time: Long) : Event

    /** The scheduled check at [time]. */
    data class Due(val time: Long) : Event

    data object Reset : Event

    /**
     * What a step left behind: the answer of the event (none for a reset), the buttons the pad
     * sends the PC as a Guide tap or a short press for that answer, what the detector hides from
     * the PC, its next deadline, and which of Start and Select belong to a chord (see
     * [MenuChordDetector.claims]).
     */
    data class Seen(
        val update: Update?,
        val tap: Int,
        val hidden: Int,
        val deadline: Long,
        val claimed: Int,
    )

    fun seen(
        update: Update,
        tap: Int = 0,
        hidden: Int = 0,
        deadline: Long = NO_DEADLINE,
        claimed: Int = 0,
    ) = Seen(update, tap, hidden, deadline, claimed)

    /** A detector with nothing pending, hidden or claimed, as after a reset. */
    val CLEAR = Seen(null, 0, 0, NO_DEADLINE, 0)

    val NOTHING = Update(fired = false, earlyRelease = false, silent = false)
    val SILENT = Update(fired = false, earlyRelease = false, silent = true)
    val FIRED = Update(fired = true, earlyRelease = false, silent = false)
    val EARLY = Update(fired = false, earlyRelease = true, silent = true)
    val EARLY_SHOWN = Update(fired = false, earlyRelease = true, silent = false)

    fun letGo(button: Int, silent: Boolean) =
        Update(fired = false, earlyRelease = false, silent = silent, loneTap = button)

    /** [mask] and [holdLone] are what the caller passes to every `update` of the case. */
    class Case(
        val name: String,
        val mask: Boolean,
        val holdLone: Boolean,
        val steps: List<Pair<Event, Seen>>,
    )

    fun case(
        name: String,
        vararg steps: Pair<Event, Seen>,
        mask: Boolean = true,
        holdLone: Boolean = false,
    ) = Case(name, mask, holdLone, steps.toList())

    fun claimed(detector: MenuChordDetector) =
        (if (detector.claims(START)) START else 0) or (if (detector.claims(SELECT)) SELECT else 0)

    /** Feeds the steps of [case] to a new detector; describes the first step that differs. */
    fun replay(case: Case): String? {
        val detector = MenuChordDetector()
        case.steps.forEachIndexed { index, (event, expected) ->
            val update =
                when (event) {
                    is Press -> detector.update(event.buttons, event.time, case.mask, case.holdLone)
                    is Due -> detector.due(event.time)
                    Reset -> null.also { detector.reset() }
                }
            val tap =
                update?.let { GuideChord.tapButtons(it, hasSelect = true, hasMode = false) } ?: 0
            val actual =
                Seen(update, tap, detector.hidden(), detector.deadline(), claimed(detector))
            if (actual != expected) {
                return "${case.name}, step ${index + 1} $event:\n  expected $expected\n  but was  $actual"
            }
        }
        return null
    }

    fun replayAll(cases: List<Case>) {
        val mismatches = cases.mapNotNull { replay(it) }
        assertWithMessage(mismatches.joinToString("\n")).that(mismatches).isEmpty()
    }

    val CHORD_CASES =
        listOf(
            case(
                "held to one second, fires once, then stays quiet until released and pressed again",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 999) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Press(CHORD, 1_100) to seen(NOTHING, hidden = CHORD, claimed = CHORD),
                Due(1_200) to seen(SILENT, hidden = CHORD, claimed = CHORD),
                Press(SELECT, 1_300) to seen(SILENT, hidden = SELECT, claimed = SELECT),
                Press(0, 1_400) to seen(SILENT),
                Press(CHORD, 2_000) to
                    seen(NOTHING, hidden = CHORD, deadline = 3_000, claimed = CHORD),
                Due(2_999) to seen(SILENT, hidden = CHORD, deadline = 3_000, claimed = CHORD),
                Due(3_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
            ),
            case(
                "the scheduled check fires at the deadline and not before",
                Press(CHORD, 500) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_500, claimed = CHORD),
                Due(1_499) to seen(SILENT, hidden = CHORD, deadline = 1_500, claimed = CHORD),
                Due(1_500) to seen(FIRED, hidden = CHORD, claimed = CHORD),
            ),
            case(
                "Select released before one second is an early release and a Guide tap",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(START, 600) to seen(EARLY, tap = GUIDE, hidden = START, claimed = START),
                Due(5_000) to seen(SILENT, hidden = START, claimed = START),
                Press(0, 5_100) to seen(SILENT),
            ),
            case(
                "Start released before one second is an early release too",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(SELECT, 300) to seen(EARLY, tap = GUIDE, hidden = SELECT, claimed = SELECT),
                Press(0, 400) to seen(SILENT),
            ),
            case(
                "both released together before one second",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(0, 600) to seen(EARLY, tap = GUIDE),
            ),
            case(
                "a release at 999 ms is early",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(START, 999) to seen(EARLY, tap = GUIDE, hidden = START, claimed = START),
            ),
            case(
                "a release at one second fires the chord first, then hides what is still held",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(START, 1_000) to seen(FIRED, hidden = START, claimed = START),
                Press(0, 1_100) to seen(SILENT),
            ),
            case(
                "a chord that fires on the release of both buttons hides nothing",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(0, 1_400) to seen(FIRED),
            ),
            case(
                "a chord that fires with a third button in the same event cannot arm again at once",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD or A, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Press(A, 1_100) to seen(SILENT),
                Press(A or CHORD, 1_200) to seen(NOTHING),
                Press(CHORD, 1_300) to seen(NOTHING),
                Press(0, 1_400) to seen(NOTHING),
                Press(CHORD, 1_500) to
                    seen(NOTHING, hidden = CHORD, deadline = 2_500, claimed = CHORD),
            ),
            case(
                "a third button cancels the chord, and Start and Select held on do not arm another",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD or A, 400) to seen(NOTHING),
                Due(5_000) to seen(SILENT),
                Press(CHORD or A, 5_000) to seen(NOTHING),
                Press(CHORD, 5_100) to seen(NOTHING),
                Press(0, 5_200) to seen(NOTHING),
                Press(CHORD, 6_000) to
                    seen(NOTHING, hidden = CHORD, deadline = 7_000, claimed = CHORD),
                Due(7_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
            ),
            case(
                "a button pressed while the fired chord is hidden is shown, the chord stays hidden",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Press(CHORD or A, 1_100) to seen(NOTHING, hidden = CHORD, claimed = CHORD),
                Press(CHORD, 1_200) to seen(NOTHING, hidden = CHORD, claimed = CHORD),
                Press(0, 1_300) to seen(SILENT),
            ),
            case(
                "after a fire, pressing the released button again does not start another chord",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Press(START, 1_200) to seen(SILENT, hidden = START, claimed = START),
                Press(CHORD, 1_300) to seen(NOTHING, hidden = START, claimed = START),
                Due(9_000) to seen(SILENT, hidden = START, claimed = START),
                Press(0, 9_100) to seen(NOTHING),
                Press(CHORD, 9_200) to
                    seen(NOTHING, hidden = CHORD, deadline = 10_200, claimed = CHORD),
            ),
            case(
                "after an early release, pressing the released button again does not arm until all are up",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(START, 300) to seen(EARLY, tap = GUIDE, hidden = START, claimed = START),
                Press(CHORD, 400) to seen(NOTHING, hidden = START, claimed = START),
                Press(0, 500) to seen(NOTHING),
                Press(CHORD, 600) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_600, claimed = CHORD),
                Due(1_600) to seen(FIRED, hidden = CHORD, claimed = CHORD),
            ),
            case(
                "without masking a pending chord is not hidden, but an early release still claims a button",
                Press(CHORD, 0) to seen(NOTHING, deadline = 1_000, claimed = CHORD),
                Press(START, 600) to seen(EARLY_SHOWN, tap = GUIDE, claimed = START),
                Press(0, 700) to seen(NOTHING),
                mask = false,
            ),
            case(
                "without masking the chord is hidden from the moment it fires",
                Press(CHORD, 0) to seen(NOTHING, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Press(0, 1_100) to seen(SILENT),
                mask = false,
            ),
            case(
                "without holdLone a lone Start is shown at once, and its partner still starts the chord",
                Press(START, 0) to seen(NOTHING),
                Press(CHORD, 100) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_100, claimed = CHORD),
            ),
        )

    fun lone(name: String, vararg steps: Pair<Event, Seen>) = case(name, *steps, holdLone = true)

    val LONE_CASES =
        listOf("Start" to START, "Select" to SELECT).flatMap { (name, button) ->
            listOf(
                lone(
                    "a lone $name is hidden for 60 ms, then the check shows it",
                    Press(button, 100) to seen(NOTHING, hidden = button, deadline = 160),
                    Due(159) to seen(SILENT, hidden = button, deadline = 160),
                    Due(160) to seen(NOTHING),
                ),
                lone(
                    "a lone $name let go within the wait is a short press, and a new press waits again",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Press(0, 30) to seen(letGo(button, silent = true), tap = button),
                    Press(button, 100) to seen(NOTHING, hidden = button, deadline = 160),
                ),
                lone(
                    "a $name that went down after the wait is not held back again by a later event",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Due(60) to seen(NOTHING),
                    Press(button, 100) to seen(NOTHING),
                    Press(0, 200) to seen(NOTHING),
                    Press(button, 300) to seen(NOTHING, hidden = button, deadline = 360),
                ),
                lone(
                    "only the check ends the wait, an event of the same buttons does not",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Press(button, 100) to seen(NOTHING, hidden = button, deadline = 60),
                    Due(100) to seen(NOTHING),
                ),
                lone(
                    "another button shows the held back $name at once",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Press(button or A, 20) to seen(NOTHING),
                    Press(A, 40) to seen(NOTHING),
                    Press(0, 60) to seen(NOTHING),
                    Press(button, 100) to seen(NOTHING, hidden = button, deadline = 160),
                ),
                lone(
                    "the partner of a $name within 60 ms starts the chord and nothing was shown",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Press(CHORD, 59) to
                        seen(NOTHING, hidden = CHORD, deadline = 1_059, claimed = CHORD),
                    Due(60) to seen(SILENT, hidden = CHORD, deadline = 1_059, claimed = CHORD),
                    Due(1_059) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                ),
                lone(
                    "the partner of a $name at 60 ms, with no check before it, starts the chord too",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Press(CHORD, 60) to
                        seen(NOTHING, hidden = CHORD, deadline = 1_060, claimed = CHORD),
                ),
                lone(
                    "the partner of a $name after the wait still starts a chord",
                    Press(button, 0) to seen(NOTHING, hidden = button, deadline = 60),
                    Due(60) to seen(NOTHING),
                    Press(CHORD, 200) to
                        seen(NOTHING, hidden = CHORD, deadline = 1_200, claimed = CHORD),
                    Due(1_199) to seen(SILENT, hidden = CHORD, deadline = 1_200, claimed = CHORD),
                    Due(1_200) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                ),
            )
        } +
            listOf(START to SELECT, SELECT to START).map { (first, second) ->
                lone(
                    "a lone button replaced by the other in one event is a short press, the other is shown",
                    Press(first, 0) to seen(NOTHING, hidden = first, deadline = 60),
                    Press(second, 20) to seen(letGo(first, silent = false), tap = first),
                    Press(0, 40) to seen(NOTHING),
                )
            } +
            lone(
                "a button held when Start goes down is not a lone press and keeps the chord from arming",
                Press(A, 0) to seen(NOTHING),
                Press(A or START, 10) to seen(NOTHING),
                Press(A or CHORD, 20) to seen(NOTHING),
                Press(CHORD, 40) to seen(NOTHING),
                Press(START, 60) to seen(NOTHING),
                Press(0, 80) to seen(NOTHING),
                Press(CHORD, 100) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_100, claimed = CHORD),
            )

    /** Start, then Select, then both bumpers, as the quit combo is pressed on a system pad. */
    val QUIT_COMBO: Array<Pair<Event, Seen>> =
        arrayOf(
            Press(START, 0) to seen(NOTHING, hidden = START, deadline = 60),
            Press(CHORD, 30) to seen(NOTHING, hidden = CHORD, deadline = 1_030, claimed = CHORD),
            Press(CHORD or LEFT_BUMPER, 100) to seen(NOTHING),
            Press(CHORD or LEFT_BUMPER or RIGHT_BUMPER, 150) to seen(NOTHING),
        )

    val QUIT_COMBO_BUTTONS =
        mapOf(
            START to "Start",
            SELECT to "Select",
            LEFT_BUMPER to "left bumper",
            RIGHT_BUMPER to "right bumper",
        )

    fun <T> List<T>.permutations(): List<List<T>> =
        if (size <= 1) listOf(this)
        else flatMap { head -> (this - head).permutations().map { listOf(head) + it } }

    /**
     * The quit combo, then its buttons let go one by one in [order]: nothing happens at any step.
     */
    fun letGoInOrder(order: List<Int>): Case {
        val all = CHORD or LEFT_BUMPER or RIGHT_BUMPER
        val held = order.runningFold(all) { buttons, up -> buttons and up.inv() }
        val releases =
            held.drop(1).mapIndexed { index, buttons ->
                Press(buttons, 1_000L + 100 * index) to seen(NOTHING)
            }
        val name = "let go in the order ${order.map(QUIT_COMBO_BUTTONS::getValue)}"
        return lone(name, *QUIT_COMBO, *releases.toTypedArray())
    }

    val QUIT_COMBO_CASES =
        QUIT_COMBO_BUTTONS.keys.toList().permutations().map { letGoInOrder(it) } +
            listOf(
                lone(
                    "bumpers let go first leave Start and Select unarmed until every button is up",
                    *QUIT_COMBO,
                    Press(CHORD or RIGHT_BUMPER, 1_000) to seen(NOTHING),
                    Press(CHORD, 1_100) to seen(NOTHING),
                    Due(2_100) to seen(SILENT),
                    Press(CHORD, 2_100) to seen(NOTHING),
                    Press(SELECT, 2_200) to seen(NOTHING),
                    Press(0, 2_300) to seen(NOTHING),
                    Press(START, 3_000) to seen(NOTHING, hidden = START, deadline = 3_060),
                    Press(CHORD, 3_030) to
                        seen(NOTHING, hidden = CHORD, deadline = 4_030, claimed = CHORD),
                    Due(4_030) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                ),
                lone(
                    "bumpers pressed first and let go last leave Start and Select unarmed",
                    Press(LEFT_BUMPER, 0) to seen(NOTHING),
                    Press(LEFT_BUMPER or RIGHT_BUMPER, 20) to seen(NOTHING),
                    Press(LEFT_BUMPER or RIGHT_BUMPER or START, 40) to seen(NOTHING),
                    Press(LEFT_BUMPER or RIGHT_BUMPER or CHORD, 60) to seen(NOTHING),
                    Press(RIGHT_BUMPER or CHORD, 200) to seen(NOTHING),
                    Press(CHORD, 300) to seen(NOTHING),
                    Press(START, 500) to seen(NOTHING),
                    Press(0, 600) to seen(NOTHING),
                    Press(START, 700) to seen(NOTHING, hidden = START, deadline = 760),
                ),
            )

    val RESET_CASES =
        listOf(
            lone(
                "reset forgets a pending chord",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Reset to CLEAR,
                Due(5_000) to seen(SILENT),
            ),
            lone(
                "reset forgets a held back button, and the buttons held count as new ones",
                Press(START, 0) to seen(NOTHING, hidden = START, deadline = 60),
                Reset to CLEAR,
                Press(START, 10) to seen(NOTHING, hidden = START, deadline = 70),
            ),
            lone(
                "reset forgets the buttons held before it, so Start and Select can arm the chord",
                Press(A, 0) to seen(NOTHING),
                Press(A or START, 10) to seen(NOTHING),
                Reset to CLEAR,
                Press(CHORD, 100) to
                    seen(NOTHING, hidden = CHORD, deadline = 1_100, claimed = CHORD),
                Due(1_100) to seen(FIRED, hidden = CHORD, claimed = CHORD),
            ),
            lone(
                "reset after the chord fired lets it arm again at once",
                Press(CHORD, 0) to seen(NOTHING, hidden = CHORD, deadline = 1_000, claimed = CHORD),
                Press(CHORD, 1_000) to seen(FIRED, hidden = CHORD, claimed = CHORD),
                Reset to CLEAR,
                Press(CHORD, 1_100) to
                    seen(NOTHING, hidden = CHORD, deadline = 2_100, claimed = CHORD),
            ),
        )
}
