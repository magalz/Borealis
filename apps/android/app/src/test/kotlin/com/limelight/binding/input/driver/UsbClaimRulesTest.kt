package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.view.InputDevice
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which USB devices the drivers take: the vendor lists and interface numbers each driver matches,
 * and the rules that decide whether the app claims a device Android may already handle. The kernel
 * checks read `os.version`, so the tests set it and put it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class, ShadowScriptedUsbConnection::class])
class UsbClaimRulesTest {
    @get:Rule val scriptedUsb = ScriptedUsbReset()

    @get:Rule val log = LimeLogCapture()

    private val savedKernel: String? = System.getProperty("os.version")

    @After
    fun restoreKernel() {
        if (savedKernel == null) System.clearProperty("os.version")
        else System.setProperty("os.version", savedKernel)
    }

    private fun kernel(version: String?) {
        if (version == null) System.clearProperty("os.version")
        else System.setProperty("os.version", version)
    }

    private fun xbox360(vendorId: Int = 0x045E) = UsbPads.xbox360Pad(vendorId = vendorId)

    private fun xboxOne(vendorId: Int = 0x045E) = UsbPads.xboxOnePad(vendorId = vendorId)

    private fun dongle(vendorId: Int = 0x045E) = UsbPads.dongle(vendorId = vendorId)

    private fun claimable(
        family: (UsbDevice) -> Boolean,
        build: (Int) -> UsbDevice,
        vendors: List<Int>,
    ) = vendors.filter { family(build(it)) }

    private val xbox360Vendors =
        listOf(
            0x0079,
            0x044F,
            0x045E,
            0x046D,
            0x056E,
            0x06A3,
            0x0738,
            0x07FF,
            0x0E6F,
            0x0F0D,
            0x1038,
            0x11C9,
            0x1209,
            0x12AB,
            0x1430,
            0x146B,
            0x1532,
            0x15E4,
            0x162E,
            0x1689,
            0x1949,
            0x1BAD,
            0x20D6,
            0x24C6,
            0x2F24,
            0x2DC8,
        )

    private val xboxOneVendors =
        listOf(0x045E, 0x0738, 0x0E6F, 0x0F0D, 0x1532, 0x20D6, 0x24C6, 0x2E24, 0x2DC8)

    private val neighbours = listOf(0x0000, 0x0078, 0x1234, 0x2E25, 0x2F25, 0xFFFF)

    @Test
    fun `the Xbox 360 driver claims a wired pad of each vendor it lists`() {
        val all = xbox360Vendors + neighbours + 0x2E24 // 0x2E24 is the One vendor Hyperkin
        assertThat(claimable({ Xbox360Controller.canClaimDevice(it) }, ::xbox360, all.distinct()))
            .containsExactlyElementsIn(xbox360Vendors)
    }

    @Test
    fun `the Xbox One driver claims a pad of each vendor it lists`() {
        val all = xboxOneVendors + neighbours + 0x2F24 // 0x2F24 is the 360 vendor GameSir
        assertThat(claimable({ XboxOneController.canClaimDevice(it) }, ::xboxOne, all.distinct()))
            .containsExactlyElementsIn(xboxOneVendors)
    }

    @Test
    fun `the dongle driver claims only the Microsoft dongle`() {
        val all = listOf(0x045E) + neighbours + 0x046D + 0x0738
        assertThat(
                claimable({ Xbox360WirelessDongle.canClaimDevice(it) }, ::dongle, all.distinct())
            )
            .containsExactly(0x045E)
    }

