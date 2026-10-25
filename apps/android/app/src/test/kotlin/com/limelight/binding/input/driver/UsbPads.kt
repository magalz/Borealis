package com.limelight.binding.input.driver

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.Parcelable
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Proxy
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Builds the USB objects the drivers look at. The framework has no public constructors for them, so
 * this calls the hidden ones by reflection; they are those of the pinned sdk=35 (Robolectric 4.17).
 */
internal object UsbPads {
    const val VENDOR_SPECIFIC = UsbConstants.USB_CLASS_VENDOR_SPEC

    // The interface subclass and protocol of each family (the numbers the drivers match).
    const val XB360_SUBCLASS = 93
    const val XB360_PROTOCOL = 1
    const val XB360W_PROTOCOL = 129
    const val XB1_SUBCLASS = 71
    const val XB1_PROTOCOL = 208

    private const val BULK_ATTRIBUTES = 2
    private const val MAX_PACKET = 64

    fun endpoint(address: Int): UsbEndpoint =
        ReflectionHelpers.callConstructor(
            UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, BULK_ATTRIBUTES),
            ClassParameter.from(Int::class.javaPrimitiveType, MAX_PACKET),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
        )

    fun inEndpoint(number: Int = 1): UsbEndpoint = endpoint(UsbConstants.USB_DIR_IN or number)

    fun outEndpoint(number: Int = 1): UsbEndpoint = endpoint(UsbConstants.USB_DIR_OUT or number)

    /** An interface with one IN and one OUT endpoint unless [endpoints] says otherwise. */
    fun usbInterface(
        id: Int,
        usbClass: Int = VENDOR_SPECIFIC,
        subclass: Int,
        protocol: Int,
        endpoints: List<UsbEndpoint> = listOf(inEndpoint(), outEndpoint()),
    ): UsbInterface {
        val usbInterface =
            ReflectionHelpers.callConstructor(
                UsbInterface::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, id),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(String::class.java, "interface $id"),
                ClassParameter.from(Int::class.javaPrimitiveType, usbClass),
                ClassParameter.from(Int::class.javaPrimitiveType, subclass),
                ClassParameter.from(Int::class.javaPrimitiveType, protocol),
            )
        ReflectionHelpers.callInstanceMethod<Unit>(
            usbInterface,
            "setEndpoints",
            ClassParameter.from(
                Array<Parcelable>::class.java,
                endpoints.toTypedArray<Parcelable>(),
            ),
        )
        return usbInterface
    }

    /** A device whose interfaces all sit in its one configuration. */
    fun device(
        name: String,
        vendorId: Int,
        productId: Int,
        interfaces: List<UsbInterface>,
    ): UsbDevice {
        val configuration =
            ReflectionHelpers.callConstructor(
                UsbConfiguration::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, 1),
                ClassParameter.from(String::class.java, "configuration"),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
            )
        ReflectionHelpers.callInstanceMethod<Unit>(
            configuration,
            "setInterfaces",
            ClassParameter.from(
                Array<Parcelable>::class.java,
                interfaces.toTypedArray<Parcelable>(),
            ),
        )
        val configurations = ReflectArray.newInstance(UsbConfiguration::class.java, 1)
        ReflectArray.set(configurations, 0, configuration)
        val builder =
            ReflectionHelpers.callConstructor(
                Class.forName("android.hardware.usb.UsbDevice\$Builder"),
                ClassParameter.from(String::class.java, name),
                ClassParameter.from(Int::class.javaPrimitiveType, vendorId),
                ClassParameter.from(Int::class.javaPrimitiveType, productId),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(String::class.java, "maker"),
                ClassParameter.from(String::class.java, "pad"),
                ClassParameter.from(String::class.java, "1"),
                ClassParameter.from(configurations.javaClass, configurations),
                ClassParameter.from(String::class.java, "serial"),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType, false),
            )
        // The device wants a serial reader; this one never reads anything.
        val readerType = Class.forName("android.hardware.usb.IUsbSerialReader")
        val reader =
            Proxy.newProxyInstance(readerType.classLoader, arrayOf(readerType)) { _, _, _ -> null }
        return ReflectionHelpers.callInstanceMethod(
            builder,
            "build",
            ClassParameter.from(readerType, reader),
        )
    }

    /** A wired Xbox 360 pad: interface 255/93/1, as many interfaces as [interfaceCount] says. */
    fun xbox360Pad(
        name: String = "/dev/xbox360",
        vendorId: Int = 0x045E,
        productId: Int = 0x028E,
        interfaceCount: Int = 1,
    ): UsbDevice =
        device(
            name,
            vendorId,
            productId,
            // Each interface has endpoints of its own, so that a test sees which one is used.
            List(interfaceCount) {
                usbInterface(
                    it,
                    subclass = XB360_SUBCLASS,
                    protocol = XB360_PROTOCOL,
                    endpoints = listOf(inEndpoint(it + 1), outEndpoint(it + 1)),
                )
            },
        )

    /** An Xbox One pad: interface 255/71/208. */
    fun xboxOnePad(
        name: String = "/dev/xboxone",
        vendorId: Int = 0x045E,
        productId: Int = 0x02EA,
    ): UsbDevice =
        device(
            name,
            vendorId,
            productId,
            listOf(usbInterface(0, subclass = XB1_SUBCLASS, protocol = XB1_PROTOCOL)),
        )

    /** A wireless dongle: interface 255/93/129. */
    fun dongle(
        name: String = "/dev/dongle",
        vendorId: Int = 0x045E,
        productId: Int = 0x0291,
        interfaces: List<UsbInterface> =
            listOf(usbInterface(0, subclass = XB360_SUBCLASS, protocol = XB360W_PROTOCOL)),
    ): UsbDevice = device(name, vendorId, productId, interfaces)

    /** A connection for [device] that a test controls; see [ShadowScriptedUsbConnection]. */
    fun connection(device: UsbDevice): UsbDeviceConnection =
        ReflectionHelpers.callConstructor(
            UsbDeviceConnection::class.java,
            ClassParameter.from(UsbDevice::class.java, device),
        )

    /** The script and the record of [connection]. */
    fun scripted(connection: UsbDeviceConnection): ShadowScriptedUsbConnection =
        Shadow.extract(connection)
}
