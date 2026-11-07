package io.github.magalz.borealis.input.ch5

import android.hardware.Sensor
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.testing.contextField
import org.junit.Test

/**
 * What a request of the host for a motion type does to a pad that has no sensor to register: the
 * rate it asked for is kept, to be applied if the sensors come back, and the request still cancels
 * the enabling that was waiting, whatever its type.
 */
class MotionRequestTest : FeedbackCharacterization() {
    private val padSensors =
        Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)

    private fun sensorRig() = rig { gamepadMotionSensors = true }

    @Test
    fun `a pad without sensors keeps the rates the host asked for`() {
        val rig = sensorRig()
        val pad =
            rig.connectPad(features = DeviceFeatures(sensorManager = Hardware.sensorManager()))
        rig.wake(pad)

        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)

        assertThat(rig.handler.contextField(pad.id, "sensorManager")).isNull()
        assertThat(rig.handler.contextField(pad.id, "gyroReportRateHz")).isEqualTo(100.toShort())
        assertThat(rig.handler.contextField(pad.id, "accelReportRateHz")).isEqualTo(50.toShort())
        assertThat(rig.handler.contextField(pad.id, "gyroListener")).isNull()
        assertThat(rig.handler.contextField(pad.id, "accelListener")).isNull()
    }

    @Test
    fun `a request of a motion type the handler does not know cancels an enabling that waits`() {
        val rig = sensorRig()
        val pad = rig.connectPad(features = DeviceFeatures(sensorManager = padSensors))
        rig.wake(pad)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()
        rig.handler.enableSensors()
        val log = Hardware.sensors(padSensors).log
        log.clear()

        rig.motionState(UNKNOWN, 100)
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(log).isEmpty()
    }

    private companion object {
        const val ACCEL = 1
        const val GYRO = 2
        const val UNKNOWN = 3
    }
}
