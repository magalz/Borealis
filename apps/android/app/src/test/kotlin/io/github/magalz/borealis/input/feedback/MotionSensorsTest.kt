package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The motion sensors of a pad, as the host asks for them and as they report, on Android 13 and later. */
@RunWith(RobolectricTestRunner::class)
class MotionSensorsTest : FeedbackTestBase() {
    private val padSensors = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
    private val sensors
        get() = Hardware.sensors(padSensors)

    private fun sensorRig(configure: com.limelight.preferences.PreferenceConfiguration.() -> Unit = {}) =
        rig {
            gamepadMotionSensors = true
            configure()
        }

    private fun wake(
        rig: FeedbackRig,
        manager: SensorManager = padSensors,
        id: Int = FeedbackRig.PAD_ID,
        vendorId: Int = FeedbackRig.XBOX_VENDOR,
    ): Pad {
        val pad =
            rig.connectPad(id = id, vendorId = vendorId, features = DeviceFeatures(sensorManager = manager))
        rig.wake(pad)
        return pad
    }

    private fun gyro(value: Float) = value * DEGREES_PER_RADIAN

    @Test
    fun `a gyroscope request registers the gyroscope at the period of the rate`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, 100)

        assertThat(sensors.log).containsExactly("register 4 10000")
    }

    @Test
    fun `an accelerometer request registers the accelerometer`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(ACCEL, 50)

        assertThat(sensors.log).containsExactly("register 1 20000")
    }

    @Test
    fun `the period is a million microseconds over the rate, truncated`() {
        val rig = sensorRig()
        wake(rig)

        for (rate in listOf(1, 3, 150, 200)) {
            rig.motionState(GYRO, rate)
        }

        assertThat(sensors.log.filter { it.startsWith("register") })
            .containsExactly(
                "register 4 1000000",
                "register 4 333333",
                "register 4 6666",
                "register 4 5000",
            )
            .inOrder()
    }

    @Test
    fun `a rate above 200 is cut at 200`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, 201)
        rig.motionState(GYRO, 500)
        rig.motionState(GYRO, 32767)

        assertThat(sensors.log.filter { it.startsWith("register") })
            .containsExactly("register 4 5000", "register 4 5000", "register 4 5000")
    }

    @Test
    fun `a negative rate registers with a negative period`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, -5)
        rig.motionState(ACCEL, -8)

        assertThat(sensors.log).containsExactly("register 4 -200000", "register 1 -125000").inOrder()
    }

    @Test
    fun `a rate of zero registers nothing`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, 0)

        assertThat(sensors.log).isEmpty()
    }

    @Test
    fun `a rate of zero unregisters the sensor that was on`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)

        rig.motionState(GYRO, 0)

        assertThat(sensors.log).containsExactly("register 4 10000", "unregister 4").inOrder()
        assertThat(sensors.registrations).isEmpty()
    }

    @Test
    fun `a new rate replaces the listener`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        val first = sensors.listenerOf(Sensor.TYPE_GYROSCOPE)

        rig.motionState(GYRO, 50)

        assertThat(sensors.log)
            .containsExactly("register 4 10000", "unregister 4", "register 4 20000")
            .inOrder()
        assertThat(sensors.listenerOf(Sensor.TYPE_GYROSCOPE)).isNotSameInstanceAs(first)
    }

    @Test
    fun `the two sensors are independent`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)
        rig.motionState(GYRO, 0)

        assertThat(sensors.log)
            .containsExactly("register 4 10000", "register 1 20000", "unregister 4")
            .inOrder()
        assertThat(sensors.registrations.map { it.sensor.type }).containsExactly(1)
    }

    @Test
    fun `a motion type the handler does not know registers nothing`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(0, 100)
        rig.motionState(3, 100)

        assertThat(sensors.log).isEmpty()
    }

    @Test
    fun `a pad without the sensor registers nothing for it`() {
        val onlyAccel = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER)
        val rig = sensorRig()
        wake(rig, onlyAccel)

        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 100)

        assertThat(Hardware.sensors(onlyAccel).log).containsExactly("register 1 10000")
    }

    @Test
    fun `a request for another controller number reaches no sensor`() {
        val rig = sensorRig()
        wake(rig)

        rig.motionState(GYRO, 100, controller = 1)

        assertThat(sensors.log).isEmpty()
    }

    @Test
    fun `a stopped handler registers nothing`() {
        val rig = sensorRig()
        wake(rig)
        rig.handler.stop()
        val before = sensors.log.toList()

        rig.motionState(GYRO, 100)

        assertThat(sensors.log).isEqualTo(before)
    }

    @Test
    fun `only the first pad with the controller number is configured`() {
        val second = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = sensorRig()
        wake(rig, padSensors, id = 5)
        wake(rig, second, id = 6)

        rig.motionState(GYRO, 100)

        assertThat(sensors.log).containsExactly("register 4 10000")
        assertThat(Hardware.sensors(second).log).isEmpty()
    }

    @Test
    fun `a pad without a sensor manager is skipped for the next pad with the number`() {
        val none = Hardware.sensorManager()
        val rig = sensorRig()
        wake(rig, none, id = 5)
        wake(rig, padSensors, id = 6)

        rig.motionState(GYRO, 100)

        assertThat(sensors.log).containsExactly("register 4 10000")
    }

    @Test
    fun `the pad of another controller number is left alone`() {
        val second = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = sensorRig { multiController = true }
        wake(rig, padSensors, id = 5)
        wake(rig, second, id = 6)

        rig.motionState(GYRO, 100, controller = 1)

        assertThat(sensors.log).isEmpty()
        assertThat(Hardware.sensors(second).log).containsExactly("register 4 10000")
    }

    @Test
    fun `sensors the pad does not have leave the setting off`() {
        val none = Hardware.sensorManager()
        val rig = sensorRig()
        wake(rig, none)

        rig.motionState(GYRO, 100)

        assertThat(Hardware.sensors(none).log).isEmpty()
        assertThat(rig.phoneSensors.let { Hardware.sensors(it).log }).isEmpty()
    }

    @Test
    fun `the setting for motion sensors off keeps the pad sensors unused`() {
        val rig = rig { gamepadMotionSensors = false }
        wake(rig)

        rig.motionState(GYRO, 100)

        assertThat(sensors.log).isEmpty()
    }

    // ---- what the sensor reports

    @Test
    fun `a gyroscope reading goes to the host in degrees per second`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)

        sensors.deliver(Sensor.TYPE_GYROSCOPE, 1f, 2f, 3f)

        assertThat(rig.connection.motions)
            .containsExactly(Motion(0, GYRO, gyro(1f), gyro(2f), gyro(3f)))
        assertThat(rig.connection.motions.single().x).isEqualTo(57.2957795f)
        assertThat(rig.connection.motions.single().y).isEqualTo(114.591559f)
    }

    @Test
    fun `an accelerometer reading goes to the host as it is`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1.5f, -2f, 9.81f)

        assertThat(rig.connection.motions).containsExactly(Motion(0, ACCEL, 1.5f, -2f, 9.81f))
    }

    @Test
    fun `the display rotation does not touch the sensors of the pad itself`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        shadowOf(rig.activity.windowManager.defaultDisplay).setRotation(Surface.ROTATION_90)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions).containsExactly(Motion(0, ACCEL, 1f, 2f, 3f))
    }

    @Test
    fun `a change of accuracy is ignored`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        val listener = sensors.listenerOf(Sensor.TYPE_GYROSCOPE)

        listener.onAccuracyChanged(sensors.sensors.getValue(Sensor.TYPE_GYROSCOPE), 3)

        assertThat(rig.connection.motions).isEmpty()
        assertThat(sensors.log).containsExactly("register 4 10000")
    }

    @Test
    fun `a reading that repeats the last one is not sent again`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 4f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 3f, 4f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 2f, 3f, 4f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions.map { Triple(it.x, it.y, it.z) })
            .containsExactly(
                Triple(1f, 2f, 3f),
                Triple(1f, 2f, 4f),
                Triple(1f, 3f, 4f),
                Triple(2f, 3f, 4f),
                Triple(1f, 2f, 3f),
            )
            .inOrder()
    }

    @Test
    fun `a first reading of all zeros is taken for a repeat and dropped`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 0f, 0f, 0f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 0f, 0f, 1f)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 0f, 0f, 0f)

        assertThat(rig.connection.motions.map { it.z }).containsExactly(1f, 0f).inOrder()
    }

    @Test
    fun `each listener remembers its own last reading`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        rig.motionState(GYRO, 100)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        sensors.deliver(Sensor.TYPE_GYROSCOPE, 1f, 2f, 3f)

        assertThat(rig.connection.motions.map { it.type }).containsExactly(ACCEL, GYRO).inOrder()
    }

    @Test
    fun `a new listener starts without a last reading`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        rig.motionState(ACCEL, 50)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions).hasSize(2)
    }

    @Test
    fun `a reading reports the controller number the host asked for`() {
        val second = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = sensorRig { multiController = true }
        wake(rig, padSensors, id = 5)
        wake(rig, second, id = 6)
        rig.motionState(ACCEL, 100, controller = 1)

        Hardware.sensors(second).deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions).containsExactly(Motion(1, ACCEL, 1f, 2f, 3f))
    }

    @Test
    fun `readings are not sent while the chrome has the input`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        rig.handler.setCaptured(true)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions).isEmpty()
    }

    @Test
    fun `a reading that came while captured is sent when the capture ends`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        rig.handler.setCaptured(true)
        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        rig.handler.setCaptured(false)

        sensors.deliver(Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)

        assertThat(rig.connection.motions).containsExactly(Motion(0, ACCEL, 1f, 2f, 3f))
    }

    // ---- the capture, disabling and enabling

    @Test
    fun `a capture stops a gyroscope that was reporting`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)

        rig.handler.setCaptured(true)

        assertThat(rig.connection.motions).containsExactly(Motion(0, GYRO, 0f, 0f, 0f))
    }

    @Test
    fun `a capture sends no stop when only the accelerometer was on`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)

        rig.handler.setCaptured(true)

        assertThat(rig.connection.motions).isEmpty()
    }

    @Test
    fun `a capture sends no stop when no sensor was on`() {
        val rig = sensorRig()
        wake(rig)

        rig.handler.setCaptured(true)

        assertThat(rig.connection.motions).isEmpty()
    }

    @Test
    fun `disabling unregisters both sensors and stops the gyroscope on the host`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)

        rig.handler.disableSensors()

        assertThat(sensors.log.drop(2)).containsExactly("unregister 4", "unregister 1").inOrder()
        assertThat(rig.connection.motions).containsExactly(Motion(0, GYRO, 0f, 0f, 0f))
    }

    @Test
    fun `disabling leaves the attitude when only the accelerometer was on`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 50)

        rig.handler.disableSensors()

        assertThat(sensors.log).containsExactly("register 1 20000", "unregister 1").inOrder()
        assertThat(rig.connection.motions).isEmpty()
    }

    @Test
    fun `disabling twice does nothing the second time`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()

        rig.handler.disableSensors()

        assertThat(sensors.log).containsExactly("register 4 10000", "unregister 4").inOrder()
        assertThat(rig.connection.motions).hasSize(1)
    }

    @Test
    fun `enabling registers again after a second, at the rates the host asked for`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 50)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()
        sensors.log.clear()

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(999)
        rig.awaitBackground()
        assertThat(sensors.log).isEmpty()
        rig.advanceBackgroundBy(1)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 1 20000", "register 4 10000").inOrder()
    }

    @Test
    fun `enabling leaves alone a sensor whose rate is zero`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 0)
        rig.handler.disableSensors()
        sensors.log.clear()

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 4 10000")
    }

    @Test
    fun `enabling does not register a sensor that is already on`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 4 10000", "register 1 20000").inOrder()
    }

    @Test
    fun `enabling registers only the sensor that is off`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 50)
        rig.motionState(ACCEL, 0)
        rig.motionState(ACCEL, 25)
        rig.handler.disableSensors()
        rig.motionState(GYRO, 100)
        sensors.log.clear()

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 1 40000")
    }

    @Test
    fun `enabling registers nothing when the host never asked for a sensor`() {
        val rig = sensorRig()
        wake(rig)

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).isEmpty()
    }

    @Test
    fun `disabling cancels an enabling that was waiting`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()
        rig.handler.enableSensors()

        rig.handler.disableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 4 10000", "unregister 4").inOrder()
    }

    @Test
    fun `a request cancels an enabling that was waiting`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(ACCEL, 100)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()
        rig.handler.enableSensors()
        sensors.log.clear()

        rig.motionState(GYRO, 50)
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 4 20000")
    }

    @Test
    fun `a stopped handler does not enable`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.handler.disableSensors()
        rig.handler.stop()
        val before = sensors.log.toList()

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).isEqualTo(before)
    }

    @Test
    fun `enabling reaches every pad`() {
        val second = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)
        val rig = sensorRig { multiController = true }
        wake(rig, padSensors, id = 5)
        wake(rig, second, id = 6)
        rig.motionState(GYRO, 100, controller = 0)
        rig.motionState(GYRO, 50, controller = 1)
        rig.handler.disableSensors()
        sensors.log.clear()
        Hardware.sensors(second).log.clear()

        rig.handler.enableSensors()
        rig.advanceBackgroundBy(1000)
        rig.awaitBackground()

        assertThat(sensors.log).containsExactly("register 4 10000")
        assertThat(Hardware.sensors(second).log).containsExactly("register 4 20000")
    }

    @Test
    fun `stopping unregisters the sensors of every pad`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.motionState(ACCEL, 100)

        rig.handler.stop()

        assertThat(sensors.registrations).isEmpty()
    }

    @Test
    fun `a pad that goes away unregisters its sensors`() {
        val rig = sensorRig()
        val pad = wake(rig)
        rig.motionState(GYRO, 100)

        rig.handler.onInputDeviceRemoved(pad.id)

        assertThat(sensors.registrations).isEmpty()
    }

    @Test
    fun `disabling after a stop still sends the stop of the gyroscope`() {
        val rig = sensorRig()
        wake(rig)
        rig.motionState(GYRO, 100)
        rig.handler.stop()

        rig.handler.disableSensors()

        assertThat(rig.connection.motions).containsExactly(Motion(0, GYRO, 0f, 0f, 0f))
    }

    // ---- what the pad reports on arrival

    @Test
    fun `the pad reports the sensors it has`() {
        val rig = sensorRig()
        wake(rig)

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_ACCEL.toInt()).isEqualTo(MoonBridge.LI_CCAP_ACCEL.toInt())
        assertThat(capabilities and MoonBridge.LI_CCAP_GYRO.toInt()).isEqualTo(MoonBridge.LI_CCAP_GYRO.toInt())
    }

    @Test
    fun `a pad with only a gyroscope reports only the gyroscope`() {
        val onlyGyro = Hardware.sensorManager(Sensor.TYPE_GYROSCOPE)
        val rig = sensorRig()
        wake(rig, onlyGyro)

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_ACCEL.toInt()).isEqualTo(0)
        assertThat(capabilities and MoonBridge.LI_CCAP_GYRO.toInt()).isEqualTo(MoonBridge.LI_CCAP_GYRO.toInt())
    }

    @Test
    fun `a pad with only an accelerometer reports only the accelerometer`() {
        val onlyAccel = Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER)
        val rig = sensorRig()
        wake(rig, onlyAccel)

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_ACCEL.toInt()).isEqualTo(MoonBridge.LI_CCAP_ACCEL.toInt())
        assertThat(capabilities and MoonBridge.LI_CCAP_GYRO.toInt()).isEqualTo(0)
    }

    @Test
    fun `a pad without sensors reports none`() {
        val rig = sensorRig()
        rig.wake(rig.connectPad())

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and (MoonBridge.LI_CCAP_ACCEL.toInt() or MoonBridge.LI_CCAP_GYRO.toInt()))
            .isEqualTo(0)
    }

    @Test
    fun `a pad that is not a PlayStation pad and has sensors is reported with an unknown type`() {
        val rig = sensorRig()
        wake(rig, vendorId = FeedbackRig.XBOX_VENDOR)

        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
    }

    @Test
    fun `a Nintendo pad that has sensors is reported with an unknown type too`() {
        val rig = sensorRig()
        wake(rig, vendorId = FeedbackRig.NINTENDO_VENDOR)

        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
    }

    @Test
    fun `a PlayStation pad that has sensors keeps its type`() {
        val rig = sensorRig()
        wake(rig, vendorId = FeedbackRig.SONY_VENDOR)

        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
    }

    @Test
    fun `a pad without sensors keeps its type`() {
        val rig = sensorRig()
        rig.wake(rig.connectPad(vendorId = FeedbackRig.XBOX_VENDOR))

        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_XBOX.toInt())
    }

    @Test
    fun `the change of type is announced with a toast`() {
        val rig = sensorRig()
        wake(rig, vendorId = FeedbackRig.XBOX_VENDOR)

        assertThat(org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
            .isEqualTo(rig.activity.getString(com.limelight.R.string.toast_controller_type_changed))
    }

    @Test
    fun `no toast for a pad that keeps its type`() {
        val rig = sensorRig()
        wake(rig, vendorId = FeedbackRig.SONY_VENDOR)

        assertThat(org.robolectric.shadows.ShadowToast.shownToastCount()).isEqualTo(0)
    }

    private companion object {
        const val ACCEL = 1
        const val GYRO = 2
        const val DEGREES_PER_RADIAN = 57.2957795f
    }
}
