package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rumble of a pad the app's USB driver holds: the values reach the driver untouched, as the
 * signed shorts they are, for every pad that has the controller number. A pad that has not sent
 * input yet has no number assigned and answers to none: the host does not know it either.
 */
@RunWith(RobolectricTestRunner::class)
class UsbRumbleTest : FeedbackTestBase() {
    /** A pad the driver holds. It has sent its first input, which gives it a controller number. */
    private fun usbPad(
        rig: FeedbackRig,
        id: Int = 1,
        sentInput: Boolean = true,
    ): RecordingUsbController =
        RecordingUsbController(id, rig.handler).also {
            rig.handler.deviceAdded(it)
            if (sentInput) {
                it.report(ControllerPacket.A_FLAG)
            }
        }

    @Test
    fun `a rumble reaches the driver with the values as they came`() {
        val rig = rig()
        val usb = usbPad(rig)

        rig.rumble(0x4000, 0xC000)

        assertThat(usb.rumbles).containsExactly(0x4000 to 0xC000.toShort().toInt())
    }

    @Test
    fun `the values are not narrowed to a byte`() {
        val rig = rig()
        val usb = usbPad(rig)

        rig.rumble(0xFFFF, 0x0001)
        rig.rumble(0x8000, 0x7FFF)

        assertThat(usb.rumbles).containsExactly(-1 to 1, -32768 to 32767).inOrder()
    }

    @Test
    fun `a pad that sent no input yet answers to no controller number`() {
        val rig = rig { multiController = true }
        val usb = usbPad(rig, sentInput = false)

        rig.rumble(0x4000, 0x4000, controller = 0)
        rig.rumble(0x2000, 0x2000, controller = 1)
        rig.rumbleTriggers(0x4000, 0x4000, controller = 0)

        assertThat(usb.rumbles).isEmpty()
        assertThat(usb.triggerRumbles).isEmpty()
    }

    @Test
    fun `a pad rumbles once it has sent input`() {
        val rig = rig()
        val usb = usbPad(rig, sentInput = false)

        usb.report(ControllerPacket.A_FLAG)
        rig.rumble(0x4000, 0x4000)
        rig.rumbleTriggers(0x2000, 0x2000)

        assertThat(usb.rumbles).containsExactly(0x4000 to 0x4000)
        assertThat(usb.triggerRumbles).containsExactly(0x2000 to 0x2000)
    }

    @Test
    fun `a pad that sent no input yet does not rumble with the pad that has controller 0`() {
        val rig = rig()
        val first = usbPad(rig, id = 1)
        val second = usbPad(rig, id = 2, sentInput = false)

        rig.rumble(0x4000, 0x4000)
        rig.rumbleTriggers(0x2000, 0x2000)

        assertThat(first.rumbles).containsExactly(0x4000 to 0x4000)
        assertThat(first.triggerRumbles).containsExactly(0x2000 to 0x2000)
        assertThat(second.rumbles).isEmpty()
        assertThat(second.triggerRumbles).isEmpty()
    }

    @Test
    fun `the phone vibrates for the on-screen controller while the only pad sent no input yet`() {
        val phone = Hardware.vibrator()
        val rig =
            rig(phoneVibrator = phone) {
                onscreenController = true
                vibrateOsc = true
            }
        usbPad(rig, sentInput = false)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).hasSize(1)
    }

    @Test
    fun `the second pad answers to its own controller number`() {
        val rig = rig { multiController = true }
        val first = usbPad(rig, id = 1)
        val second = usbPad(rig, id = 2)

        rig.rumble(0x4000, 0x4000, controller = 1)

        assertThat(first.rumbles).isEmpty()
        assertThat(second.rumbles).containsExactly(0x4000 to 0x4000)
    }

    @Test
    fun `two pads with the same controller number both rumble`() {
        val rig = rig()
        val first = usbPad(rig, id = 1)
        val second = usbPad(rig, id = 2)

        rig.rumble(0x4000, 0x4000)

        assertThat(first.rumbles).hasSize(1)
        assertThat(second.rumbles).hasSize(1)
    }

    @Test
    fun `a rumble for another controller number reaches no pad`() {
        val rig = rig()
        val usb = usbPad(rig)

        rig.rumble(0x4000, 0x4000, controller = 4)

        assertThat(usb.rumbles).isEmpty()
    }

    @Test
    fun `the phone does not stand in for a pad the driver holds`() {
        val phone = Hardware.vibrator()
        val rig =
            rig(phoneVibrator = phone) {
                vibrateFallbackToDevice = true
                vibrateFallbackToDeviceStrength = 100
            }
        usbPad(rig)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone does not vibrate for the on-screen controller when a pad answered`() {
        val phone = Hardware.vibrator()
        val rig =
            rig(phoneVibrator = phone) {
                onscreenController = true
                vibrateOsc = true
            }
        usbPad(rig)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the end of the stream stops every motor of a pad the driver holds`() {
        val rig = rig()
        val usb = usbPad(rig)
        rig.rumble(0x4000, 0x4000)
        rig.rumbleTriggers(0x7FFF, 0x7FFF)

        rig.handler.stop()

        assertThat(usb.rumbles.last()).isEqualTo(0 to 0)
        assertThat(usb.triggerRumbles.last()).isEqualTo(0 to 0)
    }

    @Test
    fun `the end of the stream stops a pad that sent no input too`() {
        val rig = rig()
        val usb = usbPad(rig, sentInput = false)

        rig.handler.stop()

        assertThat(usb.rumbles).containsExactly(0 to 0)
        assertThat(usb.triggerRumbles).containsExactly(0 to 0)
    }

    @Test
    fun `a trigger rumble reaches the driver with the values as they came`() {
        val rig = rig()
        val usb = usbPad(rig)

        rig.rumbleTriggers(0xFFFF, 0x0102)

        assertThat(usb.triggerRumbles).containsExactly(-1 to 0x0102)
    }

    @Test
    fun `a trigger rumble reaches only the pads with the controller number`() {
        val rig = rig { multiController = true }
        val first = usbPad(rig, id = 1)
        val second = usbPad(rig, id = 2)

        rig.rumbleTriggers(0x4000, 0x2000, controller = 1)

        assertThat(first.triggerRumbles).isEmpty()
        assertThat(second.triggerRumbles).containsExactly(0x4000 to 0x2000)
    }

    @Test
    fun `a stopped handler does not rumble the driver`() {
        val rig = rig()
        val usb = usbPad(rig)
        rig.handler.stop()

        rig.rumble(0x4000, 0x4000)
        rig.rumbleTriggers(0x4000, 0x4000)

        // Only the zeros of the stop itself
        assertThat(usb.rumbles).containsExactly(0 to 0)
        assertThat(usb.triggerRumbles).containsExactly(0 to 0)
    }

    @Test
    fun `a pad the driver let go no longer rumbles`() {
        val rig = rig()
        val usb = usbPad(rig)
        rig.handler.deviceRemoved(usb)

        rig.rumble(0x4000, 0x4000)

        assertThat(usb.rumbles).isEmpty()
    }

    @Test
    fun `a USB pad and an input device pad with the same controller number both rumble`() {
        val own = Hardware.vibrator()
        val rig = rig()
        val usb = usbPad(rig)
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))

        rig.rumble(0x6400, 0x6400)

        assertThat(usb.rumbles).hasSize(1)
        assertThat(Hardware.calls(own)).hasSize(1)
    }
}
