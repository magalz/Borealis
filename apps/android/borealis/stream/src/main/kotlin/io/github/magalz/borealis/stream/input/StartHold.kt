package io.github.magalz.borealis.stream.input

/**
 * Tells when Start is let go after a long hold: the gesture that turns the gamepad mouse on or off.
 */
class StartHold {
    /**
     * The pad now holds [buttons], at [time]; true when this report let go of a long-held Start.
     */
    @Suppress("UnusedParameter") fun update(buttons: Int, time: Long): Boolean = false

    companion object {
        /** Start must be held longer than this. */
        const val HOLD_MS = 750L
    }
}
