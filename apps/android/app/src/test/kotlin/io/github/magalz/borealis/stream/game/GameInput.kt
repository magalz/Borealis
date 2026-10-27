package io.github.magalz.borealis.stream.game

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.shadows.ShadowInputEvent
import org.robolectric.util.ReflectionHelpers

private const val KEYBOARD_DEVICE_ID = 11

/**
 * A key event from a hardware keyboard: a device with an alphabetic layout, which the activity
 * sends to the host as a key (a device that is not one, or no device at all, is a game controller
 * to the activity and its keys go to the controller handler).
 */
@Suppress("DEPRECATION") // Robolectric offers no other way to make an input device.
internal fun keyboardEvent(action: Int, keyCode: Int, metaState: Int = 0): KeyEvent {
    val device = ShadowInputDevice.makeInputDeviceNamed("keyboard")
    ReflectionHelpers.setField(device, "mId", KEYBOARD_DEVICE_ID)
    ReflectionHelpers.setField(device, "mSources", InputDevice.SOURCE_KEYBOARD)
    ReflectionHelpers.setField(device, "mKeyboardType", InputDevice.KEYBOARD_TYPE_ALPHABETIC)
    val now = SystemClock.uptimeMillis()
    val event =
        KeyEvent(
            now,
            now,
            action,
            keyCode,
            0,
            metaState,
            KEYBOARD_DEVICE_ID,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD,
        )
    Shadow.extract<ShadowInputEvent>(event).setDevice(device)
    return event
}

/**
 * A press of [button] (a `MotionEvent.BUTTON_*`) by a mouse that points at ([x], [y]). The
 * `motionEvent` of `input/CaptureRig.kt` builds one pointer too, but only a move, with no button
 * state: a press needs its own action and button state, so this builds the event itself.
 */
internal fun mousePress(button: Int, x: Float, y: Float): MotionEvent {
    val now = SystemClock.uptimeMillis()
    val properties =
        arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
    val coordinates =
        arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
            }
        )
    return MotionEvent.obtain(
        now,
        now,
        MotionEvent.ACTION_DOWN,
        1,
        properties,
        coordinates,
        0,
        button,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_MOUSE,
        0,
    )
}
