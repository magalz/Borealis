package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.os.SystemClock
import com.limelight.LimeLog
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractXboxController(
    @JvmField protected val device: UsbDevice,
    @JvmField protected val connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener,
) : AbstractController(deviceId, listener, device.vendorId, device.productId) {
    private var inputThread: Thread? = null
    private var stopped = false

    @JvmField protected var inEndpt: UsbEndpoint? = null
    @JvmField protected var outEndpt: UsbEndpoint? = null

    init {
        type = MoonBridge.LI_CTYPE_XBOX
        capabilities =
            (MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt())
                .toShort()
        buttonFlags =
            ControllerPacket.A_FLAG or
                ControllerPacket.B_FLAG or
                ControllerPacket.X_FLAG or
                ControllerPacket.Y_FLAG or
                ControllerPacket.UP_FLAG or
                ControllerPacket.DOWN_FLAG or
                ControllerPacket.LEFT_FLAG or
                ControllerPacket.RIGHT_FLAG or
                ControllerPacket.LB_FLAG or
                ControllerPacket.RB_FLAG or
                ControllerPacket.LS_CLK_FLAG or
                ControllerPacket.RS_CLK_FLAG or
                ControllerPacket.BACK_FLAG or
                ControllerPacket.PLAY_FLAG or
                ControllerPacket.SPECIAL_BUTTON_FLAG
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    private fun createInputThread(): Thread =
        object : Thread() {
            override fun run() {
                try {
                    // Delay for a moment before reporting the new gamepad and
                    // accepting new input. This allows time for the old InputDevice
                    // to go away before we reclaim its spot. If the old device is still
                    // around when we call notifyDeviceAdded(), we won't be able to claim
                    // the controller number used by the original InputDevice.
                    Thread.sleep(STARTUP_DELAY_MS)
                } catch (_: InterruptedException) {
                    return
                }

                // Report that we're added _before_ reporting input
                notifyDeviceAdded()

                while (!isInterrupted && !stopped) {
                    val buffer = ByteArray(READ_BUFFER_SIZE)

                    var res: Int

                    //
                    // There's no way that I can tell to determine if a device has failed
                    // or if the timeout has simply expired. We'll check how long the transfer
                    // took to fail and assume the device failed if it happened before the timeout
                    // expired.
                    //

                    do {
                        // Read the next input state packet
                        val lastMillis = SystemClock.uptimeMillis()
                        res = connection.bulkTransfer(inEndpt, buffer, buffer.size, READ_TIMEOUT_MS)

                        // If we get a zero length response, treat it as an error
                        if (res == 0) {
                            res = -1
                        }

                        if (
                            res == -1 &&
                                SystemClock.uptimeMillis() - lastMillis < IO_ERROR_WINDOW_MS
                        ) {
                            LimeLog.warning("Detected device I/O error")
                            this@AbstractXboxController.stop()
                            break
                        }
                    } while (res == -1 && !isInterrupted && !stopped)

                    if (res == -1 || stopped) {
                        break
                    }

                    if (
                        handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))
                    ) {
                        // Report input if handleRead() returns true
                        reportInput()
                    }
                }
            }
        }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    override fun start(): Boolean {
        // Force claim all interfaces
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim interfaces")
                return false
            }
        }

        // Find the endpoints
        val iface = device.getInterface(0)
        for (i in 0 until iface.endpointCount) {
            val endpt = iface.getEndpoint(i)
            if (endpt.direction == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    LimeLog.warning("Found duplicate IN endpoint")
                    return false
                }
                inEndpt = endpt
            } else if (endpt.direction == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    LimeLog.warning("Found duplicate OUT endpoint")
                    return false
                }
                outEndpt = endpt
            }
        }

        // Make sure the required endpoints were present
        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint")
            return false
        }

        // Run the init function
        if (!doInit()) {
            return false
        }

        // Start listening for controller input
        val thread = createInputThread()
        inputThread = thread
        thread.start()

        return true
    }

    override fun stop() {
        if (stopped) {
            return
        }

        stopped = true

        // Cancel any rumble effects
        rumble(0, 0)

        // Stop the input thread
        inputThread?.let {
            it.interrupt()
            inputThread = null
        }

        // Close the USB connection
        connection.close()

        // Report the device removed
        notifyDeviceRemoved()
    }

    protected abstract fun handleRead(buffer: ByteBuffer): Boolean

    protected abstract fun doInit(): Boolean

    companion object {
        private const val STARTUP_DELAY_MS = 1000L
        private const val READ_BUFFER_SIZE = 64
        private const val READ_TIMEOUT_MS = 3000
        private const val IO_ERROR_WINDOW_MS = 1000L
    }
}
