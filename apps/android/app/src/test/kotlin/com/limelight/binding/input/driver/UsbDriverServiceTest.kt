package com.limelight.binding.input.driver

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.driver.ShadowScriptedUsbConnection.Read
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.time.Duration
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Characterizes the USB driver service as the stream sees it: the binder, the claim of the pads
 * that are plugged in or that wake later, and what reaches the listener. The permission dialog and
 * its timing have their own test, [io.github.magalz.borealis.input.UsbDriverServicePermissionTest].
 * Controllers report from their input thread, which waits a second before the first event.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowScriptedUsbConnection::class,
            ShadowRequestingUsbManager::class,
        ]
)
class UsbDriverServiceTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val noThreadDeaths = NoUncaughtThreadExceptions()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val shadowUsb = shadowOf(usbManager)
    private val savedKernel: String? = System.getProperty("os.version")

    private var serviceController: ServiceController<UsbDriverService>? = null
    private lateinit var service: UsbDriverService
    private lateinit var binder: UsbDriverService.UsbDriverBinder

    private val listener = RecordingListener()
    private val promptEvents = mutableListOf<String>()
    private val stateListener =
        object : UsbDriverService.UsbDriverStateListener {
            override fun onUsbPermissionPromptStarting() {
                promptEvents += "starting"
            }

            override fun onUsbPermissionPromptCompleted() {
                promptEvents += "completed"
            }
        }

    @After
    fun tearDown() {
        serviceController?.destroy()
        if (savedKernel == null) System.clearProperty("os.version")
        else System.setProperty("os.version", savedKernel)
    }

    @Suppress("DEPRECATION")
    private fun createService(bindAll: Boolean = false) {
        if (bindAll) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("checkbox_usb_bind_all", true)
                .commit()
        }
        val controller = Robolectric.buildService(UsbDriverService::class.java).create()
        serviceController = controller
        service = controller.get()
        binder = service.onBind(Intent()) as UsbDriverService.UsbDriverBinder
        binder.setStateListener(stateListener)
    }

    private fun kernel(version: String) = System.setProperty("os.version", version)

    private fun plugIn(device: UsbDevice, permission: Boolean = true) =
        shadowUsb.addOrUpdateUsbDevice(device, permission)

    private fun xboxOne(name: String = "/dev/xboxone") = UsbPads.xboxOnePad(name = name)

    private fun xbox360(name: String = "/dev/xbox360") = UsbPads.xbox360Pad(name = name)

    private fun idle(millis: Long = 0) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun sendAttach(device: UsbDevice) {
        context.sendBroadcast(
            Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                .setPackage(context.packageName)
                .putExtra(UsbManager.EXTRA_DEVICE, device)
        )
        idle()
    }

    private fun sendPermissionResult(device: UsbDevice, granted: Boolean) {
        context.sendBroadcast(
            Intent("com.limelight.USB_PERMISSION")
                .setPackage(context.packageName)
                .putExtra(UsbManager.EXTRA_DEVICE, device)
                .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, granted)
        )
        idle()
    }

    /**
     * The controllers the service holds, as `setListener` replays them; it replaces the listener.
     */
    private fun heldControllers(): List<AbstractController> {
        val replay = RecordingListener()
        binder.setListener(replay)
        return replay.drain().map { (it as PadEvent.Added).controller }
    }

    private fun connections() = ShadowScriptedUsbConnection.created.toList()

    private class StubController(id: Int, listener: UsbDriverListener) :
        AbstractController(id, listener, 0x045E, 0x028E) {
        override fun start() = true

        override fun stop() {
            // Nothing is held open.
        }

        override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
            // The stub has no motors.
        }

        override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
            // The stub has no motors.
        }
    }

    @Test
    fun `the binder is the same object every time`() {
        createService()

        assertThat(service.onBind(Intent())).isSameInstanceAs(binder)
    }

    @Test
    fun `start claims a pad that is plugged in and has permission, by its family`() {
        plugIn(xboxOne())
        plugIn(xbox360())
        createService()

        binder.start()

        val held = heldControllers()
        assertThat(held.map { it.javaClass.simpleName })
            .containsExactly("XboxOneController", "Xbox360Controller")
        // Each pad was opened once and its driver started on it: the packets are the init of the
        // Xbox One pad (two packets for a Series pad) and the LED command of the Xbox 360 pad.
        assertThat(connections().map { it.device.deviceName })
            .containsExactly("/dev/xboxone", "/dev/xbox360")
        val written =
            connections().associate { it.device.deviceName to it.writes.map { w -> w.data.size } }
        assertThat(written["/dev/xboxone"]).containsExactly(5, 5).inOrder()
        assertThat(written["/dev/xbox360"]).containsExactly(3)
    }

    @Test
    fun `controllers get consecutive ids`() {
        plugIn(xboxOne())
        plugIn(xbox360())
        createService()

        binder.start()

        assertThat(heldControllers().map { it.controllerId }).containsExactly(0, 1)
    }

    @Test
    fun `two pads of the same kind get consecutive ids`() {
        plugIn(xbox360("/dev/xbox360-a"))
        plugIn(xbox360("/dev/xbox360-b"))
        createService()

        binder.start()

        assertThat(heldControllers().map { it.controllerId }).containsExactly(0, 1)
    }

    @Test
    fun `a second start does nothing`() {
        plugIn(xbox360())
        createService()

        binder.start()
        binder.start()

        assertThat(connections()).hasSize(1)
        assertThat(heldControllers()).hasSize(1)
    }

    @Test
    fun `a pad that Android already handles is left alone, unless the setting claims all`() {
        kernel("5.10.0")
        PadInputDevices.add(0x045E, 0x028E, PadInputDevices.GAMEPAD)
        plugIn(xbox360())
        createService()

        binder.start()

        assertThat(connections()).isEmpty()
        assertThat(promptEvents).isEmpty()
    }

    @Test
    fun `the setting claims a pad Android already handles`() {
        kernel("5.10.0")
        PadInputDevices.add(0x045E, 0x028E, PadInputDevices.GAMEPAD)
        plugIn(xbox360())
        createService(bindAll = true)

        binder.start()

        assertThat(heldControllers()).hasSize(1)
    }

    @Test
    fun `a device no driver claims is ignored`() {
        plugIn(UsbPads.device("/dev/mouse", 0x1234, 0x0001, emptyList()))
        createService(bindAll = true)

        binder.start()

        assertThat(connections()).isEmpty()
        assertThat(heldControllers()).isEmpty()
    }

    @Test
    fun `a controller that cannot start is closed and not kept`() {
        // An interface without an OUT endpoint: the driver cannot find its endpoints.
        val device =
            UsbPads.device(
                "/dev/broken",
                0x045E,
                0x028E,
                listOf(
                    UsbPads.usbInterface(
                        0,
                        subclass = UsbPads.XB360_SUBCLASS,
                        protocol = UsbPads.XB360_PROTOCOL,
                        endpoints = listOf(UsbPads.inEndpoint()),
                    )
                ),
            )
        plugIn(device)
        createService()

        binder.start()

        assertThat(connections().single().closes).isEqualTo(1)
        assertThat(heldControllers()).isEmpty()
    }

    @Test
    fun `a wireless dongle is lit and closed, never kept`() {
        kernel("3.10.0")
        plugIn(UsbPads.dongle())
        createService()
        binder.setListener(listener)

        binder.start()

        val connection = connections().single()
        assertThat(connection.writes).hasSize(1)
        assertThat(connection.writes.single().data[2]).isEqualTo(0x08.toByte())
        assertThat(connection.closes).isEqualTo(1)
        assertThat(heldControllers()).isEmpty()
        // A dongle is no controller: nothing was announced.
        assertThat(listener.drain()).isEmpty()
    }

    @Test
    fun `a pad with no permission is not opened until it is granted`() {
        plugIn(xbox360(), permission = false)
        createService()

        binder.start()

        assertThat(connections()).isEmpty()
        assertThat(heldControllers()).isEmpty()
    }

    @Test
    fun `a listener set later hears about the pads already claimed`() {
        plugIn(xboxOne())
        createService()
        binder.start()

        binder.setListener(listener)

        val added = listener.next() as PadEvent.Added
        assertThat(added.controller).isInstanceOf(XboxOneController::class.java)
        assertThat(listener.drain()).isEmpty()
    }

    @Test
    fun `setting no listener is allowed and the service stops forwarding`() {
        plugIn(xbox360())
        createService()
        binder.setListener(listener)
        binder.start()

        binder.setListener(null)
        service.reportControllerState(0, 1, 0f, 0f, 0f, 0f, 0f, 0f)

        assertThat(listener.drain()).isEmpty()
    }

    @Test
    fun `the service forwards what a controller reports, its arrival and its removal`() {
        createService()
        binder.setListener(listener)
        val stub = StubController(9, listener)

        service.reportControllerState(7, 0x1000, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
        service.deviceAdded(stub)
        service.deviceRemoved(stub)

        val events = listener.drain()
        val state = events[0] as PadEvent.State
        assertThat(state.controllerId).isEqualTo(7)
        assertThat(state.buttonFlags).isEqualTo(0x1000)
        assertThat(listOf(state.leftStickX, state.leftStickY, state.rightStickX, state.rightStickY))
            .containsExactly(0.1f, 0.2f, 0.3f, 0.4f)
            .inOrder()
        assertThat(listOf(state.leftTrigger, state.rightTrigger))
            .containsExactly(0.5f, 0.6f)
            .inOrder()
        assertThat((events[1] as PadEvent.Added).controller).isSameInstanceAs(stub)
        assertThat((events[2] as PadEvent.Removed).controller).isSameInstanceAs(stub)
        assertThat(events).hasSize(3)
    }

    @Test
    fun `without a listener the service drops what a controller reports`() {
        createService()
        val stub = StubController(9, listener)

        service.reportControllerState(7, 0x1000, 0f, 0f, 0f, 0f, 0f, 0f)
        service.deviceAdded(stub)
        service.deviceRemoved(stub)

        assertThat(listener.drain()).isEmpty()
    }

    @Test
    fun `a controller that was removed leaves the list and is not stopped with the service`() {
        plugIn(xbox360())
        createService()
        binder.start()
        val controller = heldControllers().single()

        service.deviceRemoved(controller)

        assertThat(heldControllers()).isEmpty()
        serviceController?.destroy()
        // The service let go of it: stopping the service does not stop it.
        assertThat(connections().single().closes).isEqualTo(0)
        controller.stop()
    }

    @Test
    fun `reports of a claimed pad reach the listener after its arrival`() {
        ShadowScriptedUsbConnection.onOpen = {
            it.report(0x00, 0x14, 0x01, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
        plugIn(xbox360())
        createService()
        binder.setListener(listener)

        binder.start()

        val state = listener.states(1).single()
        assertThat(state.controllerId).isEqualTo(0)
        assertThat(state.buttonFlags).isEqualTo(ControllerPacket.UP_FLAG or ControllerPacket.A_FLAG)
    }

    @Test
    fun `a pad that fails is removed from the service and the listener hears it`() {
        ShadowScriptedUsbConnection.onOpen = { it.reads.add(Read.Failure()) }
        plugIn(xbox360())
        createService()
        binder.setListener(listener)

        binder.start()

        assertThat(listener.next()).isInstanceOf(PadEvent.Added::class.java)
        val removed = listener.next() as PadEvent.Removed
        assertThat(removed.controller).isInstanceOf(Xbox360Controller::class.java)
        assertThat(heldControllers()).isEmpty()
        assertThat(connections().single().closes).isEqualTo(1)
    }

    @Test
    fun `stopping the service stops each controller in turn and lets go of the listeners`() {
        plugIn(xboxOne())
        plugIn(xbox360())
        createService()
        binder.setListener(listener)
        binder.start()
        val held = heldControllers()
        binder.setListener(listener)
        listener.drain()

        serviceController?.destroy()

        val removed = listener.drain().map { (it as PadEvent.Removed).controller }
        assertThat(removed).containsExactlyElementsIn(held).inOrder()
        assertThat(connections().map { it.closes }).containsExactly(1, 1)
        // The service dropped its listeners: nothing reaches them any more.
        service.reportControllerState(0, 1, 0f, 0f, 0f, 0f, 0f, 0f)
        assertThat(listener.drain()).isEmpty()
    }

    @Test
    fun `stopping the service motors off each pad before it closes it`() {
        plugIn(xbox360())
        createService()
        binder.start()

        serviceController?.destroy()

        // The LED command, then the rumble that turns the motors off.
        val connection = connections().single()
        assertThat(connection.writes.map { it.data.toList().map { b -> b.toInt() } })
            .containsExactly(listOf(0x01, 0x03, 0x02), listOf(0x00, 0x08, 0x00, 0, 0, 0, 0, 0))
            .inOrder()
        assertThat(connection.closes).isEqualTo(1)
    }

    @Test
    fun `stopping a service that never started does nothing`() {
        createService()

        serviceController?.destroy()

        assertThat(connections()).isEmpty()
    }

    @Test
    fun `a pad that wakes is looked at only a second after the broadcast`() {
        createService()
        binder.start()
        val device = xbox360()
        plugIn(device)

        sendAttach(device)
        idle(999)
        assertThat(connections()).isEmpty()

        idle(1)
        assertThat(connections()).hasSize(1)
        assertThat(heldControllers()).hasSize(1)
    }

    @Test
    fun `a device that wakes and that no driver claims is ignored`() {
        createService()
        binder.start()
        val device = UsbPads.device("/dev/mouse", 0x1234, 0x0001, emptyList())
        plugIn(device)

        sendAttach(device)
        idle(1_100)

        assertThat(connections()).isEmpty()
        assertThat(promptEvents).isEmpty()
    }

    @Test
    fun `a broadcast before the start is not received`() {
        createService()
        val device = xbox360()
        plugIn(device)

        sendAttach(device)
        idle(1_100)

        assertThat(connections()).isEmpty()
    }

    @Test
    fun `after the service stops its receiver is gone`() {
        createService()
        binder.start()
        serviceController?.destroy()
        val device = xbox360()
        plugIn(device)

        sendAttach(device)
        idle(1_100)

        assertThat(connections()).isEmpty()
    }

    @Test
    fun `a granted permission ends the prompt and claims the pad`() {
        val device = xbox360()
        plugIn(device, permission = false)
        createService()
        binder.start()
        binder.onStreamReady()
        idle()
        assertThat(promptEvents).containsExactly("starting")

        plugIn(device, permission = true)
        sendPermissionResult(device, granted = true)

        assertThat(promptEvents).containsExactly("starting", "completed").inOrder()
        assertThat(heldControllers()).hasSize(1)
        assertThat(connections()).hasSize(1)
    }

    @Test
    fun `a refused permission ends the prompt and claims nothing`() {
        val device = xbox360()
        plugIn(device, permission = false)
        createService()
        binder.start()
        binder.onStreamReady()
        idle()

        sendPermissionResult(device, granted = false)

        assertThat(promptEvents).containsExactly("starting", "completed").inOrder()
        assertThat(connections()).isEmpty()
        assertThat(heldControllers()).isEmpty()
    }

    @Test
    fun `an answered request can be asked again`() {
        val device = xbox360()
        plugIn(device, permission = false)
        createService()
        binder.start()
        binder.onStreamReady()
        idle()
        sendPermissionResult(device, granted = false)

        // The pad wakes again after the refusal: it asks again.
        sendAttach(device)
        idle(1_100)

        assertThat(promptEvents.filter { it == "starting" }).hasSize(2)
    }

    @Test
    fun `a pad that went away while its request was held gets no dialog, and asks again when it returns`() {
        val device = xbox360()
        plugIn(device, permission = false)
        createService()
        binder.start()
        shadowUsb.removeUsbDevice(device)

        binder.onStreamReady()
        idle()
        assertThat(promptEvents).isEmpty()

        plugIn(device, permission = false)
        sendAttach(device)
        idle(1_100)
        assertThat(promptEvents).containsExactly("starting")
    }

    @Test
    fun `a permission answer with no state listener still claims the pad`() {
        val device = xbox360()
        plugIn(device, permission = false)
        createService()
        binder.setStateListener(null)
        binder.start()

        plugIn(device, permission = true)
        sendPermissionResult(device, granted = true)

        assertThat(heldControllers()).hasSize(1)
    }

    @Test
    fun `the picture reported from another thread reaches the service on the main thread`() {
        plugIn(xbox360(), permission = false)
        createService()
        binder.start()

        // The first frame is reported from the render thread.
        Thread { binder.onStreamReady() }
            .apply {
                start()
                join()
            }
        assertThat(promptEvents).isEmpty()

        idle()
        assertThat(promptEvents).containsExactly("starting")
    }

    @Test
    fun `a picture reported before the stream starts does not count for the new stream`() {
        plugIn(xbox360(), permission = false)
        createService()
        binder.onStreamReady()
        idle()

        binder.start()
        assertThat(promptEvents).isEmpty()

        binder.onStreamReady()
        idle()
        assertThat(promptEvents).containsExactly("starting")
    }

    @Test
    fun `a service without a state listener still claims pads`() {
        plugIn(xbox360())
        createService()
        binder.setStateListener(null)

        binder.start()

        assertThat(heldControllers()).hasSize(1)
    }
}
