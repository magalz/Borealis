package io.github.magalz.borealis.stream.input

/**
 * The button bits of a controller packet, as `com.limelight.nvstream.input.ControllerPacket`
 * defines them. The app module checks that the two agree.
 */
object GamepadButtons {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val SELECT = 0x0020
    const val LEFT_BUMPER = 0x0100
    const val RIGHT_BUMPER = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val Y = 0x8000
}

/**
 * Recognises Select and Start held together for one second, the gamepad chord that opens the menu.
 * One detector follows one pad. It is fed the buttons the pad holds after each event, with the time
 * of that event, and again by a scheduled check at [deadline] so that it also fires while the
 * buttons are held still. It keeps no clock of its own.
 *
 * On a pad that reports Start and Select as separate events, Start or Select pressed alone is held
 * back from the PC for [LONE_WAIT_MS] (see [update]), so that the first button of the chord never
 * reaches it.
 */
class MenuChordDetector {
    /**
     * What one event did to the chord. [fired]: the chord is complete. [earlyRelease]: it ended
     * before one second because a button was released. [silent]: the event only released buttons
     * that were already hidden from the PC, so no packet needs to be sent for it. [loneTap]: the
     * button that was let go while it was still held back, which the PC gets as a short press; 0
     * when there is none.
     */
    data class Update(
        val fired: Boolean,
        val earlyRelease: Boolean,
        val silent: Boolean,
        val loneTap: Int = 0,
    )

    private val chord = Chord()
    private var held = 0

    /** Start or Select pressed alone and held back while its partner may still arrive. */
    private var lone = 0
    private var loneAt = NOT_ARMED

    /**
     * Buttons after the latest event. [maskWhilePending] hides the chord from the PC from the
     * start.
     *
     * The chord starts when the buttons are exactly Select and Start, no button of an earlier chord
     * is still held, and no other button has been held since every button was last up. Any other
     * button ends it, and keeps a new one from starting until every button has been let go, so that
     * what is left of the quit combo does not count. Releasing a button before one second ends it
     * as an early release. An event at or after the deadline completes it first.
     *
     * With [holdLone], Start or Select that goes down with no other button held is hidden from the
     * PC until [LONE_WAIT_MS] have passed (see [check]). The partner arriving in that time starts
     * the chord, and nothing has leaked. Another button arriving ends the wait and the PC gets what
     * is held. Letting the button go in that time is reported as [Update.loneTap].
     */
    @JvmOverloads
    @Synchronized
    fun update(
        buttons: Int,
        time: Long,
        maskWhilePending: Boolean,
        holdLone: Boolean = false,
    ): Update {
        // Two things keep their place. What is hidden is read first, before any step changes it:
        // an event is silent when it only lets go of buttons that were hidden before it. And the
        // armed chord is settled before keepHeld narrows what it claims: firing claims both
        // buttons, so one that is let go in the event that fires is dropped by keepHeld.
        val previousHeld = held
        val previousHidden = hidden()
        held = buttons

        val loneTap = endLoneWait(buttons)
        val settled = chord.settle(buttons, time)
        chord.keepHeld(buttons)
        chord.armFromCleanStart(buttons, time, maskWhilePending)
        if (holdLone && startsAlone(previousHeld, buttons)) {
            lone = buttons
            loneAt = time
        }

        val fired = settled == Settled.FIRED
        val changed = previousHeld xor buttons
        val silent = !fired && changed != 0 && changed and previousHidden == changed
        return Update(fired, settled == Settled.EARLY_RELEASE, silent, loneTap)
    }

    /** The wait of a lone button ends when the buttons change; returns the button let go in it. */
    private fun endLoneWait(buttons: Int): Int {
        if (lone == 0 || buttons == lone) return 0
        val letGo = if (buttons and lone == 0) lone else 0
        lone = 0
        loneAt = NOT_ARMED
        return letGo
    }

    /** Whether Start or Select alone has just gone down, with no wait pending. */
    private fun startsAlone(previousHeld: Int, buttons: Int): Boolean =
        lone == 0 && previousHeld == 0 && (buttons == START || buttons == SELECT)

    /** The scheduled check at [time] of a chord alone; true when it completed the chord. */
    fun check(time: Long): Boolean = due(time).fired

