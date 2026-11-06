package io.github.magalz.borealis.input.ch4

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Vibrator
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.field
import io.github.magalz.borealis.input.devices.pad
import io.github.magalz.borealis.testing.ANDROID_12
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorBuilder

/**
 * What `createInputDeviceContextForDevice` takes from the hardware of a device that
 * `ContextHardwareTest` leaves open: the vibrator of a built-in pad comes from the pad before it
 * comes from the phone, and on Android 12, where the sensors of a pad are only asked for when its
 * vendor can have them, the setting still decides.
 */
class HardwareChoiceTest : DevicesTestBase() {
    private val sony = 0x054c
    private val nintendo = 0x057e

    @Test
    fun `a built-in pad with a vibrator of its own uses it when the phone has none`() {
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java))
            .setHasVibrator(false)
        val device =
            pad(1) {
                external = false
                hasVibrator = true
            }

        val context = rig(devices = listOf(device)).touch(device)

        assertThat(context["vibrator"]).isNotNull()
        assertThat(context["vibratorManager"]).isNull()
    }

    @Test
    fun `a built-in pad with a vibrator of its own does not take the one of the phone`() {
        val device =
            pad(1) {
                external = false
                hasVibrator = true
            }
        val rig = rig(devices = listOf(device))

        val context = rig.touch(device)

        assertThat(context["vibrator"]).isNotNull()
        assertThat(context["vibrator"]).isNotSameInstanceAs(field(rig.handler, "deviceVibrator"))
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `on Android 12 a Sony or Nintendo pad has no sensor manager unless the setting is on`() {
        val manager =
            RuntimeEnvironment.getApplication().getSystemService(SensorManager::class.java)
        shadowOf(manager)
            .addSensor(SensorBuilder.newBuilder().setType(Sensor.TYPE_ACCELEROMETER).build())
        val sonyPad = pad(1) { vendorId = sony }
        val nintendoPad = pad(2) { vendorId = nintendo }

        val rig = rig(devices = listOf(sonyPad, nintendoPad))

        assertThat(rig.touch(sonyPad)["sensorManager"]).isNull()
        assertThat(rig.touch(nintendoPad)["sensorManager"]).isNull()
    }
}
