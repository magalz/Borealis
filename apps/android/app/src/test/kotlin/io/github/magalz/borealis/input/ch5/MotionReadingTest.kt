package io.github.magalz.borealis.input.ch5

import android.hardware.Sensor
import android.view.Surface
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.feedback.Motion
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * The readings of a sensor listener at the edges the other motion tests leave open: a repeat is a
 * repeat of the values the sensor gave, not of what the display rotation made of them, and it is
 * decided by the three numbers as numbers (a NaN is never equal to one, a negative zero is equal to
 * a zero). A rotation that is none of the four turns nothing.
 */
class MotionReadingTest : FeedbackCharacterization() {
    private val padSensors =
        Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)

    /** A pad with sensors of its own: its readings are not turned with the display. */
    private fun padRig(): FeedbackRig {
        val rig = rig { gamepadMotionSensors = true }
        rig.wake(rig.connectPad(features = DeviceFeatures(sensorManager = padSensors)))
        return rig
    }

    /** A pad that borrows the sensors of the phone, which are turned with the display. */
    private fun phoneRig(): FeedbackRig {
        val rig = rig {
            gamepadMotionSensors = false
            gamepadMotionSensorsFallbackToDevice = true
        }
        rig.wake(rig.connectPad())
        return rig
    }

    private fun FeedbackRig.rotate(rotation: Int) =
        shadowOf(activity.windowManager.defaultDisplay).setRotation(rotation)

    private fun deliver(manager: android.hardware.SensorManager, type: Int, vararg v: Float) =
        Hardware.sensors(manager).deliver(type, v[0], v[1], v[2])

    private fun values(rig: FeedbackRig) = rig.connection.motions.map { Triple(it.x, it.y, it.z) }

    @Test
    fun `the same values after the display turned are a repeat`() {
        val rig = phoneRig()
        rig.motionState(ACCEL, 100)

        rig.rotate(Surface.ROTATION_0)
        deliver(rig.phoneSensors, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        rig.rotate(Surface.ROTATION_180)
        deliver(rig.phoneSensors, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        deliver(rig.phoneSensors, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 4f)

        assertThat(values(rig)).containsExactly(Triple(1f, 3f, -2f), Triple(-1f, 4f, 2f)).inOrder()
    }

    @Test
    fun `a reading with a NaN is never a repeat`() {
        val rig = padRig()
        rig.motionState(ACCEL, 100)

        deliver(padSensors, Sensor.TYPE_ACCELEROMETER, Float.NaN, 2f, 3f)
        deliver(padSensors, Sensor.TYPE_ACCELEROMETER, Float.NaN, 2f, 3f)

        assertThat(rig.connection.motions).hasSize(2)
        assertThat(rig.connection.motions.map { it.x }.all { it.isNaN() }).isTrue()
    }

    @Test
    fun `a negative zero after a zero is a repeat`() {
        val rig = padRig()
        rig.motionState(ACCEL, 100)

        deliver(padSensors, Sensor.TYPE_ACCELEROMETER, 0f, 0f, 0f)
        deliver(padSensors, Sensor.TYPE_ACCELEROMETER, -0f, 0f, 0f)

        assertThat(rig.connection.motions).hasSize(1)
    }

    @Test
    fun `a rotation that is none of the four leaves the axes as they come`() {
        val rig = phoneRig()
        rig.motionState(ACCEL, 100)
        rig.motionState(GYRO, 100)
        rig.rotate(UNKNOWN_ROTATION)

        deliver(rig.phoneSensors, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        deliver(rig.phoneSensors, Sensor.TYPE_GYROSCOPE, 4f, 5f, 6f)

        assertThat(rig.connection.motions)
            .containsExactly(
                Motion(0, ACCEL, 1f, 2f, 3f),
                Motion(0, GYRO, 4f * RAD, 5f * RAD, 6f * RAD),
            )
            .inOrder()
    }

    private companion object {
        const val ACCEL = 1
        const val GYRO = 2
        const val UNKNOWN_ROTATION = 9
        const val RAD = 57.2957795f
    }
}
