package io.github.magalz.borealis.stream

import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
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
        event.keyCode in IME_KEYS && fromNavigationDevice(event) && imeVisible.asBoolean

    // A gamepad or a D-pad source that is not a mouse (the pointer class, or the trackball class of
    // a captured one) and not a keyboard with letters. Gamepads and remotes also report the
    // keyboard source bit, so it says nothing; the keyboard type of the device does.
    private fun fromNavigationDevice(event: KeyEvent): Boolean {
        val source = event.source
        return (source.hasSource(InputDevice.SOURCE_GAMEPAD) ||
            source.hasSource(InputDevice.SOURCE_DPAD)) &&
            source and (InputDevice.SOURCE_CLASS_POINTER or InputDevice.SOURCE_CLASS_TRACKBALL) ==
                0 &&
            InputDevice.getDevice(event.deviceId)?.keyboardType !=
                InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    // The source constants share the class bit, so a source has one when all its bits are set.
    private fun Int.hasSource(source: Int): Boolean = this and source == source

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

        /** Hides the Android keyboard of the window of [view]. */
        @JvmStatic
        fun hideIme(view: View) {
            view.context
                .getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }

        /** Whether the Android keyboard is showing in the window of [view]. */
        @JvmStatic
        fun isImeVisible(view: View): Boolean =
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
}
