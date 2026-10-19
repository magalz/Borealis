package io.github.magalz.borealis.designsystem.testing

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.util.ReflectionHelpers

/**
 * An input device as the framework reports it. A negative [id] makes it virtual. [joystickXy] gives
 * it the X and Y motion ranges of a joystick. [keyboardType] is what
 * `InputDevice.getKeyboardType()` returns, one of the `InputDevice.KEYBOARD_TYPE_*` constants.
 */
fun inputDevice(
    id: Int,
    sources: Int,
    joystickXy: Boolean = false,
    keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE,
): InputDevice {
    val builder =
        InputDeviceBuilder.newBuilder()
            .setId(id)
            .setName("test-device-$id")
            .setSources(sources)
            .setKeyboardType(keyboardType)
    if (joystickXy) {
        for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
            builder.addMotionRange(axis, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
        }
    }
    return builder.build()
}

/** Makes [device] one of the devices Android has. It fires no listener. */
fun connect(context: Context, device: InputDevice) {
    shadowOf(context.getSystemService(InputManager::class.java)).addInputDevice(device)
}

fun disconnect(context: Context, deviceId: Int) {
    shadowOf(context.getSystemService(InputManager::class.java)).removeInputDevice(deviceId)
}

/**
 * The listeners registered with the framework's input manager. Robolectric's shadow adds and
 * removes devices without telling them, so a test reads them from the framework and calls them.
 */
// Robolectric only, never a device: the test fixtures are not packaged into an APK.
@SuppressLint("PrivateApi")
fun inputDeviceListeners(): List<InputManager.InputDeviceListener> {
    val global =
        ReflectionHelpers.callStaticMethod<Any>(
            Class.forName("android.hardware.input.InputManagerGlobal"),
            "getInstance",
        )
    val delegates = ReflectionHelpers.getField<List<Any>>(global, "mInputDeviceListeners")
    return delegates.map { ReflectionHelpers.getField(it, "mListener") }
}
