package io.github.magalz.borealis.input

import android.view.InputDevice
import io.github.magalz.borealis.designsystem.adaptive.hasJoystickXy
import io.github.magalz.borealis.designsystem.adaptive.isGameController

/**
 * What the USB driver needs to know about one Android input device. [hasJoystickXy] is true when
 * the device has the X and Y motion ranges of a joystick (for the JOYSTICK source, or else for the
 * GAMEPAD source), the way `ControllerHandler` looks them up. [isVirtual] marks the system's
 * virtual devices, which are no pad.
 */
data class InputDeviceFacts(
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val hasJoystickXy: Boolean = false,
    val isVirtual: Boolean = false,
)

/**
 * Decides whether Android already handles a USB pad, so that the USB driver leaves it alone.
 *
 * Android must offer a gamepad (or a joystick with X and Y axes) for the pad. A dongle can show up
 * only as a keyboard, a mouse or a joystick device without those axes under the same VID/PID (the
 * 8BitDo Ultimate 2 for PC dongle, 2DC8:310B): that is no native pad support, so the driver may
 * claim it.
 */
object NativeGamepadSupport {
    /**
     * True when one of [devices] has this VID/PID and is a game controller for Android, by the rule
     * the form factor uses ([isGameController]): gamepad buttons, or joystick axes, on a device
     * that is not virtual. A device that passes it is already a pad for the stream, so the driver
     * must not claim it too.
     */
    fun hasNativeGamepad(vendorId: Int, productId: Int, devices: List<InputDeviceFacts>): Boolean =
        devices.any {
            it.vendorId == vendorId &&
                it.productId == productId &&
                isGameController(it.sources, it.isVirtual, it.hasJoystickXy)
        }

    /** The same check against the devices Android has right now. */
    @JvmStatic
    fun isRecognized(vendorId: Int, productId: Int): Boolean =
        hasNativeGamepad(
            vendorId,
            productId,
            InputDevice.getDeviceIds().asList().mapNotNull { id ->
                // A device removed while looping is null.
                InputDevice.getDevice(id)?.let {
                    InputDeviceFacts(
                        it.vendorId,
                        it.productId,
                        it.sources,
                        hasJoystickXy = it.hasJoystickXy(),
                        isVirtual = it.isVirtual,
                    )
                }
            },
        )
}
