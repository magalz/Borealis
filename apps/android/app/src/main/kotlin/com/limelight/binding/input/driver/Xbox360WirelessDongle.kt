package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.view.InputDevice
import com.limelight.LimeLog

class Xbox360WirelessDongle(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener,
) : AbstractController(deviceId, listener, device.vendorId, device.productId) {
    // The LED command is 12 bytes: the packet header, the command byte, zeros.
    private fun sendLedCommandToEndpoint(endpoint: UsbEndpoint, controllerIndex: Int) {
        val commandBuffer =
            byteArrayOf(
                0x00,
                0x00,
                LED_COMMAND_LENGTH,
                (LED_COMMAND_BASE + (LED_FIRST_COMMAND + (controllerIndex % LED_SLOT_COUNT)))
                    .toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )

        val res =
            connection.bulkTransfer(endpoint, commandBuffer, commandBuffer.size, LED_TIMEOUT_MS)
        if (res != commandBuffer.size) {
            LimeLog.warning("LED set transfer failed: $res")
        }
    }

    private fun sendLedCommandToInterface(iface: UsbInterface, controllerIndex: Int) {
        // Claim this interface to kick xpad off it (temporarily)
        if (!connection.claimInterface(iface, true)) {
            LimeLog.warning("Failed to claim interface: " + iface.id)
            return
        }

        // Find the out endpoint for this interface
        for (i in 0 until iface.endpointCount) {
            val endpt = iface.getEndpoint(i)
            if (endpt.direction == UsbConstants.USB_DIR_OUT) {
                // Send the LED command
                sendLedCommandToEndpoint(endpt, controllerIndex)
                break
            }
        }

        // Release the interface to allow xpad to take over again
        connection.releaseInterface(iface)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ComplexCondition", "LoopWithTooManyJumpStatements")
    override fun start(): Boolean {
        var controllerIndex = 0

        // On Android, there is a controller number associated with input devices.
        // We can use this to approximate the likely controller number. This won't
        // be completely accurate because there's no guarantee the order of interfaces
        // matches the order that devices were enumerated by xpad, but it's probably
        // better than nothing.
        for (id in InputDevice.getDeviceIds()) {
            // Device was removed while looping
            val inputDev = InputDevice.getDevice(id) ?: continue

            // Newer xpad versions use a special product ID (0x02a1) for controllers
            // rather than copying the product ID of the dongle itself.
            if (
                inputDev.vendorId == device.vendorId &&
                    (inputDev.productId == device.productId ||
                        inputDev.productId == NEWER_XPAD_PRODUCT_ID) &&
                    inputDev.controllerNumber > 0
            ) {
                controllerIndex = inputDev.controllerNumber - 1
                break
            }
        }

        // Send LED commands on the out endpoint of each interface. There is one interface
        // corresponding to each possible attached controller.
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            // Skip the non-input interfaces
            if (
                iface.interfaceClass != UsbConstants.USB_CLASS_VENDOR_SPEC ||
                    iface.interfaceSubclass != XB360W_IFACE_SUBCLASS ||
                    iface.interfaceProtocol != XB360W_IFACE_PROTOCOL
            ) {
                continue
            }

            sendLedCommandToInterface(iface, controllerIndex++)
        }

        // "Fail" to give control back to the kernel driver
        return false
    }

    override fun stop() {
        // Nothing to do
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        // Unreachable.
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        // Unreachable.
    }

    companion object {
        private const val XB360W_IFACE_SUBCLASS = 93
        private const val XB360W_IFACE_PROTOCOL = 129 // Wireless only

        private val SUPPORTED_VENDORS =
            intArrayOf(
                0x045e // Microsoft
            )

        private const val NEWER_XPAD_PRODUCT_ID = 0x02a1
        private const val LED_COMMAND_LENGTH: Byte = 0x08
        private const val LED_COMMAND_BASE = 0x40
        private const val LED_FIRST_COMMAND = 2
        private const val LED_SLOT_COUNT = 4
        private const val LED_TIMEOUT_MS = 3000

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ComplexCondition")
        @JvmStatic
        fun canClaimDevice(device: UsbDevice): Boolean {
            for (supportedVid in SUPPORTED_VENDORS) {
                if (
                    device.vendorId == supportedVid &&
                        device.interfaceCount >= 1 &&
                        device.getInterface(0).interfaceClass ==
                            UsbConstants.USB_CLASS_VENDOR_SPEC &&
                        device.getInterface(0).interfaceSubclass == XB360W_IFACE_SUBCLASS &&
                        device.getInterface(0).interfaceProtocol == XB360W_IFACE_PROTOCOL
                ) {
                    return true
                }
            }

            return false
        }
    }
}
