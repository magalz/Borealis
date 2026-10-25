package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On Android 12 the sensors of an input device can crash the framework for pads that are not known
 * to have them, so the handler asks only a PlayStation or a Nintendo pad. From Android 13 it asks
 * every pad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MotionSensorsAndroid12Test : FeedbackTestBase() {
    private val padSensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
    private val log
        get() = Hardware.sensors(padSensors).log

    private fun requestFrom(vendorId: Int): FeedbackRig {
        val rig = rig { gamepadMotionSensors = true }
        rig.wake(rig.connectPad(vendorId = vendorId, features = DeviceFeatures(sensorManager = padSensors)))
        rig.motionState(GYRO, 100)
        return rig
    }

    @Test
    fun `a PlayStation pad is asked for its sensors`() {
        requestFrom(FeedbackRig.SONY_VENDOR)

        assertThat(log).containsExactly("register 4 10000")
    }

    @Test
    fun `a Nintendo pad is asked for its sensors`() {
        requestFrom(FeedbackRig.NINTENDO_VENDOR)

        assertThat(log).containsExactly("register 4 10000")
    }

    @Test
    fun `an Xbox pad is not asked`() {
        requestFrom(FeedbackRig.XBOX_VENDOR)

        assertThat(log).isEmpty()
    }

    @Test
    fun `a pad of another vendor is not asked`() {
        requestFrom(0x1234)

        assertThat(log).isEmpty()
    }

    @Test
    fun `a pad that is not asked is not reported with sensors`() {
        val rig = requestFrom(FeedbackRig.XBOX_VENDOR)

        val sensors = MoonBridge.LI_CCAP_ACCEL.toInt() or MoonBridge.LI_CCAP_GYRO.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and sensors).isEqualTo(0)
    }

    private companion object {
        const val GYRO = 2
    }
}
