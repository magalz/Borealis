package io.github.magalz.borealis.input.feedback

import android.os.Vibrator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A pad that has one vibrator, or that the phone's vibrator stands in for: the two motors become
 * one amplitude, 80% of the low motor's high byte plus 33% of the high motor's, cut at 255, and
 * truncated to an int. A vibrator without amplitude control gets the same amplitude as a duty
 * cycle of 20 ms. What the vibrator is handed depends on the Android version: the media usage
 * of `VibrationAttributes` from Android 13, a game `AudioAttributes` before it.
 */
abstract class RumbleSingleVibratorContract : FeedbackTestBase() {
    /** What a vibrator is handed for a one-shot of [amplitude]. */
    abstract fun oneShot(amplitude: Int): VibratorCall

    /** What a vibrator is handed for the duty cycle of [on] and [off] milliseconds. */
    abstract fun dutyCycle(on: Long, off: Long): VibratorCall

    private val padVibrator = Hardware.vibrator()

    private fun padWithOneVibrator(vibrator: Vibrator = padVibrator): FeedbackRig {
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibrator = vibrator))
        rig.wake(pad)
        return rig
    }

    private fun amplitudeOf(rig: FeedbackRig, low: Int, high: Int): VibratorCall {
        val before = Hardware.calls(padVibrator).size
        rig.rumble(low, high)
        val calls = Hardware.calls(padVibrator)
        assertThat(calls).hasSize(before + 1)
        return calls.last()
    }

    @Test
    fun `the amplitude is 80 percent of the low motor and 33 percent of the high one`() {
        val rig = padWithOneVibrator()

        assertThat(amplitudeOf(rig, 0x6400, 0)).isEqualTo(oneShot(80))
        assertThat(amplitudeOf(rig, 0, 0x6400)).isEqualTo(oneShot(33))
        assertThat(amplitudeOf(rig, 0x6400, 0x6400)).isEqualTo(oneShot(113))
    }

    @Test
    fun `the amplitude is truncated and not rounded`() {
        val rig = padWithOneVibrator()

        assertThat(amplitudeOf(rig, 0x0700, 0)).isEqualTo(oneShot(5)) // 5.6
        assertThat(amplitudeOf(rig, 0x0300, 0)).isEqualTo(oneShot(2)) // 2.4
        assertThat(amplitudeOf(rig, 0x0A00, 0x0A00)).isEqualTo(oneShot(11)) // 8 + 3.3
        assertThat(amplitudeOf(rig, 0x0200, 0)).isEqualTo(oneShot(1)) // 1.6
        assertThat(amplitudeOf(rig, 0, 0x0400)).isEqualTo(oneShot(1)) // 1.32
    }

    @Test
    fun `a motor value counts by its high byte, unsigned`() {
        val rig = padWithOneVibrator()

        assertThat(amplitudeOf(rig, 0xFF00, 0)).isEqualTo(oneShot(204))
        assertThat(amplitudeOf(rig, 0x8000, 0)).isEqualTo(oneShot(102)) // 128 * 0.8
        assertThat(amplitudeOf(rig, 0x64FF, 0)).isEqualTo(oneShot(80))
        assertThat(amplitudeOf(rig, 0, 0xFF00)).isEqualTo(oneShot(84)) // 255 * 0.33
    }

    @Test
    fun `the amplitude is cut at 255`() {
        val rig = padWithOneVibrator()

        assertThat(amplitudeOf(rig, 0xFF00, 0xFF00)).isEqualTo(oneShot(255))
        assertThat(amplitudeOf(rig, 0xFF00, 0x6400)).isEqualTo(oneShot(237)) // 204 + 33
        assertThat(amplitudeOf(rig, 0xFF00, 0x9600)).isEqualTo(oneShot(253)) // 204 + 49.5
        assertThat(amplitudeOf(rig, 0xFF00, 0xA000)).isEqualTo(oneShot(255)) // 204 + 52.8 cut
    }

    @Test
    fun `an amplitude that truncates to zero cancels even when the motors are not zero`() {
        val rig = padWithOneVibrator()

        rig.rumble(0, 0x0100) // 0.33
        rig.rumble(0x0100, 0) // 0.8
        rig.rumble(0, 0x0300) // 0.99
        rig.rumble(0x00FF, 0x00FF)

        assertThat(Hardware.calls(padVibrator)).containsExactly(
            VibratorCall.Cancel,
            VibratorCall.Cancel,
            VibratorCall.Cancel,
            VibratorCall.Cancel,
        )
    }

    @Test
    fun `both motors at zero cancel the vibrator`() {
        val rig = padWithOneVibrator()

        rig.rumble(0, 0)

        assertThat(Hardware.calls(padVibrator)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `a vibrator without amplitude control gets the amplitude as a duty cycle`() {
        val flat = Hardware.vibrator(amplitudeControl = false)
        val rig = padWithOneVibrator(flat)

        rig.rumble(0xFF00, 0xFF00) // 255
        rig.rumble(0xFF00, 0) // 204
        rig.rumble(0x9F00, 0) // 127
        rig.rumble(0x0200, 0) // 1
        rig.rumble(0x1100, 0) // 13

        assertThat(Hardware.calls(flat))
            .containsExactly(
                dutyCycle(20, 0),
                dutyCycle(16, 4),
                dutyCycle(9, 11),
                dutyCycle(0, 20),
                dutyCycle(1, 19),
            )
            .inOrder()
    }

    @Test
    fun `a vibrator without amplitude control is cancelled at zero`() {
        val flat = Hardware.vibrator(amplitudeControl = false)
        val rig = padWithOneVibrator(flat)

        rig.rumble(0, 0)

        assertThat(Hardware.calls(flat)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `a trigger rumble does not reach a single vibrator`() {
        val rig = padWithOneVibrator()

        rig.rumbleTriggers(0x4000, 0x4000)

        assertThat(Hardware.calls(padVibrator)).isEmpty()
    }

    @Test
    fun `a stopped handler does not rumble`() {
        val rig = padWithOneVibrator()
        rig.handler.stop()
        val before = Hardware.calls(padVibrator).toList()

        rig.rumble(0x4000, 0x4000)

        assertThat(Hardware.calls(padVibrator)).isEqualTo(before)
    }

    @Test
    fun `a rumble for another controller number does not reach the vibrator`() {
        val rig = padWithOneVibrator()

        rig.rumble(0x4000, 0x4000, controller = 2)

        assertThat(Hardware.calls(padVibrator)).isEmpty()
    }

    @Test
    fun `the phone vibrates for the on-screen controller with the raw motor values`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            onscreenController = true
            vibrateOsc = true
        }

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).containsExactly(oneShot(113))
    }

    @Test
    fun `the phone does not vibrate for the on-screen controller unless the setting is on`() {
        val calls = mutableListOf<List<VibratorCall>>()
        for (setting in listOf("onscreenController", "vibrateOsc", "onlyL3R3")) {
            val phone = Hardware.vibrator()
            val rig = rig(phoneVibrator = phone) {
                onscreenController = setting != "onscreenController"
                vibrateOsc = setting != "vibrateOsc"
                onlyL3R3 = setting == "onlyL3R3"
            }
            rig.rumble(0x6400, 0x6400)
            calls += Hardware.calls(phone).toList()
        }

        assertThat(calls.flatten()).isEmpty()
        assertThat(calls).hasSize(3)
    }

    @Test
    fun `the phone does not vibrate for the on-screen controller on another controller number`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            onscreenController = true
            vibrateOsc = true
        }

        rig.rumble(0x6400, 0x6400, controller = 1)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone without amplitude control gets a duty cycle for the on-screen controller`() {
        val phone = Hardware.vibrator(amplitudeControl = false)
        val rig = rig(phoneVibrator = phone) {
            onscreenController = true
            vibrateOsc = true
        }

        rig.rumble(0xFF00, 0)

        assertThat(Hardware.calls(phone)).containsExactly(dutyCycle(16, 4))
    }

    @Test
    fun `a pad without a vibrator makes the phone vibrate when the setting asks for it`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 100
        }
        rig.wake(rig.connectPad())

        rig.rumble(0x8000, 0)

        assertThat(Hardware.calls(phone)).containsExactly(oneShot(102))
    }

    @Test
    fun `the fallback strength scales the unsigned motor value before the cut`() {
        val results = mutableListOf<VibratorCall>()
        for ((strength, low) in listOf(100 to 0xFFFF, 50 to 0xFFFF, 200 to 0x8000, 100 to 0x8000)) {
            val phone = Hardware.vibrator()
            val rig = rig(phoneVibrator = phone) {
                vibrateFallbackToDevice = true
                vibrateFallbackToDeviceStrength = strength
            }
            rig.wake(rig.connectPad())
            rig.rumble(low, 0)
            results += Hardware.calls(phone).single()
        }

        assertThat(results)
            .containsExactly(
                oneShot(204), // 65535 is cut at 65534, which is -2: the high byte is 255
                oneShot(101), // 65535 * 50 / 100 = 32767: the high byte is 127
                oneShot(204), // 32768 * 200 / 100 is cut at 65534
                oneShot(102), // 32768 * 100 / 100 = 32768: the high byte is 128
            )
            .inOrder()
    }

    @Test
    fun `the fallback strength applies to the high motor as well`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 50
        }
        rig.wake(rig.connectPad())

        rig.rumble(0, 0xFFFF)

        assertThat(Hardware.calls(phone)).containsExactly(oneShot(41)) // 127 * 0.33
    }

    @Test
    fun `a fallback strength of zero cancels the phone`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 0
        }
        rig.wake(rig.connectPad())

        rig.rumble(0xFFFF, 0xFFFF)

        assertThat(Hardware.calls(phone)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `the phone stays quiet when the setting is off`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)
        rig.wake(rig.connectPad())

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone stays quiet for another controller number`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 100
        }
        rig.wake(rig.connectPad())

        rig.rumble(0x6400, 0x6400, controller = 1)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the phone does not stand in for a pad that has a vibrator`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            vibrateFallbackToDevice = true
            vibrateFallbackToDeviceStrength = 100
        }
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = padVibrator)))

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
        assertThat(Hardware.calls(padVibrator)).containsExactly(oneShot(113))
    }

    @Test
    fun `the on-screen setting is not used when a pad answered the controller number`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone) {
            onscreenController = true
            vibrateOsc = true
        }
        rig.wake(rig.connectPad())

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
    }
}

/** Android 13 and later: the media usage of `VibrationAttributes`. */
@RunWith(RobolectricTestRunner::class)
class RumbleSingleVibratorTest : RumbleSingleVibratorContract() {
    override fun oneShot(amplitude: Int): VibratorCall =
        VibratorCall.Play(Effects.oneShot(amplitude), VibrationExpectations.media())

    override fun dutyCycle(on: Long, off: Long): VibratorCall =
        VibratorCall.Play(Effects.waveform(on, off), VibrationExpectations.media())
}

/** Android 12: the pad has a vibrator manager API but a single vibrator is driven with a game `AudioAttributes`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RumbleSingleVibratorAndroid12Test : RumbleSingleVibratorContract() {
    override fun oneShot(amplitude: Int): VibratorCall =
        VibratorCall.Play(Effects.oneShot(amplitude), Effects.gameAudio())

    override fun dutyCycle(on: Long, off: Long): VibratorCall =
        VibratorCall.Pattern(listOf(0, on, off), 0, Effects.gameAudio())
}

/** Android 11, which has no vibrator manager at all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RumbleSingleVibratorAndroid11Test : RumbleSingleVibratorContract() {
    override fun oneShot(amplitude: Int): VibratorCall =
        VibratorCall.Play(Effects.oneShot(amplitude), Effects.gameAudio())

    override fun dutyCycle(on: Long, off: Long): VibratorCall =
        VibratorCall.Pattern(listOf(0, on, off), 0, Effects.gameAudio())
}
