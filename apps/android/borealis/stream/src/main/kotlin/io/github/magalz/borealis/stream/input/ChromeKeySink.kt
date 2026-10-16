package io.github.magalz.borealis.stream.input

import android.view.InputDevice
import android.view.KeyEvent
import io.github.magalz.borealis.stream.input.GamepadButtons.A
import io.github.magalz.borealis.stream.input.GamepadButtons.B
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_DOWN
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_LEFT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_RIGHT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_UP
import io.github.magalz.borealis.stream.input.GamepadButtons.START
import io.github.magalz.borealis.stream.input.GamepadButtons.Y

/** Receives the key events the gamepad handler makes for pads the chrome cannot hear directly. */
fun interface ChromeKeySink {
    /** Returns whether the chrome used the key. */
    fun onKey(event: KeyEvent): Boolean
}

/** Turns the button changes of a USB-driver pad into the key events a menu navigates with. */
object UsbChromeKeys {
    private val keyOfButton =
        listOf(
            A to KeyEvent.KEYCODE_BUTTON_A,
            B to KeyEvent.KEYCODE_BUTTON_B,
            Y to KeyEvent.KEYCODE_BUTTON_Y,
            START to KeyEvent.KEYCODE_BUTTON_START,
            DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
            DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        )

    /** The key events for buttons that changed between [previous] and [current], at [time]. */
    fun events(previous: Int, current: Int, time: Long): List<KeyEvent> =
        keyOfButton.mapNotNull { (button, keyCode) ->
            when {
                current and button != 0 && previous and button == 0 ->
                    keyEvent(time, KeyEvent.ACTION_DOWN, keyCode)
                current and button == 0 && previous and button != 0 ->
                    keyEvent(time, KeyEvent.ACTION_UP, keyCode)
                else -> null
            }
        }

    private fun keyEvent(time: Long, action: Int, keyCode: Int) =
        KeyEvent(time, time, action, keyCode, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)
}
