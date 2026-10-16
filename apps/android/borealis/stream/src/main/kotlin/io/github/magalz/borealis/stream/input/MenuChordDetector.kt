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
 */
class MenuChordDetector {
    /**
     * What one event did to the chord. [fired]: the chord is complete. [earlyRelease]: it ended
     * before one second because a button was released. [silent]: the event only released buttons
     * that were already hidden from the PC, so no packet needs to be sent for it.
     */
    data class Update(val fired: Boolean, val earlyRelease: Boolean, val silent: Boolean)

    private var held = 0
    private var armedAt = NOT_ARMED

    /** The chord buttons that belong to a pending chord or to one that fired and is still held. */
    private var consumed = 0
    private var masking = false

    /**
     * Buttons after the latest event. [maskWhilePending] hides the chord from the PC from the
     * start.
     *
     * The chord starts when the buttons are exactly Select and Start and no button of an earlier
     * chord is still held. Any other button ends it. Releasing a button before one second ends it
     * as an early release. An event at or after the deadline completes it first.
     */
    @Synchronized
    fun update(buttons: Int, time: Long, maskWhilePending: Boolean): Update {
        val previousHeld = held
        val previousHidden = hidden()
        held = buttons

        var fired = false
        var earlyRelease = false
        if (armedAt != NOT_ARMED) {
            if (time - armedAt >= HOLD_MS) {
                fire()
                fired = true
            } else if (buttons != CHORD) {
                armedAt = NOT_ARMED
                if (buttons and CHORD.inv() != 0) {
                    consumed = 0
                    masking = false
                } else {
                    earlyRelease = true
                }
            }
        }

        consumed = consumed and buttons
        if (consumed == 0) masking = false

        if (buttons == CHORD && armedAt == NOT_ARMED && consumed == 0) {
            armedAt = time
            consumed = CHORD
            masking = maskWhilePending
        }

        val changed = previousHeld xor buttons
        val silent = !fired && changed != 0 && changed and previousHidden == changed
        return Update(fired, earlyRelease, silent)
    }

    /** The scheduled check at [time]; true when it completed the chord. */
    @Synchronized
    fun check(time: Long): Boolean {
        if (armedAt == NOT_ARMED || time - armedAt < HOLD_MS) return false
        fire()
        return true
    }

    /** The time the chord completes, or [NO_DEADLINE] when none is pending. */
    @Synchronized
    fun deadline(): Long = if (armedAt == NOT_ARMED) NO_DEADLINE else armedAt + HOLD_MS

    /** The buttons that must not be sent to the PC. */
    @Synchronized fun hidden(): Int = if (masking) consumed else 0

    /** Whether [button] belongs to a chord that is pending or still held after it fired. */
    @Synchronized fun claims(button: Int): Boolean = consumed and button != 0

    /** Forgets the chord, as when the pad is captured. */
    @Synchronized
    fun reset() {
        held = 0
        armedAt = NOT_ARMED
        consumed = 0
        masking = false
    }

    private fun fire() {
        armedAt = NOT_ARMED
        consumed = CHORD
        masking = true
    }

    companion object {
        const val HOLD_MS = 1_000L
        const val NO_DEADLINE = -1L

        private const val NOT_ARMED = -1L
        private const val CHORD = GamepadButtons.START or GamepadButtons.SELECT
    }
}
