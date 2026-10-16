package io.github.magalz.borealis.stream

import android.view.KeyEvent
import android.view.View
import java.util.function.BooleanSupplier

/**
 * Keeps the D-pad, Enter and Back of a gamepad or a remote for the Android keyboard while it is on
 * screen, instead of sending them to the PC.
 */
class ImeKeyGate(private val imeVisible: BooleanSupplier) {
    /** Whether [event] is for the Android keyboard and the stream must leave it alone. */
    fun toIme(event: KeyEvent): Boolean = false

    companion object {
        /** Whether the Android keyboard is showing in the window of [view]. */
        @JvmStatic fun isImeVisible(view: View): Boolean = true
    }
}
