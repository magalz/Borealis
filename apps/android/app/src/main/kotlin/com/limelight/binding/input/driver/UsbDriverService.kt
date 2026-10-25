package com.limelight.binding.input.driver

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.input.NativeGamepadSupport
import io.github.magalz.borealis.input.UsbPermissionRequests

class UsbDriverService : Service(), UsbDriverListener {
    private var usbManager: UsbManager? = null
    private lateinit var prefConfig: PreferenceConfiguration
    private var started = false

    private val receiver = UsbEventReceiver()
    private val binder = UsbDriverBinder()

    private val controllers = ArrayList<AbstractController>()

    // BOREALIS: the permission dialog waits for the first frame of the stream (see
    // UsbPermissionRequests)
    private val permissionRequests = UsbPermissionRequests<UsbDevice>(this::askForPermission)

    private var listener: UsbDriverListener? = null
    private var stateListener: UsbDriverStateListener? = null
    private var nextDeviceId = 0

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("LongParameterList")
    override fun reportControllerState(
        controllerId: Int,
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float,
        leftTrigger: Float,
        rightTrigger: Float,
    ) {
        // Call through to the client's listener
        listener?.reportControllerState(
            controllerId,
            buttonFlags,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
            leftTrigger,
            rightTrigger,
        )
    }

    override fun deviceRemoved(controller: AbstractController) {
        // Remove the the controller from our list (if not removed already)
        controllers.remove(controller)

        // Call through to the client's listener
        listener?.deviceRemoved(controller)
    }

    override fun deviceAdded(controller: AbstractController) {
        // Call through to the client's listener
        listener?.deviceAdded(controller)
    }

    inner class UsbEventReceiver : BroadcastReceiver() {
        // The deprecated getParcelableExtra stays: the typed one needs API 33.
        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            val action = checkNotNull(intent.action)

            // Initial attachment broadcast
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                // shouldClaimDevice() looks at the kernel's enumerated input
                // devices to make its decision about whether to prompt to take
                // control of the device. The kernel bringing up the input stack
                // may race with this callback and cause us to prompt when the
                // kernel is capable of running the device. Let's post a delayed
                // message to process this state change to allow the kernel
                // some time to bring up the stack.
                Handler()
                    .postDelayed(
                        {
                            // Continue the state machine
                            handleUsbDeviceState(checkNotNull(device))
                        },
                        ATTACH_DELAY_MS,
                    )
            }
            // Subsequent permission dialog completion intent
            else if (action == ACTION_USB_PERMISSION) {
                val device =
                    checkNotNull(intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE))
                permissionRequests.resolved(device.deviceName) // BOREALIS

                // Permission dialog is now closed
                stateListener?.onUsbPermissionPromptCompleted()

