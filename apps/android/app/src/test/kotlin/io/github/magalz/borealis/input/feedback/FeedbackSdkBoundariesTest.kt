package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 13 is where the handler starts to set the media usage and to ask every pad for its
 * sensors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackAndroid13Test : FeedbackTestBase() {
    @Test
    fun `a pair of vibrators is driven with the media usage`() {
        val manager = Hardware.vibratorManager(List(2) { Hardware.vibrator() })
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumble(0x4000, 0xC000)

        assertThat(Hardware.manager(manager).vibrations.map { it.combined to it.attributes })
            .containsExactly(
                VibrationExpectations.combo(10 to 192, 11 to 64) to VibrationExpectations.media()
            )
    }

    @Test
    fun `four vibrators are driven with the media usage`() {
        val manager = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumbleTriggers(0x2000, 0x6000)

        assertThat(Hardware.manager(manager).vibrations.map { it.combined to it.attributes })
            .containsExactly(
                VibrationExpectations.combo(12 to 32, 13 to 96) to VibrationExpectations.media()
            )
    }

    @Test
    fun `a single vibrator is driven with the media usage`() {
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))

        rig.rumble(0x6400, 0)

        assertThat(Hardware.calls(own))
            .containsExactly(VibratorCall.Play(Effects.oneShot(80), VibrationExpectations.media()))
    }

    @Test
    fun `a duty cycle is driven with the media usage`() {
        val flat = Hardware.vibrator(amplitudeControl = false)
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = flat)))

        rig.rumble(0xFF00, 0)

        assertThat(Hardware.calls(flat))
            .containsExactly(
                VibratorCall.Play(Effects.waveform(16, 4), VibrationExpectations.media())
            )
    }

    @Test
    fun `an Xbox pad is asked for its sensors`() {
        val padSensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = rig { gamepadMotionSensors = true }
        rig.wake(
            rig.connectPad(
                vendorId = FeedbackRig.XBOX_VENDOR,
                features = DeviceFeatures(sensorManager = padSensors),
            )
        )

        rig.motionState(2, 100)

        assertThat(Hardware.sensors(padSensors).log).containsExactly("register 4 10000")
    }
}

/**
 * Android 14 is where `Light.hasRgbControl()` can be trusted, so every vendor's pad takes a colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackAndroid14Test : FeedbackTestBase() {
    private val lights =
        Hardware.lightsManager(listOf(Hardware.plainLight(2), Hardware.rgbLight(1)))

    @Test
    fun `an Xbox pad takes the colour on its coloured light only`() {
        val rig = rig()
        rig.wake(
            rig.connectPad(
                vendorId = FeedbackRig.XBOX_VENDOR,
                features = DeviceFeatures(lightsManager = lights),
            )
        )

        rig.led(0x80, 0x10, 0xFF)
        rig.awaitBackground()

        assertThat(Hardware.session(Hardware.lights(lights).sessions.single()).requests)
            .containsExactly(listOf(LightColor(1, 0xFF8010FF.toInt())))
    }

    @Test
    fun `the pad reports its LED`() {
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(lightsManager = lights)))

        val flag = MoonBridge.LI_CCAP_RGB_LED.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and flag).isEqualTo(flag)
    }

    @Test
    fun `a pad whose lights have no colour control ignores the colour`() {
        val plain = Hardware.lightsManager(listOf(Hardware.plainLight(2)))
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(lightsManager = plain)))

        rig.led(1, 2, 3)
        rig.awaitBackground()

        assertThat(Hardware.lights(plain).sessions).isEmpty()
    }

    @Test
    fun `the battery is reported`() {
        val battery =
            Hardware.battery(status = android.hardware.BatteryState.STATUS_FULL, capacity = 1f)
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(batteryState = battery)))

        assertThat(batteryEvents).containsExactly(BatteryEvent(0, 5, 100))
    }
}
