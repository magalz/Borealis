package io.github.magalz.borealis.input.ch5

import android.hardware.Sensor
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.HeldReader
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.usbPad
import io.github.magalz.borealis.testing.contextField
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The rumble and the motion request come from the native connection thread, while the main thread
 * changes the lists of pads. Like the other host callbacks (see `DeviceListThreadsTest`), they walk
 * the list as it was when they began and take no lock: a pad that leaves meanwhile leaves no hole
 * in their loop, and the packet send, which holds the input lock, does not hold them up.
 */
class HostCallbackLoopsTest : FeedbackCharacterization() {
    private fun FeedbackRig.padWith(id: Int, features: DeviceFeatures) =
        wake(connectPad(id = id, features = features))

    @Test
    fun `a rumble partway through the pads still reaches the ones that stay when the first leaves`() {
        val rig = rig()
        val vibrators = (5..7).map { Hardware.vibrator() }
        vibrators.forEachIndexed { i, vibrator ->
            rig.padWith(5 + i, DeviceFeatures(vibrator = vibrator))
        }
        val reader = HeldReader(rig.handler, holdAtStep = 2) { rig.rumble(0x6400, 0x6400) }
        reader.hold()

        rig.handler.onInputDeviceRemoved(5)

        assertThat(reader.finish()).isNull()
        assertThat(Hardware.calls(vibrators[1])).hasSize(1)
        assertThat(Hardware.calls(vibrators[2])).hasSize(1)
    }

    @Test
    fun `a rumble partway through the USB pads still reaches the ones that stay when the first leaves`() {
        val rig = rig()
        val pads = (1..3).map { rig.handler.usbPad(it) }
        val reader = HeldReader(rig.handler, holdAtStep = 2) { rig.rumble(0x4000, 0x2000) }
        reader.hold()

        rig.handler.deviceRemoved(pads[0])

        assertThat(reader.finish()).isNull()
        assertThat(pads[1].rumbles).containsExactly(0x4000 to 0x2000)
        assertThat(pads[2].rumbles).containsExactly(0x4000 to 0x2000)
    }

    @Test
    fun `a motion request partway through the pads still reaches the ones that stay when the first leaves`() {
        val rig = rig { gamepadMotionSensors = true }
        (5..7).forEach {
            val sensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
            rig.padWith(it, DeviceFeatures(sensorManager = sensors))
        }
        // The first step is the one of the search for the pad that reports, which stops at the
        // first pad with sensors of its own; the loop over the pads begins after it
        val reader = HeldReader(rig.handler, holdAtStep = 3) { rig.motionState(GYRO, 100) }
        reader.hold()

        rig.handler.onInputDeviceRemoved(5)

        assertThat(reader.finish()).isNull()
        assertThat(rig.handler.contextField(6, "gyroReportRateHz")).isEqualTo(100.toShort())
        assertThat(rig.handler.contextField(7, "gyroReportRateHz")).isEqualTo(100.toShort())
    }

    @Test
    fun `a rumble and a motion request do not wait for the lock of the packet send`() {
        val rig = rig { gamepadMotionSensors = true }
        val vibrator = Hardware.vibrator()
        val sensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        rig.padWith(5, DeviceFeatures(vibrator = vibrator, sensorManager = sensors))
        val inputLock = ReflectionHelpers.getField<Any>(rig.handler, "inputLock")
        val reader =
            Thread(
                {
                    rig.rumble(0x6400, 0x6400)
                    rig.motionState(GYRO, 100)
                },
                "host-callbacks",
            )

        synchronized(inputLock) {
            reader.start()
            reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertThat(reader.isAlive).isFalse()
        }
        assertThat(Hardware.calls(vibrator)).hasSize(1)
        assertThat(Hardware.sensors(sensors).log).containsExactly("register 4 10000")
    }

    private companion object {
        const val GYRO = 2
        const val WAIT_SECONDS = 10L
    }
}