    @Test
    fun `each driver matches the class, subclass and protocol of the first interface`() {
        fun first(usbClass: Int, subclass: Int, protocol: Int) =
            UsbPads.device(
                "/dev/x",
                0x045E,
                0x0001,
                listOf(UsbPads.usbInterface(0, usbClass, subclass, protocol)),
            )
        val vendorSpecific = UsbConstants.USB_CLASS_VENDOR_SPEC
        val hid = UsbConstants.USB_CLASS_HID

        // 360: 255/93/1, One: 255/71/208, dongle: 255/93/129.
        assertThat(Xbox360Controller.canClaimDevice(first(vendorSpecific, 93, 1))).isTrue()
        assertThat(Xbox360Controller.canClaimDevice(first(hid, 93, 1))).isFalse()
        assertThat(Xbox360Controller.canClaimDevice(first(vendorSpecific, 94, 1))).isFalse()
        assertThat(Xbox360Controller.canClaimDevice(first(vendorSpecific, 93, 2))).isFalse()
        assertThat(Xbox360Controller.canClaimDevice(first(vendorSpecific, 93, 129))).isFalse()
        assertThat(Xbox360Controller.canClaimDevice(first(vendorSpecific, 71, 208))).isFalse()

        assertThat(XboxOneController.canClaimDevice(first(vendorSpecific, 71, 208))).isTrue()
        assertThat(XboxOneController.canClaimDevice(first(hid, 71, 208))).isFalse()
        assertThat(XboxOneController.canClaimDevice(first(vendorSpecific, 72, 208))).isFalse()
        assertThat(XboxOneController.canClaimDevice(first(vendorSpecific, 71, 209))).isFalse()
        assertThat(XboxOneController.canClaimDevice(first(vendorSpecific, 93, 1))).isFalse()

        assertThat(Xbox360WirelessDongle.canClaimDevice(first(vendorSpecific, 93, 129))).isTrue()
        assertThat(Xbox360WirelessDongle.canClaimDevice(first(hid, 93, 129))).isFalse()
        assertThat(Xbox360WirelessDongle.canClaimDevice(first(vendorSpecific, 92, 129))).isFalse()
        assertThat(Xbox360WirelessDongle.canClaimDevice(first(vendorSpecific, 93, 128))).isFalse()
        assertThat(Xbox360WirelessDongle.canClaimDevice(first(vendorSpecific, 93, 1))).isFalse()
    }

    @Test
    fun `only the first interface decides`() {
        val matching =
            UsbPads.usbInterface(
                1,
                subclass = UsbPads.XB360_SUBCLASS,
                protocol = UsbPads.XB360_PROTOCOL,
            )
        val other = UsbPads.usbInterface(0, subclass = 0, protocol = 0)
        val device = UsbPads.device("/dev/x", 0x045E, 0x028E, listOf(other, matching))

        assertThat(Xbox360Controller.canClaimDevice(device)).isFalse()
    }

    @Test
    fun `a device with no interface is claimed by no driver`() {
        val device = UsbPads.device("/dev/x", 0x045E, 0x028E, emptyList())

        assertThat(Xbox360Controller.canClaimDevice(device)).isFalse()
        assertThat(XboxOneController.canClaimDevice(device)).isFalse()
        assertThat(Xbox360WirelessDongle.canClaimDevice(device)).isFalse()
    }

    @Test
    fun `the Xbox One kernel check`() {
        val verdicts =
            listOf(
                    null,
                    "2.6.32",
                    "3.10.0",
                    "4.4.0",
                    "4.9.186",
                    "4.14.0",
                    "4.19.5",
                    "5.4.0",
                    "10.0.26200",
                    "4.4",
                    "4.40.1",
                    "20.1",
                    "3",
                )
                .associateWith {
                    kernel(it)
                    UsbDriverService.kernelSupportsXboxOne()
                }

        assertThat(verdicts.filterValues { !it }.keys)
            .containsExactly("2.6.32", "3.10.0", "4.4.0", "4.9.186")
        // Each call logs the version it read.
        assertThat(log.entries.map { it.second })
            .containsExactlyElementsIn(verdicts.keys.map { "Kernel Version: $it" })
            .inOrder()
        assertThat(log.entries.map { it.first }.toSet())
            .containsExactly(java.util.logging.Level.INFO)
    }

    @Test
    fun `the Xbox 360 wireless kernel check`() {
        val verdicts =
            listOf(
                    null,
                    "2.6.32",
                    "3.10.0",
                    "4.0.5",
                    "4.1.15",
                    "4.2.0",
                    "4.10.1",
                    "4.14.0",
                    "5.4.0",
                    "10.0.26200",
                    "4.1",
                    "20.1",
                )
                .associateWith {
                    kernel(it)
                    UsbDriverService.kernelSupportsXbox360W()
                }

        assertThat(verdicts.filterValues { !it }.keys)
            .containsExactly("2.6.32", "3.10.0", "4.0.5", "4.1.15")
    }

