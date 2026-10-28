package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbInterface
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.LimeLogCapture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes the wireless dongle driver. It never keeps a pad: `start` lights the LED of each
 * wireless interface and then reports failure, so that the service closes the connection and the
 * kernel driver takes the dongle back.
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
class Xbox360WirelessDongleTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val log = LimeLogCapture()

    private fun wireless(id: Int, outNumber: Int = id + 1, withOut: Boolean = true): UsbInterface =
        UsbPads.usbInterface(
            id,
            subclass = UsbPads.XB360_SUBCLASS,
            protocol = UsbPads.XB360W_PROTOCOL,
            endpoints =
                listOfNotNull(
                    UsbPads.inEndpoint(id + 1),
                    UsbPads.outEndpoint(outNumber).takeIf { withOut },
                ),
        )

    private fun dongle(vararg interfaces: UsbInterface, id: Int = 0): Pad<Xbox360WirelessDongle> =
        Pad(UsbPads.dongle(interfaces = interfaces.toList())) { device, connection, listener ->
            Xbox360WirelessDongle(device, connection, id, listener)
        }

    private fun led(slot: Int) = listOf(0x00, 0x00, 0x08, slot, 0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun `the dongle is no pad it describes to the host`() {
        val pad = dongle(wireless(0), id = 3)

        assertThat(pad.controller.viaGetter("getControllerId")).isEqualTo(3)
        assertThat(pad.controller.viaGetter("getVendorId")).isEqualTo(0x045E)
        assertThat(pad.controller.viaGetter("getProductId")).isEqualTo(0x0291)
        assertThat(pad.controller.viaGetter("getType")).isEqualTo(0.toByte())
        assertThat(pad.controller.viaGetter("getCapabilities")).isEqualTo(0.toShort())
        assertThat(pad.controller.viaGetter("getSupportedButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `start lights the LED of each wireless interface and fails so the kernel takes it back`() {
        val first = wireless(0)
        val second = wireless(2)
        val inputless = wireless(4, withOut = false)
        val third = wireless(5)
        val pad =
            dongle(
                first,
                // Not wireless input interfaces: another class, another subclass, another protocol.
                UsbPads.usbInterface(
                    1,
                    UsbConstants.USB_CLASS_HID,
                    UsbPads.XB360_SUBCLASS,
                    UsbPads.XB360W_PROTOCOL,
                ),
                second,
                UsbPads.usbInterface(3, UsbPads.VENDOR_SPECIFIC, 94, UsbPads.XB360W_PROTOCOL),
                inputless,
                third,
                UsbPads.usbInterface(6, UsbPads.VENDOR_SPECIFIC, UsbPads.XB360_SUBCLASS, 1),
            )

        assertThat(pad.start()).isFalse()

        // Each wireless interface is claimed by force, written and released, in turn; one without
        // an OUT endpoint is only claimed and released.
        assertThat(pad.usb.claims.map { it.usbInterface.id }).containsExactly(0, 2, 4, 5).inOrder()
        assertThat(pad.usb.claims.map { it.force }.toSet()).containsExactly(true)
        assertThat(pad.usb.releases.map { it.id }).containsExactly(0, 2, 4, 5).inOrder()
        assertThat(pad.writtenBytes()).containsExactly(led(0x42), led(0x43), led(0x45)).inOrder()
        assertThat(pad.usb.writes.map { it.endpoint.address })
            .containsExactly(0x01, 0x03, 0x06)
            .inOrder()
        assertThat(pad.usb.writes.map { it.timeoutMs }.toSet()).containsExactly(3000)
        // The dongle does not close the connection: the service does.
        assertThat(pad.usb.closes).isEqualTo(0)
    }

    @Test
    fun `an interface that cannot be claimed gets no LED but still counts its slot`() {
        val pad = dongle(wireless(0), wireless(1), wireless(2))
        pad.usb.claimResults.addAll(listOf(true, false, true))

        pad.start()

        assertThat(pad.writtenBytes()).containsExactly(led(0x42), led(0x44)).inOrder()
        assertThat(pad.usb.releases.map { it.id }).containsExactly(0, 2).inOrder()
        assertThat(log.warnings()).containsExactly("Failed to claim interface: 1")
    }

    @Test
    fun `only the first OUT endpoint of an interface gets the LED command`() {
        val twoOut =
            UsbPads.usbInterface(
                0,
                subclass = UsbPads.XB360_SUBCLASS,
                protocol = UsbPads.XB360W_PROTOCOL,
                endpoints =
                    listOf(UsbPads.outEndpoint(1), UsbPads.inEndpoint(1), UsbPads.outEndpoint(2)),
            )
        val pad = dongle(twoOut)

        pad.start()

        assertThat(pad.usb.writes.map { it.endpoint.address }).containsExactly(0x01)
    }

    @Test
    fun `a failed LED transfer is only logged`() {
        val pad = dongle(wireless(0), wireless(1))
        pad.usb.writeResults.add(-1)

        assertThat(pad.start()).isFalse()

        assertThat(pad.usb.writes).hasSize(2)
        assertThat(pad.usb.releases).hasSize(2)
        assertThat(log.warnings()).containsExactly("LED set transfer failed: -1")
    }

    @Test
    fun `the first slot follows the controller number Android gave the dongle's pad`() {
        // Android numbers its controllers from one; the driver numbers its LEDs from zero.
        PadInputDevices.add(0x045E, 0x0291, controllerNumber = 3)
        val pad = dongle(wireless(0), wireless(1), wireless(2))

        pad.start()

        // Slots 2, 3 and 4: the LED command adds 2 to the slot and wraps it after four.
        assertThat(pad.writtenBytes().map { it[3] }).containsExactly(0x44, 0x45, 0x42).inOrder()
    }

    @Test
    fun `a pad of the product 0x02a1 and the dongle's vendor counts too`() {
        PadInputDevices.add(0x045E, 0x02A1, controllerNumber = 2)
        val pad = dongle(wireless(0))

        pad.start()

        assertThat(pad.writtenBytes().single()[3]).isEqualTo(0x43)
    }

    @Test
    fun `an input device of another vendor or product, or with no number, is not the slot`() {
        PadInputDevices.add(0x046D, 0x0291, controllerNumber = 4)
        PadInputDevices.add(0x045E, 0x0001, controllerNumber = 4)
        PadInputDevices.add(0x045E, 0x0291, controllerNumber = 0)
        val pad = dongle(wireless(0))

        pad.start()

        assertThat(pad.writtenBytes().single()[3]).isEqualTo(0x42)
    }

    @Test
    fun `the first matching input device sets the slot`() {
        PadInputDevices.add(0x045E, 0x0291, controllerNumber = 2)
        PadInputDevices.add(0x045E, 0x0291, controllerNumber = 4)
        val pad = dongle(wireless(0))

        pad.start()

        assertThat(pad.writtenBytes().single()[3]).isEqualTo(0x43)
    }

    @Test
    fun `stop and rumble do nothing`() {
        val pad = dongle(wireless(0))
        pad.start()
        val writes = pad.usb.writes.size

        pad.controller.stop()
        pad.controller.rumble(0x7FFF, 0x7FFF)
        pad.controller.rumbleTriggers(0x7FFF, 0x7FFF)

        assertThat(pad.usb.writes).hasSize(writes)
        assertThat(pad.usb.closes).isEqualTo(0)
        assertThat(pad.listener.quietFor(50)).isTrue()
    }
}
