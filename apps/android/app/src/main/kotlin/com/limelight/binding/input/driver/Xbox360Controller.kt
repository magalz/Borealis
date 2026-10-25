package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.limelight.LimeLog
import com.limelight.nvstream.input.ControllerPacket
import java.nio.ByteBuffer

class Xbox360Controller(
    device: UsbDevice,
    connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener,
) : AbstractXboxController(device, connection, deviceId, listener) {
    private fun unsignByte(b: Byte): Int {
        return if (b < 0) {
            b + UNSIGNED_BYTE_RANGE
        } else {
            b.toInt()
        }
    }

    override fun handleRead(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < MIN_REPORT_SIZE) {
            LimeLog.severe("Read too small: " + buffer.remaining())
            return false
        }

        // Skip first short
        buffer.position(buffer.position() + 2)

        // DPAD
        var b = buffer.get().toInt()
        setButtonFlag(ControllerPacket.LEFT_FLAG, b and DPAD_LEFT_BIT)
        setButtonFlag(ControllerPacket.RIGHT_FLAG, b and DPAD_RIGHT_BIT)
        setButtonFlag(ControllerPacket.UP_FLAG, b and DPAD_UP_BIT)
        setButtonFlag(ControllerPacket.DOWN_FLAG, b and DPAD_DOWN_BIT)

        // Start/Select
        setButtonFlag(ControllerPacket.PLAY_FLAG, b and START_BIT)
        setButtonFlag(ControllerPacket.BACK_FLAG, b and BACK_BIT)

        // LS/RS
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b and LEFT_STICK_CLICK_BIT)
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b and RIGHT_STICK_CLICK_BIT)

        // ABXY buttons
        b = buffer.get().toInt()
        setButtonFlag(ControllerPacket.A_FLAG, b and A_BIT)
        setButtonFlag(ControllerPacket.B_FLAG, b and B_BIT)
        setButtonFlag(ControllerPacket.X_FLAG, b and X_BIT)
        setButtonFlag(ControllerPacket.Y_FLAG, b and Y_BIT)

        // LB/RB
        setButtonFlag(ControllerPacket.LB_FLAG, b and LEFT_BUMPER_BIT)
        setButtonFlag(ControllerPacket.RB_FLAG, b and RIGHT_BUMPER_BIT)

        // Xbox button
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, b and GUIDE_BIT)

        // Triggers
        leftTrigger = unsignByte(buffer.get()) / TRIGGER_MAX
        rightTrigger = unsignByte(buffer.get()) / TRIGGER_MAX

        // Left stick
        leftStickX = buffer.getShort() / STICK_MAX
        leftStickY = buffer.getShort().toInt().inv() / STICK_MAX

        // Right stick
        rightStickX = buffer.getShort() / STICK_MAX
        rightStickY = buffer.getShort().toInt().inv() / STICK_MAX

        // Return true to send input
        return true
    }

    private fun sendLedCommand(command: Byte): Boolean {
        val commandBuffer = byteArrayOf(LED_REPORT_ID, LED_REPORT_SIZE, command)

        val res =
            connection.bulkTransfer(
                checkNotNull(outEndpt),
                commandBuffer,
                commandBuffer.size,
                LED_TIMEOUT_MS,
            )
        if (res != commandBuffer.size) {
            LimeLog.warning("LED set transfer failed: $res")
            return false
        }

        return true
    }

    override fun doInit(): Boolean {
        // Turn the LED on corresponding to our device ID
        sendLedCommand((LED_FIRST_COMMAND + (controllerId % LED_SLOT_COUNT)).toByte())

        // No need to fail init if the LED command fails
        return true
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        val data =
            byteArrayOf(
                0x00,
                RUMBLE_REPORT_SIZE,
                0x00,
                (lowFreqMotor.toInt() shr MOTOR_BYTE_SHIFT).toByte(),
                (highFreqMotor.toInt() shr MOTOR_BYTE_SHIFT).toByte(),
                0x00,
                0x00,
                0x00,
            )
        val res =
            connection.bulkTransfer(checkNotNull(outEndpt), data, data.size, RUMBLE_TIMEOUT_MS)
        if (res != data.size) {
            LimeLog.warning("Rumble transfer failed: $res")
        }
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        // Trigger motors not present on Xbox 360 controllers
    }

    companion object {
        private const val XB360_IFACE_SUBCLASS = 93
        private const val XB360_IFACE_PROTOCOL = 1 // Wired only

        private val SUPPORTED_VENDORS =
            intArrayOf(
                0x0079, // GPD Win 2
                0x044f, // Thrustmaster
                0x045e, // Microsoft
                0x046d, // Logitech
                0x056e, // Elecom
                0x06a3, // Saitek
                0x0738, // Mad Catz
                0x07ff, // Mad Catz
                0x0e6f, // Unknown
                0x0f0d, // Hori
                0x1038, // SteelSeries
                0x11c9, // Nacon
                0x1209, // Ardwiino
                0x12ab, // Unknown
                0x1430, // RedOctane
                0x146b, // BigBen
                0x1532, // Razer Sabertooth
                0x15e4, // Numark
                0x162e, // Joytech
                0x1689, // Razer Onza
                0x1949, // Lab126 (Amazon Luna)
                0x1bad, // Harmonix
                0x20d6, // PowerA
                0x24c6, // PowerA
                0x2f24, // GameSir
                0x2dc8, // 8BitDo
            )

        // The report: a first short that the driver skips, then these.
        private const val MIN_REPORT_SIZE = 14
        private const val UNSIGNED_BYTE_RANGE = 256
        private const val TRIGGER_MAX = 255.0f
        private const val STICK_MAX = 32767.0f

        // The bits of the first button byte.
        private const val DPAD_UP_BIT = 0x01
        private const val DPAD_DOWN_BIT = 0x02
        private const val DPAD_LEFT_BIT = 0x04
        private const val DPAD_RIGHT_BIT = 0x08
        private const val START_BIT = 0x10
        private const val BACK_BIT = 0x20
        private const val LEFT_STICK_CLICK_BIT = 0x40
        private const val RIGHT_STICK_CLICK_BIT = 0x80

        // The bits of the second button byte (0x08 has no button).
        private const val LEFT_BUMPER_BIT = 0x01
        private const val RIGHT_BUMPER_BIT = 0x02
        private const val GUIDE_BIT = 0x04
        private const val A_BIT = 0x10
        private const val B_BIT = 0x20
        private const val X_BIT = 0x40
        private const val Y_BIT = 0x80

        // The LED and rumble packets.
        private const val LED_REPORT_ID: Byte = 0x01
        private const val LED_REPORT_SIZE: Byte = 0x03
        private const val LED_FIRST_COMMAND = 2
        private const val LED_SLOT_COUNT = 4
        private const val LED_TIMEOUT_MS = 3000
        private const val RUMBLE_REPORT_SIZE: Byte = 0x08
        private const val MOTOR_BYTE_SHIFT = 8
        private const val RUMBLE_TIMEOUT_MS = 100

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
                        device.getInterface(0).interfaceSubclass == XB360_IFACE_SUBCLASS &&
                        device.getInterface(0).interfaceProtocol == XB360_IFACE_PROTOCOL
                ) {
                    return true
                }
            }

            return false
        }
    }
}
