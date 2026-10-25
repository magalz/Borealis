package com.limelight.binding.input.driver

import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowUsbManager

/**
 * The stock USB manager shadow, which also keeps the permission requests the code makes, and fails
 * them on demand the way a Samsung Knox policy does: with a [SecurityException] the API does not
 * document. It behaves as the stock shadow until a test sets [failure] or [openFails], so all the
 * driver tests use it together with [ShadowScriptedUsbConnection], in one Robolectric sandbox:
 * `@Config(shadows = [ShadowMoonBridge::class, ShadowScriptedUsbConnection::class,
 * ShadowRequestingUsbManager::class])`.
 */
@Implements(UsbManager::class)
class ShadowRequestingUsbManager : ShadowUsbManager() {
    /** One call of `requestPermission`. */
    class Request(val device: UsbDevice, val pendingIntent: PendingIntent)

    val requests = CopyOnWriteArrayList<Request>()

    /** When set, the next requests throw it. */
    var failure: SecurityException? = null

    /** When set, `openDevice` returns null, as it does for a device the system cannot open. */
    var openFails = false

    @Suppress("unused")
    @Implementation
    override fun openDevice(device: UsbDevice): UsbDeviceConnection? =
        if (openFails) null else super.openDevice(device)

    @Suppress("unused")
    @Implementation
    fun requestPermission(device: UsbDevice, pendingIntent: PendingIntent) {
        failure?.let { throw it }
        requests.add(Request(device, pendingIntent))
    }
}
