package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.limelight.LimeLog
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import java.nio.ByteBuffer

class XboxOneController(
    device: UsbDevice,
    connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener,
) : AbstractXboxController(device, connection, deviceId, listener) {
    private var seqNum: Byte = 0
    private var lowFreqMotor: Short = 0
    private var highFreqMotor: Short = 0
    private var leftTriggerMotor: Short = 0
    private var rightTriggerMotor: Short = 0

    init {
        capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt()).toShort()
    }

    private fun processButtons(buffer: ByteBuffer) {
        var b = buffer.get().toInt()

        setButtonFlag(ControllerPacket.PLAY_FLAG, b and PLAY_BIT)
        setButtonFlag(ControllerPacket.BACK_FLAG, b and BACK_BIT)

        setButtonFlag(ControllerPacket.A_FLAG, b and A_BIT)
        setButtonFlag(ControllerPacket.B_FLAG, b and B_BIT)
        setButtonFlag(ControllerPacket.X_FLAG, b and X_BIT)
        setButtonFlag(ControllerPacket.Y_FLAG, b and Y_BIT)

        b = buffer.get().toInt()
        setButtonFlag(ControllerPacket.LEFT_FLAG, b and DPAD_LEFT_BIT)
        setButtonFlag(ControllerPacket.RIGHT_FLAG, b and DPAD_RIGHT_BIT)
        setButtonFlag(ControllerPacket.UP_FLAG, b and DPAD_UP_BIT)
        setButtonFlag(ControllerPacket.DOWN_FLAG, b and DPAD_DOWN_BIT)

        setButtonFlag(ControllerPacket.LB_FLAG, b and LEFT_BUMPER_BIT)
        setButtonFlag(ControllerPacket.RB_FLAG, b and RIGHT_BUMPER_BIT)

        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b and LEFT_STICK_CLICK_BIT)
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b and RIGHT_STICK_CLICK_BIT)

        leftTrigger = buffer.getShort() / TRIGGER_MAX
        rightTrigger = buffer.getShort() / TRIGGER_MAX

        leftStickX = buffer.getShort() / STICK_MAX
        leftStickY = buffer.getShort().toInt().inv() / STICK_MAX

        rightStickX = buffer.getShort() / STICK_MAX
        rightStickY = buffer.getShort().toInt().inv() / STICK_MAX
    }

    private fun ackModeReport(seqNum: Byte) {
        val payload =
            byteArrayOf(
                0x01,
                PACKET_FLAGS,
                seqNum,
                ACK_LENGTH,
                0x00,
                MODE_REPORT_TYPE,
                PACKET_FLAGS,
                ACK_STATUS,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )
        connection.bulkTransfer(checkNotNull(outEndpt), payload, payload.size, ACK_TIMEOUT_MS)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    override fun handleRead(buffer: ByteBuffer): Boolean {
        when (buffer.get().toInt()) {
            BUTTONS_REPORT -> {
                if (buffer.remaining() < BUTTONS_REPORT_MIN_SIZE) {
                    LimeLog.severe("XBone button/axis read too small: " + buffer.remaining())
                    return false
                }

                buffer.position(buffer.position() + BUTTONS_REPORT_SKIP)
                processButtons(buffer)
                return true
            }
            MODE_REPORT -> {
                if (buffer.remaining() < MODE_REPORT_MIN_SIZE) {
                    LimeLog.severe("XBone mode read too small: " + buffer.remaining())
                    return false
                }

                // The Xbox One S controller needs acks for mode reports otherwise
                // it retransmits them forever.
                if (buffer.get().toInt() == MODE_REPORT_ACK_FLAGS) {
                    ackModeReport(buffer.get())
                    buffer.position(buffer.position() + 1)
                } else {
                    buffer.position(buffer.position() + 2)
                }
                setButtonFlag(
                    ControllerPacket.SPECIAL_BUTTON_FLAG,
                    buffer.get().toInt() and GUIDE_BIT,
                )
                return true
            }
        }

        return false
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
    override fun doInit(): Boolean {
        // Send all applicable init packets
        for (pkt in INIT_PKTS) {
            if (pkt.vendorId != 0 && device.vendorId != pkt.vendorId) {
                continue
            }

            if (pkt.productId != 0 && device.productId != pkt.productId) {
                continue
            }

            val data = pkt.data.copyOf(pkt.data.size)

            // Populate sequence number
            data[2] = seqNum++

            // Send the initialization packet
            val res =
                connection.bulkTransfer(checkNotNull(outEndpt), data, data.size, INIT_TIMEOUT_MS)
            if (res != data.size) {
                LimeLog.warning("Initialization transfer failed: $res")
                return false
            }
        }

        return true
    }

    private fun sendRumblePacket() {
        val data =
            byteArrayOf(
                RUMBLE_REPORT_ID,
                0x00,
                seqNum++,
                RUMBLE_REPORT_LENGTH,
                0x00,
                RUMBLE_MOTOR_MASK,
                (leftTriggerMotor.toInt() shr MOTOR_SHIFT).toByte(),
                (rightTriggerMotor.toInt() shr MOTOR_SHIFT).toByte(),
                (lowFreqMotor.toInt() shr MOTOR_SHIFT).toByte(),
                (highFreqMotor.toInt() shr MOTOR_SHIFT).toByte(),
                RUMBLE_FULL,
                0x00,
                RUMBLE_FULL,
            )
        val res =
            connection.bulkTransfer(checkNotNull(outEndpt), data, data.size, RUMBLE_TIMEOUT_MS)
        if (res != data.size) {
            LimeLog.warning("Rumble transfer failed: $res")
        }
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        this.lowFreqMotor = lowFreqMotor
        this.highFreqMotor = highFreqMotor
        sendRumblePacket()
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        this.leftTriggerMotor = leftTrigger
        this.rightTriggerMotor = rightTrigger
        sendRumblePacket()
    }

    private class InitPacket(val vendorId: Int, val productId: Int, val data: ByteArray)

    companion object {
        private const val XB1_IFACE_SUBCLASS = 71
        private const val XB1_IFACE_PROTOCOL = 208

        private val SUPPORTED_VENDORS =
            intArrayOf(
                0x045e, // Microsoft
                0x0738, // Mad Catz
                0x0e6f, // Unknown
                0x0f0d, // Hori
                0x1532, // Razer Wildcat
                0x20d6, // PowerA
                0x24c6, // PowerA
                0x2e24, // Hyperkin
                0x2dc8, // 8BitDo
            )

        private val FW2015_INIT = byteArrayOf(0x05, 0x20, 0x00, 0x01, 0x00)
        private val ONE_S_INIT = byteArrayOf(0x05, 0x20, 0x00, 0x0f, 0x06)
        private val SERIES_S_INIT = byteArrayOf(0x05, 0x20, 0x00, 0x0f, 0x06)
        private val HORI_INIT =
            byteArrayOf(
                0x01,
                0x20,
                0x00,
                0x09,
                0x00,
                0x04,
                0x20,
                0x3a,
                0x00,
                0x00,
                0x00,
                0x80.toByte(),
                0x00,
            )
        private val PDP_INIT1 = byteArrayOf(0x0a, 0x20, 0x00, 0x03, 0x00, 0x01, 0x14)
        private val PDP_INIT2 = byteArrayOf(0x06, 0x20, 0x00, 0x02, 0x01, 0x00)
        private val RUMBLE_INIT1 =
            byteArrayOf(
                0x09,
                0x00,
                0x00,
                0x09,
                0x00,
                0x0F,
                0x00,
                0x00,
                0x1D,
                0x1D,
                0xFF.toByte(),
                0x00,
                0x00,
            )
        private val RUMBLE_INIT2 =
            byteArrayOf(
                0x09,
                0x00,
                0x00,
                0x09,
                0x00,
                0x0F,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )

        private val INIT_PKTS =
            arrayOf(
                InitPacket(0x0e6f, 0x0165, HORI_INIT),
                InitPacket(0x0f0d, 0x0067, HORI_INIT),
                InitPacket(0x0000, 0x0000, FW2015_INIT),
                InitPacket(0x045e, 0x02ea, ONE_S_INIT),
                InitPacket(0x045e, 0x02fe, ONE_S_INIT),
                InitPacket(0x045e, 0x0b00, ONE_S_INIT),
                InitPacket(0x045e, 0x0b05, SERIES_S_INIT),
                InitPacket(0x045e, 0x0b12, SERIES_S_INIT),
                InitPacket(0x045e, 0x0b13, SERIES_S_INIT),
                InitPacket(0x0e6f, 0x0000, PDP_INIT1),
                InitPacket(0x0e6f, 0x0000, PDP_INIT2),
                InitPacket(0x24c6, 0x541a, RUMBLE_INIT1),
                InitPacket(0x24c6, 0x542a, RUMBLE_INIT1),
                InitPacket(0x24c6, 0x543a, RUMBLE_INIT1),
                InitPacket(0x24c6, 0x541a, RUMBLE_INIT2),
                InitPacket(0x24c6, 0x542a, RUMBLE_INIT2),
                InitPacket(0x24c6, 0x543a, RUMBLE_INIT2),
            )

        // The reports of the pad: the type byte, then these.
        private const val BUTTONS_REPORT = 0x20
        private const val BUTTONS_REPORT_MIN_SIZE = 17
        private const val BUTTONS_REPORT_SKIP = 3
        private const val MODE_REPORT = 0x07
        private const val MODE_REPORT_MIN_SIZE = 4
        private const val MODE_REPORT_ACK_FLAGS = 0x30
        private const val GUIDE_BIT = 0x01
        private const val TRIGGER_MAX = 1023.0f
        private const val STICK_MAX = 32767.0f

        // The bits of the first button byte.
        private const val PLAY_BIT = 0x04
        private const val BACK_BIT = 0x08
        private const val A_BIT = 0x10
        private const val B_BIT = 0x20
        private const val X_BIT = 0x40
        private const val Y_BIT = 0x80

        // The bits of the second button byte.
        private const val DPAD_UP_BIT = 0x01
        private const val DPAD_DOWN_BIT = 0x02
        private const val DPAD_LEFT_BIT = 0x04
        private const val DPAD_RIGHT_BIT = 0x08
        private const val LEFT_BUMPER_BIT = 0x10
        private const val RIGHT_BUMPER_BIT = 0x20
        private const val LEFT_STICK_CLICK_BIT = 0x40
        private const val RIGHT_STICK_CLICK_BIT = 0x80

        // The packets the driver sends.
        private const val PACKET_FLAGS: Byte = 0x20
        private const val ACK_LENGTH: Byte = 0x09
        private const val ACK_STATUS: Byte = 0x02
        private const val MODE_REPORT_TYPE: Byte = 0x07
        private const val RUMBLE_REPORT_ID: Byte = 0x09
        private const val RUMBLE_REPORT_LENGTH: Byte = 0x09
        private const val RUMBLE_MOTOR_MASK: Byte = 0x0F
        private const val RUMBLE_FULL: Byte = 0xFF.toByte()
        private const val MOTOR_SHIFT = 9
        private const val INIT_TIMEOUT_MS = 3000
        private const val ACK_TIMEOUT_MS = 3000
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
                        device.getInterface(0).interfaceSubclass == XB1_IFACE_SUBCLASS &&
                        device.getInterface(0).interfaceProtocol == XB1_IFACE_PROTOCOL
                ) {
                    return true
                }
            }

            return false
        }
    }
}