    /**
     * The scheduled check at [time]: what is due at [deadline]. It completes the chord, or ends the
     * wait of a lone button, which the PC gets from then on. A check with nothing due is silent.
     */
    @Synchronized
    fun due(time: Long): Update {
        if (lone != 0 && time - loneAt >= LONE_WAIT_MS) {
            lone = 0
            loneAt = NOT_ARMED
            return Update(fired = false, earlyRelease = false, silent = false)
        }
        if (!chord.fireIfDue(time)) {
            return Update(fired = false, earlyRelease = false, silent = true)
        }
        return Update(fired = true, earlyRelease = false, silent = false)
    }

    /** The time the next check is due, or [NO_DEADLINE] when none is pending. */
    @Synchronized fun deadline(): Long = if (lone != 0) loneAt + LONE_WAIT_MS else chord.deadline()

    /** The buttons that must not be sent to the PC. */
    @Synchronized fun hidden(): Int = chord.hidden() or lone

    /** Whether [button] belongs to a chord that is pending or still held after it fired. */
    @Synchronized fun claims(button: Int): Boolean = chord.claims(button)

    /** Forgets the chord, as when the pad is captured. */
    @Synchronized
    fun reset() {
        held = 0
        chord.reset()
        lone = 0
        loneAt = NOT_ARMED
    }

    /** What an armed chord did with one event. */
    private enum class Settled {
        NOTHING,
        FIRED,
        EARLY_RELEASE,
    }

    /**
     * The chord itself, from Start and Select going down together until both are up. It is read and
     * changed only under the lock of the detector that owns it.
     */
    private class Chord {
        private var armedAt = NOT_ARMED

        /**
         * The chord buttons that belong to a pending chord or to one that fired and is still held.
         */
        private var consumed = 0
        private var masking = false

        /**
         * A button other than Start and Select has been held since every button was last up, so the
         * chord cannot arm: what is left of a quit combo or of any other press is not a chord.
         */
        private var foreign = false

        /** Completes the chord when its deadline has come at [time]; true when it did. */
        fun fireIfDue(time: Long): Boolean {
            if (armedAt == NOT_ARMED || time - armedAt < HOLD_MS) return false
            armedAt = NOT_ARMED
            consumed = CHORD
            masking = true
            return true
        }

        /**
         * What an armed chord does with an event: an event at or after the deadline completes it,
         * whatever buttons it holds, and before then any buttons but Start and Select alone end it.
         */
        fun settle(buttons: Int, time: Long): Settled =
            when {
                fireIfDue(time) -> Settled.FIRED
                armedAt == NOT_ARMED || buttons == CHORD -> Settled.NOTHING
                else -> cancel(buttons)
            }

        /**
         * Another button ends the chord for good and releases its hold on the PC's buttons. Letting
         * one of Start and Select go ends it as an early release, and what is still held stays
         * claimed.
         */
        private fun cancel(buttons: Int): Settled {
            armedAt = NOT_ARMED
            if (!holdsOther(buttons)) return Settled.EARLY_RELEASE
            consumed = 0
            masking = false
            return Settled.NOTHING
        }

        /** Only the chord buttons that are still held stay claimed. */
        fun keepHeld(buttons: Int) {
            consumed = consumed and buttons
            if (consumed == 0) masking = false
        }

        /**
         * Starts the chord when [buttons] are Start and Select alone, with nothing left over from
         * before. Every event is counted in [foreign] first, whether or not a chord can start.
         */
        fun armFromCleanStart(buttons: Int, time: Long, mask: Boolean) {
            foreign = buttons != 0 && (foreign || holdsOther(buttons))
            if (canArm(buttons)) {
                armedAt = time
                consumed = CHORD
                masking = mask
            }
        }

        private fun canArm(buttons: Int): Boolean =
            buttons == CHORD && !foreign && armedAt == NOT_ARMED && consumed == 0

        fun deadline(): Long = if (armedAt == NOT_ARMED) NO_DEADLINE else armedAt + HOLD_MS

        fun hidden(): Int = if (masking) consumed else 0

        fun claims(button: Int): Boolean = consumed and button != 0

        fun reset() {
            armedAt = NOT_ARMED
            consumed = 0
            masking = false
            foreign = false
        }
    }

    companion object {
        const val HOLD_MS = 1_000L

        /** How long a lone Start or Select waits for its partner before the PC gets it. */
        const val LONE_WAIT_MS = 60L
        const val NO_DEADLINE = -1L

        private const val NOT_ARMED = -1L
        private const val START = GamepadButtons.START
        private const val SELECT = GamepadButtons.SELECT
        private const val CHORD = START or SELECT

        /** Whether a button other than Start and Select is among [buttons]. */
        private fun holdsOther(buttons: Int): Boolean = buttons and CHORD.inv() != 0
    }
}
