package io.github.magalz.borealis.input.devices

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Parcelable
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** The interface subclass of an Xbox One pad (vendor-specific class 255, protocol 208). */
const val XBOX_ONE_INTERFACE_SUBCLASS = 71

/** The interface protocol of an Xbox One pad. */
const val XBOX_ONE_INTERFACE_PROTOCOL = 208

/**
 * A USB device as `UsbManager` lists it: one interface of [interfaceClass], [subclass] and
 * [protocol]. The default is the interface of an Xbox 360 wired pad (255, 93, 1), which the USB
 * driver claims for the vendors it knows. The reflection relies on the hidden constructors of the
 * pinned sdk=35 (Robolectric 4.17).
 */
fun usbDevice(
    name: String,
    vendorId: Int,
    productId: Int,
    interfaceClass: Int = UsbConstants.USB_CLASS_VENDOR_SPEC,
    subclass: Int = 93,
    protocol: Int = 1,
): UsbDevice {
    val int = Int::class.javaPrimitiveType
    val boolean = Boolean::class.javaPrimitiveType
    val configurations = java.lang.reflect.Array.newInstance(UsbConfiguration::class.java, 1)
    java.lang.reflect.Array.set(
        configurations,
        0,
        usbConfiguration(usbInterface(interfaceClass, subclass, protocol)),
    )
    val builder =
        ReflectionHelpers.callConstructor(
            Class.forName("android.hardware.usb.UsbDevice\$Builder"),
            ClassParameter.from(String::class.java, name),
            ClassParameter.from(int, vendorId),
            ClassParameter.from(int, productId),
            ClassParameter.from(int, 0),
            ClassParameter.from(int, 0),
            ClassParameter.from(int, 0),
            ClassParameter.from(String::class.java, "maker"),
            ClassParameter.from(String::class.java, "pad"),
            ClassParameter.from(String::class.java, "1"),
            ClassParameter.from(configurations.javaClass, configurations),
            ClassParameter.from(String::class.java, "serial"),
            ClassParameter.from(boolean, false),
            ClassParameter.from(boolean, false),
            ClassParameter.from(boolean, false),
            ClassParameter.from(boolean, false),
            ClassParameter.from(boolean, false),
        )
    // The device wants a serial reader; this one never reads anything.
    val readerType = Class.forName("android.hardware.usb.IUsbSerialReader")
    val reader =
        java.lang.reflect.Proxy.newProxyInstance(readerType.classLoader, arrayOf(readerType)) {
            _,
            _,
            _ ->
            null
        }
    return ReflectionHelpers.callInstanceMethod(
        builder,
        "build",
        ClassParameter.from(readerType, reader),
    )
}

private fun usbInterface(interfaceClass: Int, subclass: Int, protocol: Int): UsbInterface {
    val int = Int::class.javaPrimitiveType
    val usbInterface =
        ReflectionHelpers.callConstructor(
            UsbInterface::class.java,
            ClassParameter.from(int, 0),
            ClassParameter.from(int, 0),
            ClassParameter.from(String::class.java, "pad"),
            ClassParameter.from(int, interfaceClass),
            ClassParameter.from(int, subclass),
            ClassParameter.from(int, protocol),
        )
    ReflectionHelpers.callInstanceMethod<Unit>(
        usbInterface,
        "setEndpoints",
        ClassParameter.from(Array<Parcelable>::class.java, emptyArray<Parcelable>()),
    )
    return usbInterface
}

private fun usbConfiguration(usbInterface: UsbInterface): UsbConfiguration {
    val int = Int::class.javaPrimitiveType
    val configuration =
        ReflectionHelpers.callConstructor(
            UsbConfiguration::class.java,
            ClassParameter.from(int, 1),
            ClassParameter.from(String::class.java, "pad"),
            ClassParameter.from(int, 0),
            ClassParameter.from(int, 0),
        )
    ReflectionHelpers.callInstanceMethod<Unit>(
        configuration,
        "setInterfaces",
        ClassParameter.from(Array<Parcelable>::class.java, arrayOf<Parcelable>(usbInterface)),
    )
    return configuration
}

/** Plugs [device] into the `UsbManager` of the application, with no permission granted. */
fun plugUsb(device: UsbDevice) {
    val manager = RuntimeEnvironment.getApplication().getSystemService(UsbManager::class.java)
    shadowOf(manager).addOrUpdateUsbDevice(device, false)
}
