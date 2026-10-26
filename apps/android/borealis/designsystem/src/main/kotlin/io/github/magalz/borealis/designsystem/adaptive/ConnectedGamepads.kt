package io.github.magalz.borealis.designsystem.adaptive

import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether an input device with these facts is a game controller: not virtual, and either a gamepad,
 * or a joystick with the X and Y axes ([hasJoystickXy]) that is no alphabetic keyboard
 * ([keyboardType] is not [InputDevice.KEYBOARD_TYPE_ALPHABETIC]). It is the rule for the
 * gamepad-buttons and joystick-axes part of `ControllerHandler.isGameControllerDevice`, which also
 * has its own exceptions: the handler takes the joystick part through [hasControllerJoystickAxes].
 * It is kept in one place for the form factor, the USB driver and the stream's handler. The source
 * masks are compared whole, because the class bits overlap with the keyboard's: an 8BitDo pad
 * reports `KEYBOARD | GAMEPAD | JOYSTICK` with a non-alphabetic keyboard, and a remote reports
 * `KEYBOARD | DPAD`.
 *
 * The keyboard type only matters for the joystick branch: some Bluetooth keyboards report a
 * JOYSTICK source with X and Y axes, and an alphabetic keyboard is no game controller for that. A
 * gamepad source is a pad whatever its keyboard type.
 */
fun isGameController(
    sources: Int,
    isVirtual: Boolean,
    hasJoystickXy: Boolean,
    keyboardType: Int,
): Boolean =
    !isVirtual &&
        (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            isJoystickController(sources, hasJoystickXy, keyboardType))

private fun isJoystickController(sources: Int, hasJoystickXy: Boolean, keyboardType: Int): Boolean =
    sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
        hasJoystickXy &&
        keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC

/**
 * Whether this device has the X and Y motion ranges of a joystick, for the JOYSTICK source or else
 * for the GAMEPAD source, the way the stream's controller handler looks them up.
 */
fun InputDevice.hasJoystickXy(): Boolean =
    listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y).all { axis ->
        getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null ||
            getMotionRange(axis, InputDevice.SOURCE_GAMEPAD) != null
    }

/**
 * Whether this device has the axes of a game controller: the JOYSTICK source with X and Y
 * ([hasJoystickXy]), on a device that is no alphabetic keyboard. It is the joystick branch of
 * [isGameController], for the stream's controller handler, which has no virtual-device or gamepad
 * rule here.
 */
fun InputDevice.hasControllerJoystickAxes(): Boolean =
    isJoystickController(sources, hasJoystickXy(), keyboardType)

/** Stub: the stream's rule moves here in the next commit. */
fun isStreamControllerDevice(device: InputDevice?): Boolean = device == null && false

private fun InputDevice.isGameController(): Boolean =
    isGameController(sources, isVirtual, hasJoystickXy(), keyboardType)

/**
 * Whether a game controller is connected to Android right now. A pad that the app's USB driver has
 * claimed is no Android input device while claimed, so it does not count; the driver claims one
 * only during a stream, after the first frame.
 */
fun isGamepadConnected(): Boolean =
    // A device removed while looping is null.
    InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.isGameController() == true }

/**
 * [isGamepadConnected] as state: right on the first composition, and updated while it is composed
 * whenever Android adds, removes or changes an input device.
 */
@Composable
internal fun rememberGamepadConnected(): Boolean {
    val context = LocalContext.current
    val connected = remember { mutableStateOf(isGamepadConnected()) }
    DisposableEffect(context) {
        val inputManager = context.getSystemService(InputManager::class.java)
        val listener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = refresh()

                override fun onInputDeviceRemoved(deviceId: Int) = refresh()

                override fun onInputDeviceChanged(deviceId: Int) = refresh()

                private fun refresh() {
                    connected.value = isGamepadConnected()
                }
            }
        // The null handler delivers on this thread's looper, the main thread.
        inputManager?.registerInputDeviceListener(listener, null)
        // A pad that came or went between the first composition and the registration.
        connected.value = isGamepadConnected()
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }
    return connected.value
}
