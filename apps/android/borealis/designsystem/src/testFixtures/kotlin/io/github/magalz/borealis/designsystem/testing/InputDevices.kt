package io.github.magalz.borealis.designsystem.testing

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * An input device as the framework reports it. A negative [id] makes it virtual. [joystickXy] gives
 * it the X and Y motion ranges of a joystick.
 */
@Suppress("DEPRECATION") // the Robolectric helper; InputDevice.Builder is not in the public SDK
fun inputDevice(id: Int, sources: Int, joystickXy: Boolean = false): InputDevice {
    val device = ShadowInputDevice.makeInputDeviceNamed("test-device-$id")
    ReflectionHelpers.setField(device, "mId", id)
    ReflectionHelpers.setField(device, "mSources", sources)
    if (joystickXy) {
        for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
            ReflectionHelpers.callInstanceMethod<Unit>(
                device,
                "addMotionRange",
                ClassParameter.from(Int::class.javaPrimitiveType, axis),
                ClassParameter.from(Int::class.javaPrimitiveType, InputDevice.SOURCE_JOYSTICK),
                ClassParameter.from(Float::class.javaPrimitiveType, -1f),
                ClassParameter.from(Float::class.javaPrimitiveType, 1f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
            )
        }
    }
    return device
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
fun inputDeviceListeners(): List<InputManager.InputDeviceListener> {
    val global =
        ReflectionHelpers.callStaticMethod<Any>(
            Class.forName("android.hardware.input.InputManagerGlobal"),
            "getInstance",
        )
    val delegates = ReflectionHelpers.getField<List<Any>>(global, "mInputDeviceListeners")
    return delegates.map { ReflectionHelpers.getField(it, "mListener") }
}
