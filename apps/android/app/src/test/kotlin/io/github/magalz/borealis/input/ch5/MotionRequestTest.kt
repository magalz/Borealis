package io.github.magalz.borealis.input.ch5

import android.hardware.Sensor
import android.util.SparseArray
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
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

    /** The field [name] of the context of the pad with [id], as a number. */
    private fun fieldOf(rig: FeedbackRig, id: Int, name: String): Any? {
        @Suppress("UNCHECKED_CAST") // The field holds the handler's contexts by construction.
        val contexts =
            ControllerHandler::class
                .java
                .getDeclaredField("inputDeviceContexts")
                .apply { isAccessible = true }
                .get(rig.handler) as SparseArray<Any>
        val context = contexts.get(id)
        return context.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(context)
    }

    @Test
    fun `a pad without sensors keeps the rates the host asked for`() {
        val rig = sensorRig()
        val pad =
            rig.connectPad(features = DeviceFeatures(sensorManager = Hardware.sensorManager()))
        rig.wake(pad)

        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)

        assertThat(fieldOf(rig, pad.id, "sensorManager")).isNull()
        assertThat(fieldOf(rig, pad.id, "gyroReportRateHz")).isEqualTo(100.toShort())
        assertThat(fieldOf(rig, pad.id, "accelReportRateHz")).isEqualTo(50.toShort())
        assertThat(fieldOf(rig, pad.id, "gyroListener")).isNull()
        assertThat(fieldOf(rig, pad.id, "accelListener")).isNull()
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
