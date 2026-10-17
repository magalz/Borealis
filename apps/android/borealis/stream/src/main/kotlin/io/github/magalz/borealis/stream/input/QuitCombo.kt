package io.github.magalz.borealis.stream.input

import io.github.magalz.borealis.stream.input.GamepadButtons.LEFT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.RIGHT_BUMPER
import io.github.magalz.borealis.stream.input.GamepadButtons.SELECT
import io.github.magalz.borealis.stream.input.GamepadButtons.START

/**
 * Recognises the quit combo on a pad that reports its whole button state each time: Start, Select
 * and both bumpers held together, with no other button, and then everything let go. This is the
 * gesture the handler's own key-event path already knows for system pads. One instance follows one
 * pad. A fifth button pressed and let go while the four stay held still arms it, on purpose: it is
 * forgiving, and only a report with the exact four ever arms it.
 */
class QuitCombo {
    private var armed = false

    /**
     * The pad now holds [buttons]; true once, on the first report after the combo when nothing is
     * held. Once armed it stays armed through partial releases and re-presses until then.
     */
    @Synchronized
    fun update(buttons: Int): Boolean {
        if (buttons == COMBO) armed = true
        if (!armed || buttons != 0) return false
        armed = false
        return true
    }

    /** Whether the combo has been held and not yet let go. */
    @Synchronized fun pending(): Boolean = armed

    /** Forgets the combo, as when the pad is captured. */
    @Synchronized
    fun reset() {
        armed = false
    }

    private companion object {
        const val COMBO = START or SELECT or LEFT_BUMPER or RIGHT_BUMPER
    }
}
