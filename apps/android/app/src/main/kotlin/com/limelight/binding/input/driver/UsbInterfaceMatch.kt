package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

/**
 * The claim rule the three Xbox drivers share: the device is from one of [vendors], and its first
 * interface is vendor specific with the given [subclass] and [protocol]. Only the first interface
 * decides, and a device with no interface matches nothing.
 */
internal fun matchesFirstInterface(
    device: UsbDevice,
    vendors: IntArray,
    subclass: Int,
    protocol: Int,
): Boolean {
    if (device.vendorId !in vendors || device.interfaceCount < 1) return false

    val first = device.getInterface(0)
    return first.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
        first.interfaceSubclass == subclass &&
        first.interfaceProtocol == protocol
}
