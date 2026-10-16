package io.github.magalz.borealis.stream.input

import android.view.KeyEvent
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START

/**
 * Watches the Select and Start keys the chrome receives for the chord that closes its panel: both
 * held for one second, on the same pad. A key that went down before the chrome took the input only
 * repeats here, and does not start the chord. Call it from the main thread.
 */
class ChromeChord(private val onChord: () -> Unit) {
    private class Pad {
        val detector = MenuChordDetector()
        var held = 0
    }

    private val pads = HashMap<Int, Pad>()

    /** A key the chrome received. */
    fun onKey(event: KeyEvent) {
        val button =
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_START -> START
                KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
                else -> return
            }
        val pad = pads.getOrPut(event.deviceId) { Pad() }
        when {
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 ->
                pad.held = pad.held or button
            event.action == KeyEvent.ACTION_UP -> pad.held = pad.held and button.inv()
            else -> return
        }
        if (pad.detector.update(pad.held, event.eventTime, maskWhilePending = false).fired) {
            onChord()
        }
    }

    /** The time to call [check] at, or [MenuChordDetector.NO_DEADLINE] when no chord is pending. */
    fun deadline(): Long =
        pads.values
            .map { it.detector.deadline() }
            .filter { it != MenuChordDetector.NO_DEADLINE }
            .minOrNull() ?: MenuChordDetector.NO_DEADLINE

    /** The scheduled check at [time]: tells the chord when a pad has held it long enough. */
    fun check(time: Long) {
        for (pad in pads.values.toList()) {
            if (pad.detector.check(time)) onChord()
        }
    }

    /** Forgets every key held, as when the chrome gives the input back. */
    fun reset() {
        pads.values.forEach { it.detector.reset() }
        pads.clear()
    }
}
