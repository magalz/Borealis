package io.github.magalz.borealis.stream.input

import android.os.Handler

/**
 * What a pad on the USB driver does to the chrome while the chrome has the input: its buttons and
 * its left stick become the keys the chrome navigates with, sent to the sink on the main thread.
 * One instance follows one pad.
 */
class UsbChromeInput(private val handler: Handler, private val sink: () -> ChromeKeySink?) {
    /** The pad reported [buttons] and the left stick at [stickX], [stickY] (down is positive). */
    @Suppress("UnusedParameter") fun report(buttons: Int, stickX: Float, stickY: Float) = Unit

    companion object {
        /** How long a direction is held before it repeats. */
        const val REPEAT_DELAY_MS = 400L

        /** The time between repeats once they start. */
        const val REPEAT_INTERVAL_MS = 100L
    }
}
