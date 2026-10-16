package io.github.magalz.borealis.stream.input

import android.view.KeyEvent

/**
 * Watches the Select and Start keys the chrome receives for the chord that closes its panel: both
 * held for one second.
 */
class ChromeChord(private val onChord: () -> Unit) {
    /** A key the chrome received. */
    @Suppress("UnusedParameter") fun onKey(event: KeyEvent) = Unit

    /** The time to call [check] at, or [MenuChordDetector.NO_DEADLINE]. */
    fun deadline(): Long = MenuChordDetector.NO_DEADLINE

    /** The scheduled check at [time]. */
    @Suppress("UnusedParameter") fun check(time: Long) = Unit

    /** Forgets every key held. */
    fun reset() = Unit
}
