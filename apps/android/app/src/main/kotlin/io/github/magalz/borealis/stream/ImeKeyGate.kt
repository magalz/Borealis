package io.github.magalz.borealis.stream

import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.function.BooleanSupplier

/**
 * Keeps the D-pad, Enter and Back of a gamepad or a remote for the Android keyboard while it is on
 * screen, instead of sending them to the PC. A hardware keyboard keeps its keys, and so does the
 * synthetic Back of a mouse right click, which the stream turns into a mouse button.
 */
class ImeKeyGate(private val imeVisible: BooleanSupplier) {
    /** Whether [event] is for the Android keyboard and the stream must leave it alone. */
    fun toIme(event: KeyEvent): Boolean =
        event.keyCode in IME_KEYS && fromNavigationDevice(event.source) && imeVisible.asBoolean

    // Not a keyboard, and not a mouse: the pointer class, or the trackball class of a captured one.
    private fun fromNavigationDevice(source: Int): Boolean =
        source and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD &&
            source and (InputDevice.SOURCE_CLASS_POINTER or InputDevice.SOURCE_CLASS_TRACKBALL) == 0

    companion object {
        private val IME_KEYS =
            setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BACK,
            )

        /** Whether the Android keyboard is showing in the window of [view]. */
        @JvmStatic
        fun isImeVisible(view: View): Boolean =
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
}
