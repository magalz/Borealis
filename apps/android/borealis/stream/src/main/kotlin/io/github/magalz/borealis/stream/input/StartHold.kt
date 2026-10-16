package io.github.magalz.borealis.stream.input

import io.github.magalz.borealis.stream.input.GamepadButtons.START

/**
 * Tells when Start is let go after a long hold: the gesture that turns the gamepad mouse on or off.
 * It follows the button reports of a pad that gives no key events, so the hold is timed from the
 * report that showed Start down. One instance follows one pad.
 */
class StartHold {
    private var downAt = NOT_DOWN

    /**
     * The pad now holds [buttons], at [time]; true when this report let go of a long-held Start.
     */
    fun update(buttons: Int, time: Long): Boolean {
        if (buttons and START != 0) {
            if (downAt == NOT_DOWN) downAt = time
            return false
        }
        val held = downAt != NOT_DOWN && time - downAt > HOLD_MS
        downAt = NOT_DOWN
        return held
    }

    companion object {
        /** Start must be held longer than this. */
        const val HOLD_MS = 750L

        private const val NOT_DOWN = -1L
    }
}
