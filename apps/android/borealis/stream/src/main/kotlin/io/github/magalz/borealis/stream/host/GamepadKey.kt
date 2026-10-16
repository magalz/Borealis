package io.github.magalz.borealis.stream.host

import android.view.KeyEvent

/**
 * What the modal chrome does with a gamepad key. The chrome handles these itself, for a pad Android
 * knows and for the keys the gamepad handler makes up for a USB-driver pad, so no key is left to
 * the system fallbacks (Start would click the focused item as OK, Select would be Menu).
 */
internal enum class GamepadKey {
    /** A clicks the focused item, as OK does. */
    OK,

    /** B acts as Back, unless a screen took it first. */
    BACK,

    /** Y is the screen's to use, and does nothing when it does not. */
    SCREEN_ONLY,

    /** Start, Select and the rest never click, open a menu or reach the PC. */
    UNUSED;

    companion object {
        /**
         * The role of [keyCode], or null when it is not a key the chrome takes as a gamepad key.
         */
        fun of(keyCode: Int): GamepadKey? =
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> OK
                KeyEvent.KEYCODE_BUTTON_B -> BACK
                KeyEvent.KEYCODE_BUTTON_Y -> SCREEN_ONLY
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_BUTTON_MODE -> UNUSED
                else -> null
            }

        /** Whether [keyCode] confirms the focused item: OK, Enter, or the gamepad A. */
        fun isConfirm(keyCode: Int): Boolean =
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> true
                else -> false
            }

        /** [event] as the key OK, which is what a click listens for. */
        fun asOk(event: KeyEvent): KeyEvent =
            KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                KeyEvent.KEYCODE_DPAD_CENTER,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source,
            )
    }
}
