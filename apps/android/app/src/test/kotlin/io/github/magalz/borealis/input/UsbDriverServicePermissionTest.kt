package io.github.magalz.borealis.input

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.driver.UsbDriverService
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsbManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * The USB driver service puts the permission dialog of a pad on screen only once the stream shows a
 * picture. A pad that wakes after that asks at once, and a pad that already has a grant is claimed
 * without any dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class UsbDriverServicePermissionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val shadowUsb: ShadowUsbManager = shadowOf(usbManager)

    private val service = Robolectric.buildService(UsbDriverService::class.java).create().get()
    private val binder = service.onBind(Intent()) as UsbDriverService.UsbDriverBinder

    private var prompts = 0
    private val stateListener =
        object : UsbDriverService.UsbDriverStateListener {
            override fun onUsbPermissionPromptStarting() {
                prompts++
            }

            override fun onUsbPermissionPromptCompleted() = Unit
        }

    init {
        binder.setStateListener(stateListener)
    }

    // The reflection below relies on the hidden constructors of the pinned sdk=35 (Robolectric
    // 4.17).
    // An Xbox 360 wired pad: vendor-specific interface 255/93/1 of a vendor the driver knows.
    private fun pad(name: String): UsbDevice {
        val configurations =
            java.lang.reflect.Array.newInstance(
                android.hardware.usb.UsbConfiguration::class.java,
                1,
            )
        java.lang.reflect.Array.set(configurations, 0, padConfiguration(padInterface()))
        val builder =
            ReflectionHelpers.callConstructor(
                Class.forName("android.hardware.usb.UsbDevice\$Builder"),
                ClassParameter.from(String::class.java, name),
                ClassParameter.from(Int::class.javaPrimitiveType, 0x045E),
                ClassParameter.from(Int::class.javaPrimitiveType, 0x028E),
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

    private fun padInterface(): android.hardware.usb.UsbInterface {
        val usbInterface =
            ReflectionHelpers.callConstructor(
                android.hardware.usb.UsbInterface::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(String::class.java, "pad"),
                ClassParameter.from(
                    Int::class.javaPrimitiveType,
                    UsbConstants.USB_CLASS_VENDOR_SPEC,
                ),
                ClassParameter.from(Int::class.javaPrimitiveType, 93),
                ClassParameter.from(Int::class.javaPrimitiveType, 1),
            )
        // No endpoints: a claimed pad stops at "missing endpoint" instead of starting a thread.
        ReflectionHelpers.callInstanceMethod<Unit>(
            usbInterface,
            "setEndpoints",
            ClassParameter.from(
                Array<android.os.Parcelable>::class.java,
                emptyArray<android.os.Parcelable>(),
            ),
        )
        return usbInterface
    }

    private fun padConfiguration(
        usbInterface: android.hardware.usb.UsbInterface
    ): android.hardware.usb.UsbConfiguration {
        val configuration =
            ReflectionHelpers.callConstructor(
                android.hardware.usb.UsbConfiguration::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, 1),
                ClassParameter.from(String::class.java, "pad"),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
                ClassParameter.from(Int::class.javaPrimitiveType, 0),
            )
        ReflectionHelpers.callInstanceMethod<Unit>(
            configuration,
            "setInterfaces",
            ClassParameter.from(
                Array<android.os.Parcelable>::class.java,
                arrayOf<android.os.Parcelable>(usbInterface),
            ),
        )
        return configuration
    }

    private fun attach(device: UsbDevice) {
        shadowUsb.addOrUpdateUsbDevice(device, false)
        service.onAttached(device)
    }

    private fun UsbDriverService.onAttached(device: UsbDevice) {
        context.sendBroadcast(
            Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                .setPackage(context.packageName)
                .putExtra(UsbManager.EXTRA_DEVICE, device)
        )
        shadowOf(Looper.getMainLooper()).idle()
        // The service waits a second for the kernel before it looks at the device.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1_100))
    }

    @Test
    fun `a pad found at the start of a stream is not asked before the picture`() {
        shadowUsb.addOrUpdateUsbDevice(pad("/dev/pad-1"), false)

        binder.start()

        assertThat(prompts).isEqualTo(0)
    }

    @Test
    fun `the picture asks for the pad that was found at the start`() {
        shadowUsb.addOrUpdateUsbDevice(pad("/dev/pad-1"), false)
        binder.start()

        binder.onStreamReady()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(prompts).isEqualTo(1)
    }

    @Test
    fun `a pad that wakes before the picture is held until it`() {
        binder.start()

        attach(pad("/dev/pad-1"))
        assertThat(prompts).isEqualTo(0)

        binder.onStreamReady()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(prompts).isEqualTo(1)
    }

    @Test
    fun `a pad that wakes after the picture asks at once`() {
        binder.start()
        binder.onStreamReady()
        shadowOf(Looper.getMainLooper()).idle()

        attach(pad("/dev/pad-1"))

        assertThat(prompts).isEqualTo(1)
    }

    @Test
    fun `a pad that already has permission is claimed with no dialog, before and after the picture`() {
        shadowUsb.addOrUpdateUsbDevice(pad("/dev/pad-1"), true)

        // A claim that went through the dialog path would call the state listener.
        binder.start()
        assertThat(prompts).isEqualTo(0)

        binder.onStreamReady()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(prompts).isEqualTo(0)
    }
}
