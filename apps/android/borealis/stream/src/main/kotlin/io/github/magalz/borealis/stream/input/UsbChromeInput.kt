package io.github.magalz.borealis.stream.input

import android.os.Handler
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_DOWN
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_LEFT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_RIGHT
import io.github.magalz.borealis.stream.input.GamepadButtons.DPAD_UP
import kotlin.math.abs

/**
 * What a pad on the USB driver does to the chrome while the chrome has the input: its buttons and
 * its left stick become the keys the chrome navigates with, sent to the sink on the main thread.
 * The sink is null while the chrome does not have the input. A direction held on the D-pad or the
 * stick repeats, as a held D-pad key does on a system pad. One instance follows one pad; [handler]
 * is the main thread's.
 */
class UsbChromeInput(private val handler: Handler, private val sink: () -> ChromeKeySink?) {
    private var pressed = 0
    private var unseen = 0
    private var stickDirection = 0
    private var repeatKey = 0
    private var repeatCount = 0
    private var repeatDownTime = 0L
    private val repeater = Runnable { repeat() }

    /** The pad reported [buttons] and the left stick at [stickX], [stickY] (down is positive). */
    @Synchronized
    fun report(buttons: Int, stickX: Float, stickY: Float) {
        stickDirection = directionOf(stickX, stickY, stickDirection)
        val held = buttons or stickDirection
        val target = sink()
        if (target == null) {
            // What is held now was down before the chrome had the input: it stays unseen until let
            // go.
            unseen = held
            pressed = 0
            stopRepeat()
            return
        }
        unseen = unseen and held
        val current = held and unseen.inv()
        // Keys let go come before keys pressed, so a stick that swings across never holds two.
        val keys =
            UsbChromeKeys.events(pressed, current, SystemClock.uptimeMillis()).sortedBy {
                it.action == KeyEvent.ACTION_DOWN
            }
        pressed = current
        for (key in keys) {
            handler.post { target.onKey(key) }
            followRepeat(key)
        }
    }

    private fun followRepeat(key: KeyEvent) {
        if (key.keyCode !in DIRECTION_KEYS) return
        if (key.action == KeyEvent.ACTION_DOWN) {
            repeatKey = key.keyCode
            repeatCount = 0
            repeatDownTime = key.eventTime
            handler.removeCallbacks(repeater)
            handler.postDelayed(repeater, REPEAT_DELAY_MS)
        } else if (key.keyCode == repeatKey) {
            stopRepeat()
        }
    }

    private fun stopRepeat() {
        repeatKey = 0
        handler.removeCallbacks(repeater)
    }

    @Synchronized
    private fun repeat() {
        val target = sink()
        if (target == null || repeatKey == 0) {
            stopRepeat()
            return
        }
        repeatCount++
        target.onKey(
            KeyEvent(
                repeatDownTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                repeatKey,
                repeatCount,
                0,
                0,
                0,
                0,
                InputDevice.SOURCE_GAMEPAD,
            )
        )
        handler.postDelayed(repeater, REPEAT_INTERVAL_MS)
    }

    companion object {
        /** How long a direction is held before it repeats. */
        const val REPEAT_DELAY_MS = 400L

        /** The time between repeats once they start. */
        const val REPEAT_INTERVAL_MS = 100L

        // The stick passes the first to press a direction and falls under the second to let it go,
        // so a stick that rests near the edge does not flicker.
        private const val PRESS_THRESHOLD = 0.6f
        private const val RELEASE_THRESHOLD = 0.4f

        private val DIRECTION_KEYS =
            setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            )

        /** The D-pad button the stick stands for: the axis it is pushed further along. */
        private fun directionOf(x: Float, y: Float, current: Int): Int {
            val horizontal = abs(x) >= abs(y)
            val push = if (horizontal) x else y
            val threshold = if (current != 0) RELEASE_THRESHOLD else PRESS_THRESHOLD
            return when {
                abs(push) < threshold -> 0
                horizontal -> if (push > 0) DPAD_RIGHT else DPAD_LEFT
                else -> if (push > 0) DPAD_DOWN else DPAD_UP
            }
        }
    }
}
