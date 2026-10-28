package io.github.magalz.borealis.testing

import android.hardware.input.InputManager
import android.util.SparseArray
import android.view.InputDevice
import com.limelight.binding.input.ControllerHandler
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Gives the handler the context of the pad with [deviceId], as it makes one after the pad sent its
 * first event. The handler makes it from the real input device, which reads the vibrators, lights
 * and sensors that Robolectric does not have; a unit test makes it here instead, and sets on it by
 * name the [fields] it needs (a controller number, so that no arrival is reported, for one).
 */
fun ControllerHandler.addInputDeviceContext(deviceId: Int, fields: Map<String, Any?>): Any {
    val contextClass =
        Class.forName("com.limelight.binding.input.ControllerHandler\$InputDeviceContext")
    val context =
        ReflectionHelpers.callConstructor(
            contextClass,
            ClassParameter.from(ControllerHandler::class.java, this),
        )
    fields.forEach { (name, value) -> ReflectionHelpers.setField(context, name, value) }
    ReflectionHelpers.getField<SparseArray<Any?>>(this, "inputDeviceContexts")
        .put(deviceId, context)
    return context
}

/** An input device named [name] with the id [deviceId], which the input manager lists. */
@Suppress("DEPRECATION") // Robolectric offers no other way to make an input device.
fun addSystemInputDevice(name: String, deviceId: Int): InputDevice {
    val device = ShadowInputDevice.makeInputDeviceNamed(name)
    ReflectionHelpers.setField(device, "mId", deviceId)
    val inputManager =
        RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)
    shadowOf(inputManager).addInputDevice(device)
    return device
}
