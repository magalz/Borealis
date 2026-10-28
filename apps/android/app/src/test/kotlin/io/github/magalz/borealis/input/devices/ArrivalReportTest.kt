package io.github.magalz.borealis.input.devices

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.input.FakeUsbController
import io.github.magalz.borealis.testing.ANDROID_11
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.ShadowToast

/**
 * The arrival report is what the handler tells the host about a controller the first time it sends
 * input: its type, the buttons it has and what it can do (rumble, battery, motion, touchpad, LEDs).
 * It is made from the device's vendor id, its key layout, its axes and its hardware, and it goes
 * out before the first packet of the controller.
 */
class ArrivalReportTest : DevicesTestBase() {
    private val xbox = 0x045e
    private val sony = 0x054c
    private val nintendo = 0x057e

    private fun arrivalOf(
        device: Pad,
        multiController: Boolean = false,
        configure: com.limelight.preferences.PreferenceConfiguration.() -> Unit = {},
    ): Arrival {
        val rig = rig(multiController, listOf(device), configure)
        rig.press(device, KEYCODE_BUTTON_A)
        return rig.connection.arrivals.single()
    }

    /** The capability flags added up, as the `short` the arrival report carries them in. */
    private fun caps(vararg flags: Short): Int = flags.fold(0) { sum, flag -> sum or flag.toInt() }

    private fun addSensors(vararg types: Int) {
        val manager =
            RuntimeEnvironment.getApplication().getSystemService(SensorManager::class.java)
        types.forEach {
            shadowOf(manager).addSensor(SensorBuilder.newBuilder().setType(it).build())
        }
    }

