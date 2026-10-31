package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pad has no controller number until it sends input, and the host knows nothing of it until then.
 * It keeps number 0 in the meantime, which is player 1's: what the host sends for controller 0 (a
 * rumble, a trigger rumble, a light, a motion request) reaches the pad that holds the number and
 * not a pad that is only connected, or one that was refused a number because all sixteen were
 * taken.
 */
@RunWith(RobolectricTestRunner::class)
class UnassignedPadTest : FeedbackTestBase() {
    private val assignedVibrators = Hardware.vibratorManager(List(2) { Hardware.vibrator() })
    private val idleVibrators = Hardware.vibratorManager(List(2) { Hardware.vibrator() })

    /** A pad that sent its first button: it holds controller 0. */
    private fun FeedbackRig.assigned(features: DeviceFeatures, id: Int = 5): Pad =
        connectPad(id = id, features = features).also(::wake)

    /** A pad that is connected and has a context, but sent no controller input. */
    private fun FeedbackRig.idle(features: DeviceFeatures, id: Int = 6): Pad =
        connectPad(id = id, features = features).also { press(it, KeyEvent.KEYCODE_A) }

    @Test
    fun `a rumble for controller 0 does not reach a pad that sent no input`() {
        val rig = rig()
        rig.assigned(DeviceFeatures(vibratorManager = assignedVibrators))
        rig.idle(DeviceFeatures(vibratorManager = idleVibrators))

        rig.rumble(0x4000, 0xC000)

        assertThat(Hardware.manager(assignedVibrators).vibrations).hasSize(1)
        assertThat(Hardware.manager(idleVibrators).vibrations).isEmpty()
    }

    @Test
    fun `a rumble for controller 0 does not reach a pad that got no number because sixteen were taken`() {
        val rig = rig { multiController = true }
        rig.assigned(DeviceFeatures(vibratorManager = assignedVibrators), id = 1)
        (2..16).forEach { rig.assigned(DeviceFeatures(), id = it) }
        rig.assigned(DeviceFeatures(vibratorManager = idleVibrators), id = 17)

        rig.rumble(0x4000, 0xC000)

        assertThat(Hardware.manager(assignedVibrators).vibrations).hasSize(1)
        assertThat(Hardware.manager(idleVibrators).vibrations).isEmpty()
    }

    @Test
    fun `a trigger rumble for controller 0 does not reach a pad that sent no input`() {
        val assignedQuad = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
        val idleQuad = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
        val rig = rig()
        rig.assigned(DeviceFeatures(vibratorManager = assignedQuad))
        rig.idle(DeviceFeatures(vibratorManager = idleQuad))

        rig.rumbleTriggers(0x2000, 0x6000)

        assertThat(Hardware.manager(assignedQuad).vibrations).hasSize(1)
        assertThat(Hardware.manager(idleQuad).vibrations).isEmpty()
    }

    @Test
    fun `a colour for controller 0 does not reach a pad that sent no input`() {
        val assignedLights = Hardware.lightsManager(listOf(Hardware.rgbLight(1)))
        val idleLights = Hardware.lightsManager(listOf(Hardware.rgbLight(1)))
        val rig = rig()
        rig.assigned(DeviceFeatures(lightsManager = assignedLights))
        rig.idle(DeviceFeatures(lightsManager = idleLights))

        rig.led(0x10, 0x20, 0x30)
        rig.awaitBackground()

        assertThat(Hardware.lights(assignedLights).sessions).hasSize(1)
        assertThat(Hardware.lights(idleLights).sessions).isEmpty()
    }

    @Test
    fun `a motion request for controller 0 does not reach a pad that sent no input`() {
        val assignedSensors =
            Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val idleSensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = rig { gamepadMotionSensors = true }
        rig.assigned(DeviceFeatures(sensorManager = assignedSensors))
        rig.idle(DeviceFeatures(sensorManager = idleSensors))

        rig.motionState(GYRO, 100)

        assertThat(Hardware.sensors(assignedSensors).log).containsExactly("register 4 10000")
        assertThat(Hardware.sensors(idleSensors).log).isEmpty()
    }

    private companion object {
        const val GYRO = 2
    }
}
