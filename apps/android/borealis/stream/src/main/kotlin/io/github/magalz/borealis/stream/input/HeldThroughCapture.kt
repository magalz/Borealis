package io.github.magalz.borealis.stream.input

/**
 * For a pad that sends key events: tells which key presses began before the chrome gave the input
 * back (while it had the input, or before it). Such a press does nothing until the key has been let
 * go, and its repeats keep the original down time, so they are told apart from a fresh press.
 * Without it, closing a panel with the Select and Start chord would toggle the gamepad mouse and
 * start the chord again while the buttons are still held.
 */
class CaptureEnd {
    @Volatile private var endedAt = 0L

    /** The chrome gave the input back at [time] (uptime). */
    fun ended(time: Long) {
        endedAt = time
    }

    /** Whether a key that went down at [downTime] was already down when the input came back. */
    fun began(downTime: Long): Boolean = downTime < endedAt
}

/** The same for a pad that reports its whole button state each time instead of key events. */
class HeldSinceCapture {
    private var held = 0

    /**
     * The buttons of a report that count: none while [captured], and none that have been down since
     * then and not let go.
     */
    @Synchronized
    fun filter(buttons: Int, captured: Boolean): Int {
        if (captured) {
            held = buttons
            return 0
        }
        held = held and buttons
        return buttons and held.inv()
    }
}
