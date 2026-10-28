package io.github.magalz.borealis.input.feedback

import android.hardware.BatteryState
import android.hardware.Sensor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * When Android says a device changed (a touchpad that gains an axis, a pad that comes back from
 * pointer capture), the handler makes a new context for it and moves what the old one held: the
 * controller number, the sensor rates, the LED colour and its session. The host must not notice, so
 * no controller is added or removed, but the new context reads the battery again, repaints the LED
 * and, a second later, turns the sensors back on.
 */
@RunWith(RobolectricTestRunner::class)
class ControllerMigrationTest : FeedbackTestBase() {
    private val battery =
        Hardware.battery(status = BatteryState.STATUS_DISCHARGING, capacity = 0.5f)
    private val sensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
    private val lights = Hardware.lightsManager(listOf(Hardware.rgbLight(1)))
    private val own = Hardware.vibrator()

    private val padFeatures
        get() =
            DeviceFeatures(
                batteryState = battery,
                sensorManager = sensors,
                lightsManager = lights,
                vibrator = own,
            )

    private fun migrationRig(
        configure: com.limelight.preferences.PreferenceConfiguration.() -> Unit = {}
    ) = rig {
        gamepadMotionSensors = true
        configure()
    }

    private fun events() = batteryEvents.toList()

    private fun session() = Hardware.session(Hardware.lights(lights).sessions.single())

    private fun change(rig: FeedbackRig, pad: Pad) {
        rig.handler.onInputDeviceChanged(pad.id)
        rig.awaitBackground()
    }

    @Test
    fun `the new context reads the battery again`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        assertThat(events()).hasSize(1)

        change(rig, pad)

        assertThat(events()).containsExactly(BatteryEvent(0, 2, 50), BatteryEvent(0, 2, 50))
    }

    @Test
    fun `the old context stops polling the battery`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        change(rig, pad)

        Hardware.batteryState(battery).batteryCapacity = 0.25f
        rig.advanceBackgroundBy(120_000)
        rig.awaitBackground()

        assertThat(events().map { it.percentage }).containsExactly(50, 50, 25).inOrder()
    }

    @Test
    fun `the sensors that were on are turned on again after a second`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.motionState(2, 100)
        rig.motionState(1, 50)

        change(rig, pad)
        val afterChange = Hardware.sensors(sensors).log.toList()
        rig.advanceBackgroundBy(999)
        rig.awaitBackground()
        assertThat(Hardware.sensors(sensors).log).isEqualTo(afterChange)
        rig.advanceBackgroundBy(1)
        rig.awaitBackground()

        assertThat(afterChange.drop(2)).containsExactly("unregister 4", "unregister 1").inOrder()
        assertThat(Hardware.sensors(sensors).log.drop(afterChange.size))
            .containsExactly("register 1 20000", "register 4 10000")
            .inOrder()
    }

    @Test
    fun `a sensor the host switched off stays off`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.motionState(2, 100)
        rig.motionState(2, 0)

        change(rig, pad)
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(Hardware.sensors(sensors).log)
            .containsExactly("register 4 10000", "unregister 4")
            .inOrder()
    }

    @Test
    fun `the old sensor listeners are given back`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.motionState(2, 100)

        change(rig, pad)

        assertThat(Hardware.sensors(sensors).registrations).isEmpty()
    }

    @Test
    fun `the borrowed sensors of the phone are borrowed again`() {
        val rig = rig {
            gamepadMotionSensors = false
            gamepadMotionSensorsFallbackToDevice = true
        }
        val pad = rig.connectPad(features = DeviceFeatures())
        rig.wake(pad)
        rig.motionState(2, 100)
        val phone = Hardware.sensors(rig.phoneSensors)

        change(rig, pad)
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(phone.log)
            .containsExactly("register 4 10000", "unregister 4", "register 4 10000")
            .inOrder()
    }

    @Test
    fun `the colour of the LED is painted again on the same session`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.led(0x80, 0x10, 0xFF)
        rig.awaitBackground()

        change(rig, pad)

        assertThat(Hardware.lights(lights).sessions).hasSize(1)
        assertThat(session().requests)
            .containsExactly(
                listOf(LightColor(1, 0xFF8010FF.toInt())),
                listOf(LightColor(1, 0xFF8010FF.toInt())),
            )
        assertThat(session().closed).isFalse()
    }

    @Test
    fun `the LED is left alone when the host never set a colour`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)

        change(rig, pad)

        assertThat(Hardware.lights(lights).sessions).isEmpty()
    }

    @Test
    fun `the session now belongs to the new context and closes with the handler`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.led(1, 2, 3)
        rig.awaitBackground()
        change(rig, pad)

        rig.handler.stop()

        assertThat(session().closed).isTrue()
    }

    @Test
    fun `a new colour after the change goes to the same session`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.led(1, 2, 3)
        rig.awaitBackground()
        change(rig, pad)

        rig.led(4, 5, 6)
        rig.awaitBackground()

        assertThat(Hardware.lights(lights).sessions).hasSize(1)
        assertThat(session().requests.last()).containsExactly(LightColor(1, 0xFF040506.toInt()))
    }

    @Test
    fun `the host sees no controller come or go`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        val packets = rig.connection.packets.size

        change(rig, pad)

        assertThat(rig.connection.arrivals).hasSize(1)
        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `the pad keeps its controller number`() {
        val second = Hardware.vibrator()
        val rig = migrationRig { multiController = true }
        rig.wake(rig.connectPad(id = 5))
        val pad = rig.connectPad(id = 6, features = DeviceFeatures(vibrator = second))
        rig.wake(pad)

        change(rig, pad)
        rig.rumble(0x6400, 0x6400, controller = 1)

        assertThat(rig.connection.arrivals.map { it.controller }).containsExactly(0, 1).inOrder()
        assertThat(Hardware.calls(second).filterIsInstance<VibratorCall.Play>()).hasSize(1)
    }

    @Test
    fun `the old context cancels its vibration`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.rumble(0x6400, 0x6400)

        change(rig, pad)

        assertThat(Hardware.calls(own).last()).isEqualTo(VibratorCall.Cancel)
    }

    @Test
    fun `the new context rumbles with the vibrator of the device`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        change(rig, pad)

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(own).filterIsInstance<VibratorCall.Play>()).hasSize(1)
    }

    @Test
    fun `a device that has no context needs nothing`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)

        rig.handler.onInputDeviceChanged(pad.id)
        rig.awaitBackground()

        assertThat(events()).isEmpty()
        assertThat(rig.connection.arrivals).isEmpty()
    }

    @Test
    fun `a pad that goes away after a change is released`() {
        val rig = migrationRig()
        val pad = rig.connectPad(features = padFeatures)
        rig.wake(pad)
        rig.led(1, 2, 3)
        rig.awaitBackground()
        change(rig, pad)

        rig.handler.onInputDeviceRemoved(pad.id)

        assertThat(session().closed).isTrue()
    }
}
