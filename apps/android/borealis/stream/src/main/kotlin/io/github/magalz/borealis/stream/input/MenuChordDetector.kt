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

    /**
     * Buttons after the latest event. [maskWhilePending] hides the chord from the PC from the
     * start.
     */
    fun update(buttons: Int, time: Long, maskWhilePending: Boolean): Update =
        Update(fired = false, earlyRelease = false, silent = false)

    /** The scheduled check at [time]; true when it completed the chord. */
    fun check(time: Long): Boolean = false

    /** The time the chord completes, or [NO_DEADLINE] when none is pending. */
    fun deadline(): Long = NO_DEADLINE

    /** The buttons that must not be sent to the PC. */
    fun hidden(): Int = 0

    /** Whether [button] belongs to a chord that is pending or still held after it fired. */
    fun claims(button: Int): Boolean = false

    /** Forgets the chord, as when the pad is captured. */
    fun reset() {}

    companion object {
        const val HOLD_MS = 1_000L
        const val NO_DEADLINE = -1L
    }
}
