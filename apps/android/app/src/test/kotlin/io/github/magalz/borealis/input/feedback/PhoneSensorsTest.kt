package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import android.view.Surface
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The phone's own sensors standing in for a pad that has none, when the setting allows it and the
 * pad is player one. The phone is held in whatever way the display is turned, so every reading is
 * remapped to the axes of the controller, and each rotation does it differently.
 */
// Abstract so that it does not run alone: the subclasses carry the Robolectric runner and the SDK
// level.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class PhoneSensorsContract : FeedbackTestBase() {
    private fun phoneRig(
        configure: com.limelight.preferences.PreferenceConfiguration.() -> Unit = {}
    ) = rig {
        gamepadMotionSensors = false
        gamepadMotionSensorsFallbackToDevice = true
        configure()
    }

    private fun FeedbackRig.rotate(rotation: Int) =
        shadowOf(activity.windowManager.defaultDisplay).setRotation(rotation)

    private fun log(rig: FeedbackRig) = Hardware.sensors(rig.phoneSensors).log

    private fun deliver(rig: FeedbackRig, type: Int, x: Float, y: Float, z: Float) =
        Hardware.sensors(rig.phoneSensors).deliver(type, x, y, z)

    @Test
    fun `a pad without sensors borrows the sensors of the phone`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())

        rig.motionState(GYRO, 100)

        assertThat(log(rig)).containsExactly("register 4 10000")
    }

    @Test
    fun `a pad that is not external borrows them too`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad(external = false))

        rig.motionState(ACCEL, 100)

        assertThat(log(rig)).containsExactly("register 1 10000")
    }

    @Test
    fun `the pad reports the sensors of the phone`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_ACCEL.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_ACCEL.toInt())
        assertThat(capabilities and MoonBridge.LI_CCAP_GYRO.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_GYRO.toInt())
    }

    @Test
    fun `the pad reports only the sensors the phone has`() {
        val rig =
            rig(phoneSensors = Hardware.sensorManager(Sensor.TYPE_GYROSCOPE)) {
                gamepadMotionSensorsFallbackToDevice = true
            }
        rig.wake(rig.connectPad())

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_ACCEL.toInt()).isEqualTo(0)
        assertThat(capabilities and MoonBridge.LI_CCAP_GYRO.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_GYRO.toInt())
    }

    @Test
    fun `the pad does not claim sensors when the setting is off`() {
        val rig = rig { gamepadMotionSensorsFallbackToDevice = false }
        rig.wake(rig.connectPad())

        rig.motionState(GYRO, 100)

        assertThat(log(rig)).isEmpty()
        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(
                capabilities and
                    (MoonBridge.LI_CCAP_ACCEL.toInt() or MoonBridge.LI_CCAP_GYRO.toInt())
            )
            .isEqualTo(0)
    }

    @Test
    fun `a pad of another controller number does not borrow them`() {
        val rig = phoneRig { multiController = true }
        rig.wake(rig.connectPad(id = 5))
        rig.wake(rig.connectPad(id = 6))

        rig.motionState(GYRO, 100, controller = 1)

        assertThat(log(rig)).isEmpty()
    }

    @Test
    fun `the first pad borrows them and the second does not`() {
        val rig = phoneRig { multiController = true }
        rig.wake(rig.connectPad(id = 5))
        rig.wake(rig.connectPad(id = 6))

        rig.motionState(GYRO, 100, controller = 0)

        assertThat(log(rig)).containsExactly("register 4 10000")
    }

    @Test
    fun `a pad that borrows the sensors is reported with an unknown type and a toast`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad(vendorId = FeedbackRig.XBOX_VENDOR))

        assertThat(rig.connection.arrivals.single().type)
            .isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
        assertThat(org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
            .isEqualTo(rig.activity.getString(com.limelight.R.string.toast_controller_type_changed))
    }

    @Test
    fun `a PlayStation pad that borrows the sensors keeps its type`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad(vendorId = FeedbackRig.SONY_VENDOR))

        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
    }

    @Test
    fun `a gyroscope reading goes to the host in degrees per second`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())
        rig.rotate(Surface.ROTATION_270)
        rig.motionState(GYRO, 100)

        deliver(rig, Sensor.TYPE_GYROSCOPE, 1f, 2f, 3f)

        // Turned 270 degrees the axes are x -> y, y -> z, z -> x without a change of sign
        assertThat(rig.connection.motions)
            .containsExactly(Motion(0, GYRO, 2f * RAD, 3f * RAD, 1f * RAD))
    }

    @Test
    fun `an accelerometer reading is remapped for each rotation of the display`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())
        rig.motionState(ACCEL, 100)

        rig.rotate(Surface.ROTATION_0)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        rig.rotate(Surface.ROTATION_90)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 4f, 5f, 6f)
        rig.rotate(Surface.ROTATION_180)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 7f, 8f, 9f)
        rig.rotate(Surface.ROTATION_270)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 10f, 11f, 12f)

        assertThat(rig.connection.motions.map { Triple(it.x, it.y, it.z) })
            .containsExactly(
                Triple(1f, 3f, -2f),
                Triple(-5f, 6f, -4f),
                Triple(-7f, 9f, 8f),
                Triple(11f, 12f, 10f),
            )
            .inOrder()
    }

    @Test
    fun `a gyroscope reading is remapped for each rotation and converted`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())
        rig.motionState(GYRO, 100)

        rig.rotate(Surface.ROTATION_0)
        deliver(rig, Sensor.TYPE_GYROSCOPE, 1f, 2f, 3f)
        rig.rotate(Surface.ROTATION_90)
        deliver(rig, Sensor.TYPE_GYROSCOPE, 4f, 5f, 6f)
        rig.rotate(Surface.ROTATION_180)
        deliver(rig, Sensor.TYPE_GYROSCOPE, 7f, 8f, 9f)
        rig.rotate(Surface.ROTATION_270)
        deliver(rig, Sensor.TYPE_GYROSCOPE, 10f, 11f, 12f)

        assertThat(rig.connection.motions.map { Triple(it.x, it.y, it.z) })
            .containsExactly(
                Triple(1f * RAD, 3f * RAD, -2f * RAD),
                Triple(-5f * RAD, 6f * RAD, -4f * RAD),
                Triple(-7f * RAD, 9f * RAD, 8f * RAD),
                Triple(11f * RAD, 12f * RAD, 10f * RAD),
            )
            .inOrder()
    }

    @Test
    fun `the rotation is read for every reading`() {
        val rig = phoneRig()
        rig.wake(rig.connectPad())
        rig.motionState(ACCEL, 100)

        rig.rotate(Surface.ROTATION_0)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 3f)
        rig.rotate(Surface.ROTATION_180)
        deliver(rig, Sensor.TYPE_ACCELEROMETER, 1f, 2f, 4f)

        assertThat(rig.connection.motions.map { Triple(it.x, it.y, it.z) })
            .containsExactly(Triple(1f, 3f, -2f), Triple(-1f, 4f, 2f))
            .inOrder()
    }

    private companion object {
        const val ACCEL = 1
        const val GYRO = 2
        const val RAD = 57.2957795f
    }
}

/** Android 13 and later. */
@RunWith(RobolectricTestRunner::class) class PhoneSensorsTest : PhoneSensorsContract()

/** Android 11, which has no sensors on input devices: the phone's are the only ones there are. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PhoneSensorsAndroid11Test : PhoneSensorsContract()
