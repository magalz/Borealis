package io.github.magalz.borealis.stream.input

/**
 * Tells which button presses began while the chrome had the input (or before it), so that they do
 * nothing once the input is back, until the button has been let go. Without it, closing a panel
 * with the Select and Start chord would toggle the gamepad mouse and start the chord again while
 * the buttons are still held.
 */
class CaptureEnd {
    /** The chrome gave the input back at [time] (uptime). */
    @Suppress("UnusedParameter") fun ended(time: Long) = Unit

    /** Whether a key that went down at [downTime] was already down when the input came back. */
    @Suppress("UnusedParameter") fun began(downTime: Long): Boolean = false
}

/** The same for a pad that reports its whole button state each time instead of key events. */
class HeldSinceCapture {
    /** The buttons of a report that count: none while [captured], and none still held from it. */
    @Suppress("UnusedParameter") fun filter(buttons: Int, captured: Boolean): Int = buttons
}
