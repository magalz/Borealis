package io.github.magalz.borealis.input

import android.view.InputDevice
import android.view.KeyEvent
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject

/**
 * Gives an input device the keyboard layout a test sets for its id. Robolectric answers
 * `InputDevice.getKeyCodeForKeyLocation` with "unknown" for every key, so no keyboard has a layout
 * of its own. A layout maps the key code of a QWERTY key location to the key code that the device
 * sends from that location: on an AZERTY keyboard the location of QWERTY's `A` sends `Q`.
 *
 * Use it with `@Config(shadows = [ShadowKeyLayoutInputDevice::class])` and call [reset] before each
 * test.
 */
@Implements(InputDevice::class)
class ShadowKeyLayoutInputDevice {
    // Robolectric sets the field when it makes the shadow, so it cannot be a val.
    @Suppress("VarCouldBeVal") @RealObject private lateinit var device: InputDevice

    @Suppress("unused")
    @Implementation
    fun getKeyCodeForKeyLocation(locationKeyCode: Int): Int =
        layouts[device.id]?.get(locationKeyCode) ?: KeyEvent.KEYCODE_UNKNOWN

    companion object {
        /** Device id to the layout that its keys have. */
        val layouts = mutableMapOf<Int, Map<Int, Int>>()

        fun reset() = layouts.clear()
    }
}