    @Test
    fun `a gamepad Android shows with the vendor and product of the device is recognized`() {
        PadInputDevices.add(0x045E, 0x028E, PadInputDevices.GAMEPAD)

        assertThat(UsbDriverService.isRecognizedInputDevice(xbox360())).isTrue()
    }

    @Test
    fun `nothing is recognized without an input device, or with another product, or no gamepad`() {
        assertThat(UsbDriverService.isRecognizedInputDevice(xbox360())).isFalse()

        PadInputDevices.add(0x045E, 0x0001, PadInputDevices.GAMEPAD)
        assertThat(UsbDriverService.isRecognizedInputDevice(xbox360())).isFalse()

        // The keyboard and mouse interface of a dongle shows the same vendor and product.
        PadInputDevices.add(0x045E, 0x028E, PadInputDevices.KEYBOARD_AND_MOUSE)
        assertThat(UsbDriverService.isRecognizedInputDevice(xbox360())).isFalse()
    }

    @Test
    fun `an Xbox One pad is claimed unless Android handles it on a kernel that is fine for it`() {
        kernel("5.10.0")
        assertThat(UsbDriverService.shouldClaimDevice(xboxOne(), false)).isTrue()

        PadInputDevices.add(0x045E, 0x02EA, PadInputDevices.GAMEPAD)
        assertThat(UsbDriverService.shouldClaimDevice(xboxOne(), false)).isFalse()
        assertThat(UsbDriverService.shouldClaimDevice(xboxOne(), true)).isTrue()

        // An old kernel has no good Xbox One support: the app claims it whatever Android says.
        kernel("4.9.186")
        assertThat(UsbDriverService.shouldClaimDevice(xboxOne(), false)).isTrue()
    }

    @Test
    fun `an Xbox 360 pad is claimed unless Android handles it, whatever the kernel`() {
        for (version in listOf("3.10.0", "5.10.0")) {
            kernel(version)
            assertThat(UsbDriverService.shouldClaimDevice(xbox360(), false)).isTrue()
        }

        PadInputDevices.add(0x045E, 0x028E, PadInputDevices.GAMEPAD)
        for (version in listOf("3.10.0", "5.10.0")) {
            kernel(version)
            assertThat(UsbDriverService.shouldClaimDevice(xbox360(), false)).isFalse()
            assertThat(UsbDriverService.shouldClaimDevice(xbox360(), true)).isTrue()
        }
    }

    @Test
    fun `a wireless dongle is claimed only on a kernel that does not light its LED, or by the setting`() {
        PadInputDevices.add(0x045E, 0x0291, PadInputDevices.GAMEPAD)

        kernel("5.10.0")
        // What Android shows for the dongle does not matter: wireless pads have other product IDs.
        assertThat(UsbDriverService.shouldClaimDevice(dongle(), false)).isFalse()
        assertThat(UsbDriverService.shouldClaimDevice(dongle(), true)).isTrue()

        kernel("4.1.15")
        assertThat(UsbDriverService.shouldClaimDevice(dongle(), false)).isTrue()
    }

    @Test
    fun `a device no driver knows is never claimed, even by the setting`() {
        kernel("3.10.0")
        val unknown = UsbPads.device("/dev/x", 0x1234, 0x0001, emptyList())
        val unknownVendor = xbox360(vendorId = 0x1234)

        assertThat(UsbDriverService.shouldClaimDevice(unknown, true)).isFalse()
        assertThat(UsbDriverService.shouldClaimDevice(unknownVendor, true)).isFalse()
    }

    @Test
    fun `isRecognizedInputDevice reads the vendor and product of the device only`() {
        // A keyboard with a gamepad-like source is no pad; this only checks the plumbing.
        PadInputDevices.add(0x046D, 0xC21D, InputDevice.SOURCE_GAMEPAD)

        val logitech = UsbPads.xbox360Pad(vendorId = 0x046D, productId = 0xC21D)
        assertThat(UsbDriverService.isRecognizedInputDevice(logitech)).isTrue()
        assertThat(UsbDriverService.isRecognizedInputDevice(xbox360(vendorId = 0x046D))).isFalse()
    }
}
