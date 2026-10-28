package io.github.magalz.borealis.input.devices

import android.content.Context
import android.hardware.usb.UsbConstants
import android.preference.PreferenceManager
import android.view.InputDevice
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * `ControllerHandler.getAttachedControllerMask` counts the gamepads that are attached, one bit each
 * from bit 0 up: the input devices with joystick axes, then the USB devices the USB driver would
 * claim. The on-screen controller sets bit 0 too. The handler reads the same mask when it is made,
 * as the controllers it expects to hear from.
 */
class AttachedControllerMaskTest : DevicesTestBase() {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun mask() = ControllerHandler.getAttachedControllerMask(context).toInt()

    private fun setPreference(key: String, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(key, value)
            .commit()
    }

    private fun connectAll(vararg pads: Pad) = pads.forEach(::connectPad)

    @Test
    fun `no device is an empty mask`() {
        assertThat(mask()).isEqualTo(0)
    }

    @Test
    fun `each pad with joystick axes takes the next bit`() {
        connectAll(pad(1), pad(2), pad(3))

        assertThat(mask()).isEqualTo(0b111)
    }

    @Test
    fun `the bits do not depend on the device ids`() {
        connectAll(pad(40), pad(7))

        assertThat(mask()).isEqualTo(0b11)
    }

    @Test
    fun `a device without joystick axes is not counted`() {
        connectAll(
            pad(1) {
                sources = InputDevice.SOURCE_GAMEPAD
                axes = emptyList()
            },
            pad(2) {
                sources = InputDevice.SOURCE_MOUSE
                axes = emptyList()
                keyboardType = InputDevice.KEYBOARD_TYPE_NONE
            },
        )

        assertThat(mask()).isEqualTo(0)
    }

    @Test
    fun `axes without the joystick source are not counted`() {
        connectAll(
            pad(1) {
                sources = InputDevice.SOURCE_GAMEPAD
                axes = LEFT_STICK
            },
            pad(2),
        )

        assertThat(mask()).isEqualTo(0b1)
    }

    @Test
    fun `an alphabetic keyboard with joystick axes is not counted`() {
        connectAll(
            pad(1) {
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_JOYSTICK
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC
                axes = LEFT_STICK
            },
            pad(2),
        )

        assertThat(mask()).isEqualTo(0b1)
    }

    @Test
    fun `the mask is a short that wraps at the sixteenth pad`() {
        (1..15).forEach { connectPad(pad(it)) }
        assertThat(mask()).isEqualTo(0x7FFF)

        connectPad(pad(16))
        assertThat(mask()).isEqualTo(-1)

        connectPad(pad(17))
        assertThat(mask()).isEqualTo(-1)
    }

    @Test
    fun `the on-screen controller sets the first bit`() {
        setPreference("checkbox_show_onscreen_controls", true)

        assertThat(mask()).isEqualTo(1)
    }

    @Test
    fun `the on-screen controller shares its bit with the first pad`() {
        setPreference("checkbox_show_onscreen_controls", true)
        connectAll(pad(1), pad(2))

        assertThat(mask()).isEqualTo(0b11)
    }

    @Test
    fun `a USB pad the driver would claim takes the next bit`() {
        connectAll(pad(1))
        plugUsb(usbDevice("/dev/usb-1", vendorId = 0x045e, productId = 0x028e))

        assertThat(mask()).isEqualTo(0b11)
    }

    @Test
    fun `an Xbox One USB pad is claimed too`() {
        plugUsb(
            usbDevice(
                "/dev/usb-1",
                vendorId = 0x045e,
                productId = 0x02ea,
                type =
                    UsbInterfaceType(
                        subclass = XBOX_ONE_INTERFACE_SUBCLASS,
                        protocol = XBOX_ONE_INTERFACE_PROTOCOL,
                    ),
            )
        )

        assertThat(mask()).isEqualTo(0b1)
    }

    @Test
    fun `a USB pad that Android shows as an input device is counted once`() {
        connectAll(
            pad(1) {
                vendorId = 0x045e
                productId = 0x028e
            }
        )
        plugUsb(usbDevice("/dev/usb-1", vendorId = 0x045e, productId = 0x028e))

        assertThat(mask()).isEqualTo(0b1)
    }

    @Test
    fun `a USB pad is counted when Android only shows another device of its ids`() {
        connectAll(
            pad(1) {
                vendorId = 0x045e
                productId = 0x028e
                sources = InputDevice.SOURCE_KEYBOARD
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC
                axes = emptyList()
            }
        )
        plugUsb(usbDevice("/dev/usb-1", vendorId = 0x045e, productId = 0x028e))

        assertThat(mask()).isEqualTo(0b1)
    }

    @Test
    fun `USB pads are not counted when the USB driver is off`() {
        setPreference("checkbox_usb_driver", false)
        plugUsb(usbDevice("/dev/usb-1", vendorId = 0x045e, productId = 0x028e))

        assertThat(mask()).isEqualTo(0)
    }

    @Test
    fun `a USB device the driver does not know is not counted`() {
        plugUsb(usbDevice("/dev/usb-1", vendorId = 0x1234, productId = 0x0001))
        plugUsb(
            usbDevice(
                "/dev/usb-2",
                vendorId = 0x045e,
                productId = 0x0002,
                type = UsbInterfaceType(interfaceClass = UsbConstants.USB_CLASS_HID),
            )
        )

        assertThat(mask()).isEqualTo(0)
    }

    @Test
    fun `the handler expects the pads that were attached when it was made`() {
        val first = pad(1)
        val second = pad(2)
        val rig = rig(multiController = true, devices = listOf(first, second))

        rig.press(second, KeyEvent.KEYCODE_BUTTON_A)

        // Bit 1 is the pad that has not sent anything yet
        assertThat(rig.connection.packets.single().activeMask).isEqualTo(0b11)
    }
}
