package io.github.magalz.borealis.input.ch5

import android.os.Vibrator
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.feedback.RecordingUsbController
import io.github.magalz.borealis.input.feedback.VibratorCall
import org.junit.Test

/**
 * Whether the phone stands in for the pads of a controller number is decided by all of them: it
 * vibrates only when a pad answered the number and not one of them rumbled, whatever order the pads
 * come in and whichever driver holds them.
 */
class MixedPadsRumbleTest : FeedbackCharacterization() {
    private val phone = Hardware.vibrator()

    private fun fallbackRig(): FeedbackRig =
        rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 100
        }

    private fun FeedbackRig.padWith(id: Int, vibrator: Vibrator?) =
        wake(connectPad(id = id, features = DeviceFeatures(vibrator = vibrator)))

    @Test
    fun `the phone stays quiet when the first pad rumbled and the last has no vibrator`() {
        val own = Hardware.vibrator()
        val rig = fallbackRig()
        rig.padWith(5, own)
        rig.padWith(6, null)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(own)).hasSize(1)
        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone stays quiet when the first pad has no vibrator and the last rumbled`() {
        val own = Hardware.vibrator()
        val rig = fallbackRig()
        rig.padWith(5, null)
        rig.padWith(6, own)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(own)).hasSize(1)
        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the pad after one that rumbled still gets the rumble`() {
        val first = Hardware.vibrator()
        val second = Hardware.vibrator()
        val rig = fallbackRig()
        rig.padWith(5, first)
        rig.padWith(6, second)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(first)).hasSize(1)
        assertThat(Hardware.calls(second)).hasSize(1)
    }

    @Test
    fun `the phone stays quiet when a USB pad rumbled and a pad of the number has no vibrator`() {
        val rig = fallbackRig()
        val usb = RecordingUsbController(1, rig.handler)
        rig.handler.deviceAdded(usb)
        usb.report(ControllerPacket.A_FLAG)
        rig.padWith(5, null)

        rig.rumble(0x6400, 0x6400)

        assertThat(usb.rumbles).containsExactly(0x6400 to 0x6400)
        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone vibrates when every pad of the number has no vibrator`() {
        val rig = fallbackRig()
        rig.padWith(5, null)
        rig.padWith(6, null)

        rig.rumble(0x8000, 0)

        assertThat(Hardware.calls(phone)).hasSize(1)
        assertThat(Hardware.calls(phone).single()).isInstanceOf(VibratorCall.Play::class.java)
    }
}