    @Test
    fun `the arrival goes out before the first packet of the controller`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))

        rig.press(device, KEYCODE_BUTTON_A)

        assertThat(rig.connection.log).containsExactly("arrival 0", "packet 0").inOrder()
    }

    @Test
    fun `Microsoft pads are Xbox pads and the native library is not asked`() {
        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = xbox
                    productId = 0x028e
                }
            )

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_XBOX.toInt())
        assertThat(NativePads.queries)
            .containsExactly(
                "guessControllerHasPaddles $xbox ${0x028e}",
                "guessControllerHasShareButton $xbox ${0x028e}",
            )
            .inOrder()
    }

    @Test
    fun `Sony pads are PlayStation pads`() {
        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = sony
                    productId = 0x09cc
                }
            )

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
        assertThat(NativePads.queries.none { it.startsWith("guessControllerType") }).isTrue()
    }

    @Test
    fun `Nintendo pads are Nintendo pads`() {
        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = nintendo
                    productId = 0x2009
                }
            )

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_NINTENDO.toInt())
    }

    @Test
    fun `another vendor is looked up in the native list`() {
        NativePads.type(0x28de, 0x1205, MoonBridge.LI_CTYPE_STEAM)

        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = 0x28de
                    productId = 0x1205
                }
            )

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_STEAM.toInt())
        assertThat(NativePads.queries.last()).isEqualTo("guessControllerType ${0x28de} ${0x1205}")
    }

    @Test
    fun `a vendor the native list does not know is an unknown type`() {
        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = 0x1234
                    productId = 0x5678
                }
            )

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
    }

    @Test
    fun `the key layout decides the buttons a pad is said to have`() {
        val expected =
            mapOf(
                KeyEvent.KEYCODE_BUTTON_A to ControllerPacket.A_FLAG,
                KeyEvent.KEYCODE_BUTTON_B to ControllerPacket.B_FLAG,
                KeyEvent.KEYCODE_BUTTON_X to ControllerPacket.X_FLAG,
                KeyEvent.KEYCODE_BUTTON_Y to ControllerPacket.Y_FLAG,
                KeyEvent.KEYCODE_DPAD_UP to ControllerPacket.UP_FLAG,
                KeyEvent.KEYCODE_DPAD_DOWN to ControllerPacket.DOWN_FLAG,
                KeyEvent.KEYCODE_DPAD_LEFT to ControllerPacket.LEFT_FLAG,
                KeyEvent.KEYCODE_DPAD_RIGHT to ControllerPacket.RIGHT_FLAG,
                KeyEvent.KEYCODE_DPAD_UP_LEFT to
                    (ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG),
                KeyEvent.KEYCODE_DPAD_UP_RIGHT to
                    (ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG),
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT to
                    (ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG),
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT to
                    (ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG),
                KeyEvent.KEYCODE_BUTTON_L1 to ControllerPacket.LB_FLAG,
                KeyEvent.KEYCODE_BUTTON_R1 to ControllerPacket.RB_FLAG,
                KeyEvent.KEYCODE_BUTTON_THUMBL to ControllerPacket.LS_CLK_FLAG,
                KeyEvent.KEYCODE_BUTTON_THUMBR to ControllerPacket.RS_CLK_FLAG,
                KeyEvent.KEYCODE_BUTTON_START to ControllerPacket.PLAY_FLAG,
                KeyEvent.KEYCODE_MENU to ControllerPacket.PLAY_FLAG,
                KeyEvent.KEYCODE_BUTTON_SELECT to ControllerPacket.BACK_FLAG,
                KeyEvent.KEYCODE_BACK to ControllerPacket.BACK_FLAG,
                KeyEvent.KEYCODE_BUTTON_MODE to ControllerPacket.SPECIAL_BUTTON_FLAG,
                KeyEvent.KEYCODE_MEDIA_RECORD to ControllerPacket.MISC_FLAG,
                KeyEvent.KEYCODE_BUTTON_1 to ControllerPacket.TOUCHPAD_FLAG,
            )

        for ((key, flags) in expected) {
            val arrival = arrivalOf(pad(key) { keys = intArrayOf(key) })

            assertWithMessage("key $key").that(arrival.supportedButtons).isEqualTo(flags)
        }
    }

    @Test
    fun `keys the Android layout has but the host does not use add nothing`() {
        val keys =
            intArrayOf(
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_C,
                KeyEvent.KEYCODE_BUTTON_Z,
            )

        val arrival = arrivalOf(pad(1) { this.keys = keys })

        assertThat(arrival.supportedButtons).isEqualTo(0)
    }

    @Test
    fun `a full key layout adds up its flags`() {
        val keys =
            intArrayOf(
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BACK,
            )

        val arrival = arrivalOf(pad(1) { this.keys = keys })

        assertThat(arrival.supportedButtons)
            .isEqualTo(
                ControllerPacket.A_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG
            )
    }

    @Test
    fun `a pad with paddles has the four paddle buttons`() {
        NativePads.paddles(0x045e, 0x0b00)

        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = xbox
                    productId = 0x0b00
                }
            )

        assertThat(arrival.supportedButtons)
            .isEqualTo(
                ControllerPacket.PADDLE1_FLAG or
                    ControllerPacket.PADDLE2_FLAG or
                    ControllerPacket.PADDLE3_FLAG or
                    ControllerPacket.PADDLE4_FLAG
            )
    }

    @Test
    fun `a pad with a share button has the misc button`() {
        NativePads.shares(0x045e, 0x0b12)

        val arrival =
            arrivalOf(
                pad(1) {
                    vendorId = xbox
                    productId = 0x0b12
                }
            )

        assertThat(arrival.supportedButtons).isEqualTo(ControllerPacket.MISC_FLAG)
    }

    @Test
    fun `the hat axes say the dpad exists even without dpad keys`() {
        val horizontal = arrivalOf(pad(1) { axes = STICKS_AND_Z + Axis(MotionEvent.AXIS_HAT_X) })
        val vertical = arrivalOf(pad(2) { axes = STICKS_AND_Z + Axis(MotionEvent.AXIS_HAT_Y) })

        assertThat(horizontal.supportedButtons)
            .isEqualTo(ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG)
        assertThat(vertical.supportedButtons)
            .isEqualTo(ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG)
    }

    @Test
    fun `an external pad can report its battery`() {
        val arrival = arrivalOf(pad(1))

        assertThat(arrival.capabilities).isEqualTo(MoonBridge.LI_CCAP_BATTERY_STATE.toInt())
    }

    @Test
    fun `analog triggers are reported when the pad has trigger axes`() {
        val withTriggers =
            pad(1) {
                axes =
                    LEFT_STICK +
                        listOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER).map {
                            Axis(it)
                        }
            }

        val arrival = arrivalOf(withTriggers)

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_ANALOG_TRIGGERS, MoonBridge.LI_CCAP_BATTERY_STATE))
    }

    @Test
    fun `an internal pad uses the vibrator of the device`() {
        val arrival = arrivalOf(pad(1) { external = false })

        assertThat(arrival.capabilities).isEqualTo(MoonBridge.LI_CCAP_RUMBLE.toInt())
    }

    @Test
    fun `an internal pad on a device without a vibrator cannot rumble or report a battery`() {
        shadowOf(
                RuntimeEnvironment.getApplication()
                    .getSystemService(android.os.Vibrator::class.java)
            )
            .setHasVibrator(false)

        val arrival = arrivalOf(pad(1) { external = false })

        assertThat(arrival.capabilities).isEqualTo(0)
    }

    @Test
    fun `a SHIELD controller rumbles and reports its battery through the SHIELD service`() {
        shadowOf(
                RuntimeEnvironment.getApplication()
                    .getSystemService(android.os.Vibrator::class.java)
            )
            .setHasVibrator(false)
        val device = pad(1) { external = false }
        SceDevices.recognized += device.id

        val arrival = arrivalOf(device)

        // Without the service this device would report nothing: it is internal and has no vibrator
        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_RUMBLE, MoonBridge.LI_CCAP_BATTERY_STATE))
    }

    @Test
    fun `the SHIELD service only knows the devices it names`() {
        val shield = pad(1)
        val other = pad(2)
        SceDevices.recognized += shield.id

        val capabilities = arrivalOf(other).capabilities

        assertThat(capabilities and MoonBridge.LI_CCAP_RUMBLE.toInt()).isEqualTo(0)
    }

    @Test
    fun `a pad with its own vibrator rumbles`() {
        val arrival = arrivalOf(pad(1) { hasVibrator = true })

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_RUMBLE, MoonBridge.LI_CCAP_BATTERY_STATE))
    }

    @Test
    fun `a pad with two vibrators rumbles`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 2))

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_RUMBLE, MoonBridge.LI_CCAP_BATTERY_STATE))
    }

    @Test
    fun `a pad with four vibrators rumbles its triggers too`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 4))

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities)
            .isEqualTo(
                caps(
                    MoonBridge.LI_CCAP_RUMBLE,
                    MoonBridge.LI_CCAP_TRIGGER_RUMBLE,
                    MoonBridge.LI_CCAP_BATTERY_STATE,
                )
            )
    }

    @Test
    fun `a pad with a light that shows colors reports the LED`() {
        val device =
            pad(1) {
                vendorId = sony
                productId = 0x09cc
            }
        DeviceHardware.set(device.id, Hardware(rgbLights = 1))

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_RGB_LED, MoonBridge.LI_CCAP_BATTERY_STATE))
    }

    @Test
    fun `a pad with only plain lights does not report the LED`() {
        val device =
            pad(1) {
                vendorId = sony
                productId = 0x09cc
            }
        DeviceHardware.set(device.id, Hardware(plainLights = 2))

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities).isEqualTo(MoonBridge.LI_CCAP_BATTERY_STATE.toInt())
    }

    @Test
    fun `a pad with a touchpad reports it`() {
        val device =
            pad(1) {
                sources = PAD_SOURCES or InputDevice.SOURCE_TOUCHPAD
                vendorId = xbox
            }

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_TOUCHPAD, MoonBridge.LI_CCAP_BATTERY_STATE))
        // Only a PlayStation pad is known to have a button under it
        assertThat(arrival.supportedButtons).isEqualTo(0)
    }

    @Test
    fun `the touchpad of a PlayStation pad is a button`() {
        val device =
            pad(1) {
                sources = PAD_SOURCES or InputDevice.SOURCE_TOUCHPAD
                vendorId = sony
            }

        val arrival = arrivalOf(device)

        assertThat(arrival.supportedButtons).isEqualTo(ControllerPacket.TOUCHPAD_FLAG)
    }

    @Test
    fun `a PlayStation pad without a touchpad has no touchpad button`() {
        val arrival = arrivalOf(pad(1) { vendorId = sony })

        assertThat(arrival.supportedButtons).isEqualTo(0)
        assertThat(arrival.capabilities and MoonBridge.LI_CCAP_TOUCHPAD.toInt()).isEqualTo(0)
    }

    @Test
    fun `a touchscreen source is no touchpad`() {
        val arrival = arrivalOf(pad(1) { sources = PAD_SOURCES or InputDevice.SOURCE_TOUCHSCREEN })

        assertThat(arrival.capabilities and MoonBridge.LI_CCAP_TOUCHPAD.toInt()).isEqualTo(0)
    }

    @Test
    fun `a pad with motion sensors reports them and changes its type`() {
        addSensors(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)

        val arrival = arrivalOf(pad(1) { vendorId = xbox }) { gamepadMotionSensors = true }

        assertThat(arrival.capabilities)
            .isEqualTo(
                caps(
                    MoonBridge.LI_CCAP_ACCEL,
                    MoonBridge.LI_CCAP_GYRO,
                    MoonBridge.LI_CCAP_BATTERY_STATE,
                )
            )
        // A pad that is not a PlayStation pad is told to the host as unknown, with a notice
        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Gamepad type may be changed due to motion sensor emulation")
        assertThat(ShadowToast.getLatestToast().duration)
            .isEqualTo(android.widget.Toast.LENGTH_LONG)
    }

    @Test
    fun `a pad with an accelerometer only reports no gyro`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)

        val arrival = arrivalOf(pad(1) { vendorId = xbox }) { gamepadMotionSensors = true }

        assertThat(arrival.capabilities and MoonBridge.LI_CCAP_ACCEL.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_ACCEL.toInt())
        assertThat(arrival.capabilities and MoonBridge.LI_CCAP_GYRO.toInt()).isEqualTo(0)
    }

    @Test
    fun `a PlayStation pad with motion sensors keeps its type and shows no notice`() {
        addSensors(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)

        val arrival = arrivalOf(pad(1) { vendorId = sony }) { gamepadMotionSensors = true }

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `a pad without motion sensors keeps its type and shows no notice`() {
        val arrival = arrivalOf(pad(1) { vendorId = xbox }) { gamepadMotionSensors = true }

        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_XBOX.toInt())
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `the sensors of the device stand in for the pad's when the setting allows`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)
        val device = pad(1) { vendorId = xbox }
        val rig = rig(devices = listOf(device)) { gamepadMotionSensorsFallbackToDevice = true }

        rig.press(device, KEYCODE_BUTTON_A)

        val arrival = rig.connection.arrivals.single()
        assertThat(arrival.capabilities and MoonBridge.LI_CCAP_ACCEL.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_ACCEL.toInt())
        assertThat(arrival.type).isEqualTo(MoonBridge.LI_CTYPE_UNKNOWN.toInt())
        assertThat(rig.context(device)["sensorManager"])
            .isSameInstanceAs(field(rig.handler, "deviceSensorManager"))
        assertThat(rig.context(device).bool("needsClickpadEmulation")).isTrue()
    }

    @Test
    fun `a pad that is a PlayStation pad gets no clickpad emulation from the fallback`() {
        addSensors(Sensor.TYPE_ACCELEROMETER)
        val device = pad(1) { vendorId = sony }
        val rig = rig(devices = listOf(device)) { gamepadMotionSensorsFallbackToDevice = true }

        rig.press(device, KEYCODE_BUTTON_A)

        assertThat(rig.context(device).bool("needsClickpadEmulation")).isFalse()
        assertThat(rig.connection.arrivals.single().type).isEqualTo(MoonBridge.LI_CTYPE_PS.toInt())
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 a pad cannot report its battery or take rumble from a manager`() {
        val device = pad(1)

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities).isEqualTo(0)
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 the analog triggers and the vibrator still count`() {
        val device =
            pad(1) {
                hasVibrator = true
                axes =
                    LEFT_STICK +
                        listOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER).map {
                            Axis(it)
                        }
            }

        val arrival = arrivalOf(device)

        assertThat(arrival.capabilities)
            .isEqualTo(caps(MoonBridge.LI_CCAP_ANALOG_TRIGGERS, MoonBridge.LI_CCAP_RUMBLE))
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `before Android 12 the sensors still stand in for the pad when the setting allows`() {
        addSensors(Sensor.TYPE_GYROSCOPE)
        val device = pad(1) { vendorId = sony }

        val arrival = arrivalOf(device) { gamepadMotionSensorsFallbackToDevice = true }

        assertThat(arrival.capabilities).isEqualTo(MoonBridge.LI_CCAP_GYRO.toInt())
    }

    @Test
    fun `a USB pad reports what its driver says`() {
        val rig = rig(multiController = true)
        val usb = FakeUsbController(5, rig.handler).also { rig.handler.deviceAdded(it) }

        usb.report(ControllerPacket.A_FLAG)

        assertThat(rig.connection.arrivals.single())
            .isEqualTo(
                Arrival(
                    controller = 0,
                    activeMask = 1,
                    type = 0,
                    supportedButtons = 0,
                    capabilities = 0,
                )
            )
    }

    @Test
    fun `a USB pad with a type and capabilities reports them as its driver set them`() {
        val rig = rig(multiController = true)
        val usb = FakeUsbController(5, rig.handler)
        setField(usb, "type", MoonBridge.LI_CTYPE_XBOX)
        setField(
            usb,
            "capabilities",
            caps(MoonBridge.LI_CCAP_ANALOG_TRIGGERS, MoonBridge.LI_CCAP_RUMBLE).toShort(),
        )
        setField(usb, "supportedButtonFlags", ControllerPacket.A_FLAG)
        rig.handler.deviceAdded(usb)

        usb.report(ControllerPacket.A_FLAG)

        assertThat(rig.connection.arrivals.single())
            .isEqualTo(
                Arrival(
                    controller = 0,
                    activeMask = 1,
                    type = MoonBridge.LI_CTYPE_XBOX.toInt(),
                    supportedButtons = ControllerPacket.A_FLAG,
                    capabilities =
                        caps(MoonBridge.LI_CCAP_ANALOG_TRIGGERS, MoonBridge.LI_CCAP_RUMBLE),
                )
            )
    }
}
