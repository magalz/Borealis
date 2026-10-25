package com.limelight.binding.input.driver

import android.hardware.input.InputManager
import android.view.InputDevice
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowInputDevice

/** The Android input devices the USB drivers look at to see what the system already handles. */
internal object PadInputDevices {
    /** The sources of an Android gamepad. */
    const val GAMEPAD = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD

    /** What Android showed for the keyboard-and-mouse interface of a dongle: no gamepad. */
    const val KEYBOARD_AND_MOUSE = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE

    private var nextId = 100

    /** Adds an input device to Android and returns it. */
    @Suppress("DEPRECATION")
    fun add(
        vendorId: Int,
        productId: Int,
        sources: Int = GAMEPAD,
        controllerNumber: Int = 0,
    ): InputDevice {
        val device = ShadowInputDevice.makeInputDeviceNamed("pad $vendorId:$productId")
        fun set(field: String, value: Int) =
            InputDevice::class
                .java
                .getDeclaredField(field)
                .apply { isAccessible = true }
                .setInt(device, value)
        set("mId", nextId++)
        set("mVendorId", vendorId)
        set("mProductId", productId)
        set("mSources", sources)
        set("mControllerNumber", controllerNumber)
        val inputManager =
            RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)
        shadowOf(inputManager).addInputDevice(device)
        return device
    }
}