                // If we got this far, we've already found we're able to handle this device
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    handleUsbDeviceState(device)
                }
            }
        }
    }

    inner class UsbDriverBinder : Binder() {
        fun setListener(listener: UsbDriverListener?) {
            this@UsbDriverService.listener = listener

            // Report all controllerMap that already exist
            if (listener != null) {
                for (controller in controllers) {
                    listener.deviceAdded(controller)
                }
            }
        }

        fun setStateListener(stateListener: UsbDriverStateListener?) {
            this@UsbDriverService.stateListener = stateListener
        }

        fun start() {
            this@UsbDriverService.start()
        }

        // BOREALIS: the stream shows its first frame, so the held permission dialogs may appear
        fun onStreamReady() {
            // The first frame is reported from the render thread; the requests live on the main one
            Handler(Looper.getMainLooper()).post(permissionRequests::onReady)
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    private fun handleUsbDeviceState(device: UsbDevice) {
        // Are we able to operate it?
        if (shouldClaimDevice(device, prefConfig.bindAllUsb)) {
            val manager = checkNotNull(usbManager)

            // Do we have permission yet?
            if (!manager.hasPermission(device)) {
                // BOREALIS: ask once the stream shows a picture
                permissionRequests.request(device.deviceName, device)
                return
            }

            // Open the device
            val connection = manager.openDevice(device)
            if (connection == null) {
                LimeLog.warning("Unable to open USB device: " + device.deviceName)
                return
            }

            val controller: AbstractController =
                if (XboxOneController.canClaimDevice(device)) {
                    XboxOneController(device, connection, nextDeviceId++, this)
                } else if (Xbox360Controller.canClaimDevice(device)) {
                    Xbox360Controller(device, connection, nextDeviceId++, this)
                } else if (Xbox360WirelessDongle.canClaimDevice(device)) {
                    Xbox360WirelessDongle(device, connection, nextDeviceId++, this)
                } else {
                    // Unreachable
                    return
                }

            // Start the controller
            if (!controller.start()) {
                connection.close()
                return
            }

            // Add this controller to the list
            controllers.add(controller)
        }
    }

    // BOREALIS: moved out of handleUsbDeviceState() so that UsbPermissionRequests can call it later
    private fun askForPermission(device: UsbDevice) {
        val manager = checkNotNull(usbManager)

        // A pad that went away while its request was held needs no dialog
        if (!manager.deviceList.containsKey(device.deviceName)) {
            permissionRequests.resolved(device.deviceName)
            return
        }

        // Let's ask for permission
        try {
            // Tell the state listener that we're about to display a permission dialog
            stateListener?.onUsbPermissionPromptStarting()

            var intentFlags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // This PendingIntent must be mutable to allow the framework to populate
                // EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED.
                intentFlags = intentFlags or PendingIntent.FLAG_MUTABLE
            }

            // This function is not documented as throwing any exceptions (denying access
            // is indicated by calling the PendingIntent with a false result). However,
            // Samsung Knox has some policies which block this request, but rather than
            // just returning a false result or returning 0 enumerated devices,
            // they throw an undocumented SecurityException from this call, crashing
            // the whole app. :(

            // Use an explicit intent to activate our unexported broadcast receiver, as required on
            // Android 14+
            val i = Intent(ACTION_USB_PERMISSION)
            i.setPackage(packageName)

            manager.requestPermission(
                device,
                PendingIntent.getBroadcast(this@UsbDriverService, 0, i, intentFlags),
            )
        } catch (_: SecurityException) {
            permissionRequests.resolved(device.deviceName) // BOREALIS
            Toast.makeText(this, this.getText(R.string.error_usb_prohibited), Toast.LENGTH_LONG)
                .show()
            stateListener?.onUsbPermissionPromptCompleted()
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun start() {
        val manager = usbManager
        if (started || manager == null) {
            return
        }

        started = true
        permissionRequests.reset() // BOREALIS: a stream starts, its picture is not up yet

        // Register for USB attach broadcasts and permission completions
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Enumerate existing devices
        for (dev in manager.deviceList.values) {
            if (shouldClaimDevice(dev, prefConfig.bindAllUsb)) {
                // Start the process of claiming this device
                handleUsbDeviceState(dev)
            }
        }
    }

    private fun stop() {
        if (!started) {
            return
        }

        started = false
        permissionRequests.reset() // BOREALIS

        // Stop the attachment receiver
        unregisterReceiver(receiver)

        // Stop all controllers
        while (controllers.isNotEmpty()) {
            // Stop and remove the controller
            controllers.removeAt(0).stop()
        }
    }

    override fun onCreate() {
        this.usbManager = getSystemService(Context.USB_SERVICE) as UsbManager?
        this.prefConfig = PreferenceConfiguration.readPreferences(this)
    }

    override fun onDestroy() {
        stop()

        // Remove listeners
        listener = null
        stateListener = null
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    interface UsbDriverStateListener {
        fun onUsbPermissionPromptStarting()

        fun onUsbPermissionPromptCompleted()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.limelight.USB_PERMISSION"

        // How long an attached device waits for the kernel to bring up its input stack.
        private const val ATTACH_DELAY_MS = 1000L

        @JvmStatic
        fun isRecognizedInputDevice(device: UsbDevice): Boolean {
            // BOREALIS: only an Android gamepad with this VID and PID counts as native support (a
            // dongle's keyboard or mouse does not)
            return NativeGamepadSupport.isRecognized(device.vendorId, device.productId)
        }

        @JvmStatic
        fun kernelSupportsXboxOne(): Boolean {
            val kernelVersion = System.getProperty("os.version")
            LimeLog.info("Kernel Version: $kernelVersion")

            return if (kernelVersion == null) {
                // We'll assume this is some newer version of Android
                // that doesn't let you read the kernel version this way.
                true
            } else if (kernelVersion.startsWith("2.") || kernelVersion.startsWith("3.")) {
                // These are old kernels that definitely don't support Xbox One controllers properly
                false
            } else if (kernelVersion.startsWith("4.4.") || kernelVersion.startsWith("4.9.")) {
                // These aren't guaranteed to have backported kernel patches for proper Xbox One
                // support (though some devices will).
                false
            } else {
                // The next AOSP common kernel is 4.14 which has working Xbox One controller support
                true
            }
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ComplexCondition")
        @JvmStatic
        fun kernelSupportsXbox360W(): Boolean {
            // Check if this kernel is 4.2+ to see if the xpad driver sets Xbox 360 wireless LEDs
            // https://github.com/torvalds/linux/commit/75b7f05d2798ee3a1cc5bbdd54acd0e318a80396
            val kernelVersion = System.getProperty("os.version")
            if (kernelVersion != null) {
                if (
                    kernelVersion.startsWith("2.") ||
                        kernelVersion.startsWith("3.") ||
                        kernelVersion.startsWith("4.0.") ||
                        kernelVersion.startsWith("4.1.")
                ) {
                    // Even if LED devices are present, the driver won't set the initial LED state.
                    return false
                }
            }

            // We know we have a kernel that should set Xbox 360 wireless LEDs, but we still don't
            // know if CONFIG_JOYSTICK_XPAD_LEDS was enabled during the kernel build. Unfortunately
            // it's not possible to detect this reliably due to Android's app sandboxing. Reading
            // /proc/config.gz and enumerating /sys/class/leds are both blocked by SELinux on any
            // relatively modern device. We will assume that CONFIG_JOYSTICK_XPAD_LEDS=y on these
            // kernels and users can override by using the settings option to claim all devices.
            return true
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ComplexCondition")
        @JvmStatic
        fun shouldClaimDevice(device: UsbDevice, claimAllAvailable: Boolean): Boolean {
            return ((!kernelSupportsXboxOne() ||
                !isRecognizedInputDevice(device) ||
                claimAllAvailable) && XboxOneController.canClaimDevice(device)) ||
                ((!isRecognizedInputDevice(device) || claimAllAvailable) &&
                    Xbox360Controller.canClaimDevice(device)) ||
                // We must not call isRecognizedInputDevice() because wireless controllers don't
                // share the same product ID as the dongle
                ((!kernelSupportsXbox360W() || claimAllAvailable) &&
                    Xbox360WirelessDongle.canClaimDevice(device))
        }
    }
}
