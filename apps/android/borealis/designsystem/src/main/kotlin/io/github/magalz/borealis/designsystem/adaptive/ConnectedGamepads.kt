package io.github.magalz.borealis.designsystem.adaptive

import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether an input device with these facts is a real game controller: not virtual, and either a
 * gamepad, or a joystick with the X and Y axes ([hasJoystickXy]) that is no alphabetic keyboard
 * ([keyboardType] is not [InputDevice.KEYBOARD_TYPE_ALPHABETIC]). It answers "is a pad connected?",
 * for the form factor and for the USB driver (`NativeGamepadSupport`). The stream asks something
 * broader, which devices' key events `Game` offers to the controller handler first: that is
 * [isStreamControllerDevice]. The source masks are compared whole, because the class bits overlap
 * with the keyboard's: an 8BitDo pad reports `KEYBOARD | GAMEPAD | JOYSTICK` with a non-alphabetic
 * keyboard, and a remote reports `KEYBOARD | DPAD`.
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
 * ([hasJoystickXy]), on a device that is no alphabetic keyboard. It is the joystick branch of both
 * [isGameController] and [isStreamControllerDevice], and the check of the stream's controller
 * handler where only axes count (the number of attached controllers, the remote that has none).
 */
fun InputDevice.hasControllerJoystickAxes(): Boolean =
    isJoystickController(sources, hasJoystickXy(), keyboardType)

/**
 * Whether `Game` offers this device's key events to the controller handler before the keyboard
 * handler. It is the rule of `ControllerHandler.isGameControllerDevice`, which `Game.onKeyDown` and
 * `onKeyUp` ask for each key event. The verdict only decides the order of the two handlers: the
 * controller handler can still decline the event (after a remapping, for example), and then the
 * keyboard handler gets it. Motion events do not ask it: `Game` gives those to the controller
 * handler by the source class of the event.
 *
 * It is broader than [isGameController], which judges whether one device is a real pad for the form
 * factor and the USB driver, and every difference is on purpose, so that a TV remote's keys still
 * reach the controller handler first:
 * - no device at all counts: an event with no device goes to the controller handler first;
 * - a virtual device counts when it has gamepad buttons or joystick axes: the rule does not look at
 *   whether a device is virtual;
 * - on Android 11 only, the virtual device -1 counts while any device has gamepad buttons or
 *   joystick axes, the workaround for https://issuetracker.google.com/issues/163120692, where the
 *   system reports the events of every attached device as coming from it;
 * - anything that is no alphabetic keyboard counts, whatever its sources: a remote that reports
 *   `KEYBOARD | DPAD` with a non-alphabetic keyboard, and a mouse or a touchscreen, which have no
 *   keyboard at all.
 *
 * An alphabetic keyboard counts only for gamepad buttons, never for joystick axes, apart from the
 * Android 11 case above: there the virtual device -1 counts whatever its keyboard type, while a
 * device has gamepad buttons or joystick axes.
 */
fun isStreamControllerDevice(device: InputDevice?): Boolean =
    device == null ||
        device.hasPadInputs() ||
        isVirtualDeviceOfConnectedPad(device) ||
        device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC

/** The inputs of a pad that no virtual-device or keyboard-type rule changes: axes or buttons. */
private fun InputDevice.hasPadInputs(): Boolean =
    hasControllerJoystickAxes() ||
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

private const val VIRTUAL_DEVICE_ID = -1

// Android 11 only: https://issuetracker.google.com/issues/163120692
private fun isVirtualDeviceOfConnectedPad(device: InputDevice): Boolean =
    Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
        device.id == VIRTUAL_DEVICE_ID &&
        // A device removed while looping is null.
        InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.hasPadInputs() == true }

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
