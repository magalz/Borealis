package io.github.magalz.borealis.input.devices

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Vibrator
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadow.api.Shadow

/**
 * The context of a device takes what rumbles, senses motion and shows colors from the hardware the
 * framework reports for it: the device's own vibrators first, then, for a device that is built in,
 * the vibrator of the phone. The sensors and lights came with Android 12, and some of them only
 * count for some vendors before Android 13 and 14.
 */
class ContextHardwareTest : DevicesTestBase() {
    private val sony = 0x054c
    private val nintendo = 0x057e
    private val microsoft = 0x045e

    private fun contextOf(
        device: Pad,
        configure: PreferenceConfiguration.() -> Unit = {},
    ): Pair<DevicesRig, ContextView> {
        val rig = rig(devices = listOf(device), configure = configure)
        return rig to rig.touch(device)
    }

    private fun systemVibrator(): Vibrator =
        RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)

    private fun addSensors(vararg types: Int) {
        val manager = RuntimeEnvironment.getApplication().getSystemService(SensorManager::class.java)
        types.forEach { shadowOf(manager).addSensor(SensorBuilder.newBuilder().setType(it).build()) }
    }

    private fun ContextView.ownsVibratorManagerOf(device: Pad): Boolean =
        Shadow.extract<ShadowDeviceVibratorManager>(get("vibratorManager")!!).deviceId == device.id

    @Test
    fun `a device with four vibrators with amplitude control rumbles through its manager`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 4))

        val (_, context) = contextOf(device)

        assertThat(context.ownsVibratorManagerOf(device)).isTrue()
        assertThat(context.bool("quadVibrators")).isTrue()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `a device with two vibrators with amplitude control rumbles through its manager`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 2))

        val (_, context) = contextOf(device)

        assertThat(context.ownsVibratorManagerOf(device)).isTrue()
        assertThat(context.bool("quadVibrators")).isFalse()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `vibrators without amplitude control are not used through the manager`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 4, amplitudeControl = false))

        val (_, context) = contextOf(device)

        assertThat(context["vibratorManager"]).isNull()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `only two or four vibrators make a pad`() {
        for ((id, count) in listOf(1 to 1, 2 to 3, 3 to 5)) {
            val device = pad(id)
            DeviceHardware.set(device.id, Hardware(vibrators = count))

            val (_, context) = contextOf(device)

            assertThat(context["vibratorManager"]).isNull()
        }
    }

    @Test
    fun `four vibrators win over a plain vibrator`() {
        val device = pad(1, hasVibrator = true)
        DeviceHardware.set(device.id, Hardware(vibrators = 4))

        val (_, context) = contextOf(device)

        assertThat(context.bool("quadVibrators")).isTrue()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `two vibrators win over a plain vibrator`() {
        val device = pad(1, hasVibrator = true)
        DeviceHardware.set(device.id, Hardware(vibrators = 2))

        val (_, context) = contextOf(device)

        assertThat(context.ownsVibratorManagerOf(device)).isTrue()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `a device with a plain vibrator rumbles through it`() {
        val (_, context) = contextOf(pad(1, hasVibrator = true))

        assertThat(context["vibrator"]).isNotNull()
        assertThat(context["vibratorManager"]).isNull()
        assertThat(context.bool("quadVibrators")).isFalse()
    }

    @Test
    fun `an external pad without a vibrator does not borrow the one of the phone`() {
        val (_, context) = contextOf(pad(1))

        assertThat(context["vibrator"]).isNull()
        assertThat(context["vibratorManager"]).isNull()
    }

    @Test
    fun `an internal pad borrows the vibrator of the phone`() {
        val (rig, context) = contextOf(pad(1, external = false))

        assertThat(context["vibrator"]).isSameInstanceAs(field(rig.handler, "deviceVibrator"))
        assertThat(context["vibratorManager"]).isNull()
    }

    @Test
    fun `an internal pad on a phone without a vibrator has none`() {
        shadowOf(systemVibrator()).setHasVibrator(false)

        val (_, context) = contextOf(pad(1, external = false))

        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `an internal pad prefers the four vibrators of the phone`() {
        DeviceHardware.systemVibrators = 4
        shadowOf(systemVibrator()).setHasAmplitudeControl(true)

        val (rig, context) = contextOf(pad(1, external = false))

        assertThat(context["vibratorManager"]).isSameInstanceAs(field(rig.handler, "deviceVibratorManager"))
        assertThat(context.bool("quadVibrators")).isTrue()
        assertThat(context["vibrator"]).isNull()
    }

    @Test
    fun `an internal pad takes the two vibrators of the phone`() {
        DeviceHardware.systemVibrators = 2
        shadowOf(systemVibrator()).setHasAmplitudeControl(true)

        val (rig, context) = contextOf(pad(1, external = false))

        assertThat(context["vibratorManager"]).isSameInstanceAs(field(rig.handler, "deviceVibratorManager"))
        assertThat(context.bool("quadVibrators")).isFalse()
    }

    @Test
    fun `the vibrators of the phone are not used without amplitude control`() {
        DeviceHardware.systemVibrators = 4
        shadowOf(systemVibrator()).setHasAmplitudeControl(false)

        val (rig, context) = contextOf(pad(1, external = false))

        assertThat(context["vibratorManager"]).isNull()
        assertThat(context["vibrator"]).isSameInstanceAs(field(rig.handler, "deviceVibrator"))
    }

    @Test
    fun `an external pad never uses the vibrators of the phone`() {
        DeviceHardware.systemVibrators = 4
        shadowOf(systemVibrator()).setHasAmplitudeControl(true)

        val (_, context) = contextOf(pad(1))

        assertThat(context["vibratorManager"]).isNull()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 a vibrator manager is never used`() {
        val device = pad(1, hasVibrator = true)
        DeviceHardware.set(device.id, Hardware(vibrators = 4))

        val (_, context) = contextOf(device)

        assertThat(context["vibratorManager"]).isNull()
        assertThat(context["vibrator"]).isNotNull()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 an internal pad borrows the vibrator of the phone`() {
        val (rig, context) = contextOf(pad(1, external = false))

        assertThat(context["vibrator"]).isSameInstanceAs(field(rig.handler, "deviceVibrator"))
    }

    @Test
    fun `a pad with motion sensors uses its own sensor manager when the setting is on`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)
        val device = pad(1)

        val (_, context) = contextOf(device) { gamepadMotionSensors = true }

        assertThat(context["sensorManager"]).isSameInstanceAs(DeviceHardware.sensorManagerOf(device.id))
    }

    @Test
    fun `a gyroscope alone is enough`() {
        addSensors(Sensor.TYPE_GYROSCOPE)
        val device = pad(1)

        val (_, context) = contextOf(device) { gamepadMotionSensors = true }

        assertThat(context["sensorManager"]).isSameInstanceAs(DeviceHardware.sensorManagerOf(device.id))
    }

    @Test
    fun `without the setting a pad has no sensor manager`() {
        addSensors(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)

        val (_, context) = contextOf(pad(1))

        assertThat(context["sensorManager"]).isNull()
    }

    @Test
    fun `a pad with no motion sensors has no sensor manager`() {
        addSensors(Sensor.TYPE_LIGHT)

        val (_, context) = contextOf(pad(1)) { gamepadMotionSensors = true }

        assertThat(context["sensorManager"]).isNull()
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `on Android 12 only Sony and Nintendo pads are asked for their sensors`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)

        val sonyPad = pad(1, vendorId = sony)
        val nintendoPad = pad(2, vendorId = nintendo)
        val microsoftPad = pad(3, vendorId = microsoft)

        assertThat(contextOf(sonyPad) { gamepadMotionSensors = true }.second["sensorManager"]).isNotNull()
        assertThat(contextOf(nintendoPad) { gamepadMotionSensors = true }.second["sensorManager"])
            .isNotNull()
        assertThat(contextOf(microsoftPad) { gamepadMotionSensors = true }.second["sensorManager"])
            .isNull()
    }

    @Test
    @Config(sdk = [ANDROID_13])
    fun `on Android 13 every pad is asked for its sensors`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)

        val (_, context) = contextOf(pad(1, vendorId = microsoft)) { gamepadMotionSensors = true }

        assertThat(context["sensorManager"]).isNotNull()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 the sensors of a pad are not asked for`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)

        val (_, context) = contextOf(pad(1, vendorId = sony)) { gamepadMotionSensors = true }

        assertThat(context["sensorManager"]).isNull()
    }

    @Test
    fun `a light that shows colors makes an RGB LED`() {
        val device = pad(1, vendorId = microsoft)
        DeviceHardware.set(device.id, Hardware(rgbLights = 1, plainLights = 1))

        val (_, context) = contextOf(device)

        assertThat(context.bool("hasRgbLed")).isTrue()
    }

    @Test
    fun `lights that cannot show colors make no RGB LED`() {
        val device = pad(1, vendorId = sony)
        DeviceHardware.set(device.id, Hardware(plainLights = 2))

        val (_, context) = contextOf(device)

        assertThat(context.bool("hasRgbLed")).isFalse()
    }

    @Test
    fun `a pad without lights has no RGB LED`() {
        val (_, context) = contextOf(pad(1, vendorId = sony))

        assertThat(context.bool("hasRgbLed")).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_13])
    fun `before Android 14 only a Sony pad can have an RGB LED`() {
        val sonyPad = pad(1, vendorId = sony)
        val microsoftPad = pad(2, vendorId = microsoft)
        DeviceHardware.set(sonyPad.id, Hardware(rgbLights = 1))
        DeviceHardware.set(microsoftPad.id, Hardware(rgbLights = 1))

        assertThat(contextOf(sonyPad).second.bool("hasRgbLed")).isTrue()
        assertThat(contextOf(microsoftPad).second.bool("hasRgbLed")).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_13])
    fun `before Android 14 every light claims to show colors`() {
        // Light.hasRgbControl() was always true, which is why the handler trusts only Sony
        val device = pad(1, vendorId = sony)
        DeviceHardware.set(device.id, Hardware(plainLights = 1))

        assertThat(contextOf(device).second.bool("hasRgbLed")).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `on Android 12 a Sony pad with a light has an RGB LED`() {
        val device = pad(1, vendorId = sony)
        DeviceHardware.set(device.id, Hardware(rgbLights = 1))

        assertThat(contextOf(device).second.bool("hasRgbLed")).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 a pad has no RGB LED`() {
        val device = pad(1, vendorId = sony)
        DeviceHardware.set(device.id, Hardware(rgbLights = 1))

        assertThat(contextOf(device).second.bool("hasRgbLed")).isFalse()
    }

    private companion object {
        const val ANDROID_11 = 30
        const val ANDROID_12 = 31
        const val ANDROID_13 = 33
    }
}
