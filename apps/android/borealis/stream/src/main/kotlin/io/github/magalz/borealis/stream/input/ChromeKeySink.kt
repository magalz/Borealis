package io.github.magalz.borealis.stream.input

import android.view.KeyEvent

/** Receives the key events the gamepad handler makes for pads the chrome cannot hear directly. */
fun interface ChromeKeySink {
    /** Returns whether the chrome used the key. */
    fun onKey(event: KeyEvent): Boolean
}

/** Turns the button changes of a USB-driver pad into the key events a menu navigates with. */
object UsbChromeKeys {
    /** The key events for buttons that changed between [previous] and [current], at [time]. */
    fun events(previous: Int, current: Int, time: Long): List<KeyEvent> = emptyList()
}
