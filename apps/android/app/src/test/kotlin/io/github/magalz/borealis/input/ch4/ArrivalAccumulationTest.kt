package io.github.magalz.borealis.input.ch4

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.input.devices.Axis
import io.github.magalz.borealis.input.devices.DeviceHardware
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.Hardware
import io.github.magalz.borealis.input.devices.LEFT_STICK
import io.github.magalz.borealis.input.devices.NativePads
import io.github.magalz.borealis.input.devices.PAD_SOURCES
import io.github.magalz.borealis.input.devices.pad
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.ShadowToast

/**
 * What the arrival report of a pad says when it has everything: the capabilities and the buttons
 * that each step of `sendControllerArrival` adds are added to one another, none replaces what an
 * earlier one found. `ArrivalReportTest` has each flag on its own.
 */
class ArrivalAccumulationTest : DevicesTestBase() {
    private val sony = 0x054c
    private val nintendo = 0x057e
    private val product = 0x09cc

    private fun caps(vararg flags: Short): Int = flags.fold(0) { sum, flag -> sum or flag.toInt() }

    @Test
    fun `a pad with everything has every capability and every button the steps found`() {
        NativePads.paddles(sony, product)
        NativePads.shares(sony, product)
        val manager =
            RuntimeEnvironment.getApplication().getSystemService(SensorManager::class.java)
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach {
            shadowOf(manager).addSensor(SensorBuilder.newBuilder().setType(it).build())
        }
        val device =
            pad(1) {
                vendorId = sony
                productId = product
                sources = PAD_SOURCES or InputDevice.SOURCE_TOUCHPAD
                keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START)
                axes =
                    LEFT_STICK +
                        listOf(
                                MotionEvent.AXIS_LTRIGGER,
                                MotionEvent.AXIS_RTRIGGER,
                                MotionEvent.AXIS_HAT_X,
                                MotionEvent.AXIS_HAT_Y,
                            )
                            .map { Axis(it) }
            }
        DeviceHardware.set(device.id, Hardware(vibrators = 4, rgbLights = 1))
        val rig = rig(devices = listOf(device)) { gamepadMotionSensors = true }

        rig.press(device, KeyEvent.KEYCODE_BUTTON_A)

        val arrival = rig.connection.arrivals.single()
        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
        assertThat(arrival.supportedButtons)
            .isEqualTo(
                ControllerPacket.A_FLAG or
                    ControllerPacket.PLAY_FLAG or
                    ControllerPacket.PADDLE1_FLAG or
                    ControllerPacket.PADDLE2_FLAG or
                    ControllerPacket.PADDLE3_FLAG or
                    ControllerPacket.PADDLE4_FLAG or
                    ControllerPacket.MISC_FLAG or
                    ControllerPacket.LEFT_FLAG or
                    ControllerPacket.RIGHT_FLAG or
                    ControllerPacket.UP_FLAG or
                    ControllerPacket.DOWN_FLAG or
                    ControllerPacket.TOUCHPAD_FLAG
            )
        assertThat(arrival.capabilities)
            .isEqualTo(
                caps(
                    MoonBridge.LI_CCAP_RUMBLE,
                    MoonBridge.LI_CCAP_TRIGGER_RUMBLE,
                    MoonBridge.LI_CCAP_BATTERY_STATE,
                    MoonBridge.LI_CCAP_RGB_LED,
                    MoonBridge.LI_CCAP_ANALOG_TRIGGERS,
                    MoonBridge.LI_CCAP_ACCEL,
                    MoonBridge.LI_CCAP_GYRO,
                    MoonBridge.LI_CCAP_TOUCHPAD,
                )
            )
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `a Nintendo pad is not looked up in the native list`() {
        val device =
            pad(1) {
                vendorId = nintendo
                productId = 0x2009
            }
        val rig = rig(devices = listOf(device))

        rig.press(device, KeyEvent.KEYCODE_BUTTON_A)

        assertThat(rig.connection.arrivals.single().type)
            .isEqualTo(MoonBridge.LI_CTYPE_NINTENDO.toInt())
        assertThat(NativePads.queries.none { it.startsWith("guessControllerType") }).isTrue()
    }
}
