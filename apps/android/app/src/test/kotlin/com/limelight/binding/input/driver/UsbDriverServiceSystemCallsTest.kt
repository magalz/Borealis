package com.limelight.binding.input.driver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import io.github.magalz.borealis.settings.ShadowMoonBridge
import io.github.magalz.borealis.testing.LimeLogCapture
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowToast

/**
 * The calls the service makes to the system's USB manager: the permission request it carries, what
 * the service does when a device policy (Samsung Knox) refuses it with an exception the API does
 * not document, and what it does when the system cannot open a device. When the dialog goes up and
 * when it is held is in [io.github.magalz.borealis.input.UsbDriverServicePermissionTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowMoonBridge::class,
            ShadowScriptedUsbConnection::class,
            ShadowRequestingUsbManager::class,
        ]
)
class UsbDriverServiceSystemCallsTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val log = LimeLogCapture()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val shadowUsb: ShadowRequestingUsbManager = Shadow.extract(usbManager)

    private lateinit var serviceController: ServiceController<UsbDriverService>
    private lateinit var binder: UsbDriverService.UsbDriverBinder
    private val promptEvents = mutableListOf<String>()

    @Before
    fun startService() {
        serviceController = Robolectric.buildService(UsbDriverService::class.java).create()
        binder = serviceController.get().onBind(Intent()) as UsbDriverService.UsbDriverBinder
        binder.setStateListener(
            object : UsbDriverService.UsbDriverStateListener {
                override fun onUsbPermissionPromptStarting() {
                    promptEvents += "starting"
                }

                override fun onUsbPermissionPromptCompleted() {
                    promptEvents += "completed"
                }
            }
        )
    }

    @After
    fun stopService() {
        serviceController.destroy()
    }

    private fun plugInWithoutPermission(): UsbDevice {
        val device = UsbPads.xbox360Pad()
        shadowUsb.addOrUpdateUsbDevice(device, false)
        return device
    }

    private fun idle(millis: Long = 0) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun askAfterThePicture() {
        binder.start()
        binder.onStreamReady()
        idle()
    }

    @Test
    fun `the request is a broadcast to this app that the system may fill in`() {
        val device = plugInWithoutPermission()

        askAfterThePicture()

        val request = shadowUsb.requests.single()
        assertThat(request.device).isEqualTo(device)
        val pendingIntent = shadowOf(request.pendingIntent)
        assertThat(pendingIntent.isBroadcast).isTrue()
        assertThat(pendingIntent.requestCode).isEqualTo(0)
        // An explicit intent: it names the app, so the receiver need not be exported.
        assertThat(pendingIntent.savedIntent.action).isEqualTo("com.limelight.USB_PERMISSION")
        assertThat(pendingIntent.savedIntent.`package`).isEqualTo(context.packageName)
        // The system adds the device and the answer to it, so it must be mutable (Android 12+).
        assertThat(pendingIntent.flags and PendingIntent.FLAG_MUTABLE)
            .isEqualTo(PendingIntent.FLAG_MUTABLE)
        assertThat(promptEvents).containsExactly("starting")
    }

    @Test
    fun `a request the device policy refuses shows a toast and ends the prompt`() {
        plugInWithoutPermission()
        shadowUsb.failure = SecurityException("blocked by Knox")

        askAfterThePicture()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(context.getString(R.string.error_usb_prohibited))
        assertThat(promptEvents).containsExactly("starting", "completed").inOrder()
        assertThat(shadowUsb.requests).isEmpty()
    }

    @Test
    fun `after the policy refused a request the pad asks again when it wakes`() {
        val device = plugInWithoutPermission()
        shadowUsb.failure = SecurityException("blocked by Knox")
        askAfterThePicture()
        shadowUsb.failure = null

        context.sendBroadcast(
            Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                .setPackage(context.packageName)
                .putExtra(UsbManager.EXTRA_DEVICE, device)
        )
        idle(1_100)

        assertThat(shadowUsb.requests).hasSize(1)
        assertThat(promptEvents).containsExactly("starting", "completed", "starting").inOrder()
    }

    @Test
    fun `a refused request with no state listener only shows the toast`() {
        plugInWithoutPermission()
        shadowUsb.failure = SecurityException("blocked by Knox")
        binder.setStateListener(null)

        askAfterThePicture()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(context.getString(R.string.error_usb_prohibited))
    }

    @Test
    fun `a device the system cannot open is skipped`() {
        shadowUsb.addOrUpdateUsbDevice(UsbPads.xbox360Pad(), true)
        shadowUsb.openFails = true

        binder.start()

        assertThat(ShadowScriptedUsbConnection.created).isEmpty()
        assertThat(promptEvents).isEmpty()
        // The pad is not kept: a listener set later hears of no controller.
        val replay = RecordingListener()
        binder.setListener(replay)
        assertThat(replay.drain()).isEmpty()
        assertThat(log.warnings()).containsExactly("Unable to open USB device: /dev/xbox360")
    }
}
