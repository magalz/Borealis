package io.github.magalz.borealis.input

import android.view.InputDevice

/** What the USB driver needs to know about one Android input device. */
data class InputDeviceFacts(val vendorId: Int, val productId: Int, val sources: Int)

/**
 * Decides whether Android already handles a USB pad, so that the USB driver leaves it alone.
 *
 * Android must offer a gamepad for the pad. A dongle can show up only as a keyboard, a mouse or a
 * joystick-only device under the same VID/PID (the 8BitDo Ultimate 2 for PC dongle, 2DC8:310B):
 * that is no native pad support, so the driver may claim it.
 */
object NativeGamepadSupport {
    /** True when one of [devices] has this VID/PID and a gamepad source. */
    fun hasNativeGamepad(vendorId: Int, productId: Int, devices: List<InputDeviceFacts>): Boolean =
        devices.any {
            it.vendorId == vendorId &&
                it.productId == productId &&
                it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
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
                    InputDeviceFacts(it.vendorId, it.productId, it.sources)
                }
            },
        )
}
