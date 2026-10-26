package io.github.magalz.borealis.input

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_BUTTON_Y
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.GamepadButtons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** While the menu or a modal screen is open, no gamepad reaches the PC. */
@RunWith(RobolectricTestRunner::class)
class GamepadCaptureTest {
    private val rig = ControllerHandlerRig(multiController = true)
    private val padOne = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val padTwo = FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }

    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG

    @Test
    fun `capturing sends one neutral packet to each controller`() {
        padOne.report(a)
        padTwo.report(b)
        rig.press(KEYCODE_BUTTON_A)
        val before = rig.connection.packets.size

        rig.handler.setCaptured(true)

        val sent = rig.connection.packets.drop(before)
        assertThat(sent.map { it.controller }).containsExactly(0, 1)
        assertThat(sent.map { it.copy(controller = 0) }).containsExactly(Packet(0, 0), Packet(0, 0))
    }

    @Test
    fun `capturing forgets the buttons and sticks the controllers held`() {
        padOne.report(a or b, leftStickX = 1f)
        rig.handler.setCaptured(true)
        rig.handler.setCaptured(false)

        rig.press(KEYCODE_BUTTON_Y)

        assertThat(rig.connection.packets.last())
            .isEqualTo(Packet(controller = 0, buttons = ControllerPacket.Y_FLAG))
    }

    @Test
    fun `nothing reaches the PC from any source while captured`() {
        rig.handler.setCaptured(true)
        val packets = rig.connection.packets.size
        val mouse = rig.connection.mouse.size

        rig.press(KEYCODE_BUTTON_A)
        rig.release(KEYCODE_BUTTON_A)
        padOne.report(a)
        padTwo.report(b)
        rig.handler.reportOscState(a, 100, 100, 100, 100, 10, 10)
        rig.advanceBy(2_000)

        assertThat(rig.connection.packets).hasSize(packets)
        assertThat(rig.connection.mouse).hasSize(mouse)
    }

    @Test
    fun `input reaches the PC again when the capture ends`() {
        rig.handler.setCaptured(true)
        rig.press(KEYCODE_BUTTON_A)
        rig.release(KEYCODE_BUTTON_A)

        rig.handler.setCaptured(false)
        rig.press(KEYCODE_BUTTON_A)

        assertThat(rig.lastButtons()).isEqualTo(a)
    }

    @Test
    fun `buttons let go after the capture started do nothing to the PC`() {
        rig.press(KEYCODE_BUTTON_A)
        rig.handler.setCaptured(true)
        val packets = rig.connection.packets.size

        rig.release(KEYCODE_BUTTON_A)

        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `a held mouse click is released and the cursor stops when the capture starts`() {
        rig.handler.setMouseEmulation(true)
        padOne.report(a)
        assertThat(rig.connection.mouse).contains("down 1")

        rig.handler.setCaptured(true)
        padOne.report(0)

        assertThat(rig.connection.mouse.last()).isEqualTo("up 1")
        val mouse = rig.connection.mouse.size
        rig.advanceBy(1_000)
        assertThat(rig.connection.mouse).hasSize(mouse)
    }

    @Test
    fun `a held right click is released when the capture starts`() {
        rig.handler.setMouseEmulation(true)
        padOne.report(b)
        assertThat(rig.connection.mouse).contains("down 3")

        rig.handler.setCaptured(true)
        padOne.report(0)

        assertThat(rig.connection.mouse.last()).isEqualTo("up 3")
    }

    @Test
    fun `the menu chord is not reported while captured`() {
        rig.handler.setCaptured(true)

        rig.press(KEYCODE_BUTTON_START)
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(3_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a chord let go after a second while captured is not reported either`() {
        rig.handler.setCaptured(true)
        val packets = rig.connection.packets.size
        val time = rig.now

        rig.handler.handleButtonDown(
            rig.keyEvent(time, time, KeyEvent.ACTION_DOWN, KEYCODE_BUTTON_START)
        )
        rig.handler.handleButtonDown(
            rig.keyEvent(time, time, KeyEvent.ACTION_DOWN, KEYCODE_BUTTON_SELECT)
        )
        rig.handler.handleButtonUp(
            rig.keyEvent(time, time + 1_500, KeyEvent.ACTION_UP, KEYCODE_BUTTON_SELECT)
        )

        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `a chord held when the capture starts sends nothing when it is let go`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(1_000)
        rig.handler.setCaptured(true)
        val packets = rig.connection.packets.size

        rig.release(KEYCODE_BUTTON_SELECT)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `the Start hold toggle does not run while captured`() {
        rig.handler.setCaptured(true)
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(900)

        rig.release(KEYCODE_BUTTON_START)

        assertThat(org.robolectric.shadows.ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `capturing twice sends the neutral packets once`() {
        padOne.report(a)
        rig.handler.setCaptured(true)
        val packets = rig.connection.packets.size

        rig.handler.setCaptured(true)

        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `capturing stops the gyro of a pad that was sending it`() {
        rig.setDefaultContextField("gyroListener", object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Only the registration matters here: no sensor event is ever delivered.
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // Only the registration matters here: no sensor event is ever delivered.
                }
            })
        rig.connection.motion.clear()

        rig.handler.setCaptured(true)

        assertThat(rig.connection.motion).containsExactly("motion 0 2 0.0 0.0 0.0")
    }

    @Test
    fun `capturing lifts the fingers off the touchpad of a pad that has one`() {
        rig.setDefaultContextField("touchpadXRange", motionRange(MotionEvent.AXIS_X))
        rig.setDefaultContextField("touchpadYRange", motionRange(MotionEvent.AXIS_Y))

        rig.handler.setCaptured(true)

        assertThat(rig.connection.touches).containsExactly("touch 0 7")
    }

    @Test
    fun `capturing sends nothing about a touchpad to a pad without one`() {
        rig.handler.setCaptured(true)

        assertThat(rig.connection.touches).isEmpty()
    }

    @Test
    fun `capturing sends no neutral packet to a controller number that was freed`() {
        padOne.report(a)
        padTwo.report(b)
        rig.handler.deviceRemoved(padTwo)
        val before = rig.connection.packets.size

        rig.handler.setCaptured(true)

        assertThat(rig.connection.packets.drop(before).map { it.controller }).containsExactly(0)
    }

    private fun motionRange(axis: Int): InputDevice.MotionRange {
        val constructor = InputDevice.MotionRange::class.java.declaredConstructors.first { it.parameterCount == 7 }
        constructor.isAccessible = true
        return constructor.newInstance(axis, InputDevice.SOURCE_TOUCHPAD, 0f, 1f, 0f, 0f, 0f)
            as InputDevice.MotionRange
    }

    @Test
    fun `the button bits agree with the controller packet flags`() {
        assertThat(GamepadButtons.START).isEqualTo(ControllerPacket.PLAY_FLAG)
        assertThat(GamepadButtons.SELECT).isEqualTo(ControllerPacket.BACK_FLAG)
        assertThat(GamepadButtons.GUIDE).isEqualTo(ControllerPacket.SPECIAL_BUTTON_FLAG)
        assertThat(GamepadButtons.A).isEqualTo(ControllerPacket.A_FLAG)
        assertThat(GamepadButtons.B).isEqualTo(ControllerPacket.B_FLAG)
        assertThat(GamepadButtons.Y).isEqualTo(ControllerPacket.Y_FLAG)
        assertThat(GamepadButtons.LEFT_BUMPER).isEqualTo(ControllerPacket.LB_FLAG)
        assertThat(GamepadButtons.RIGHT_BUMPER).isEqualTo(ControllerPacket.RB_FLAG)
        assertThat(GamepadButtons.DPAD_UP).isEqualTo(ControllerPacket.UP_FLAG)
        assertThat(GamepadButtons.DPAD_DOWN).isEqualTo(ControllerPacket.DOWN_FLAG)
        assertThat(GamepadButtons.DPAD_LEFT).isEqualTo(ControllerPacket.LEFT_FLAG)
        assertThat(GamepadButtons.DPAD_RIGHT).isEqualTo(ControllerPacket.RIGHT_FLAG)
    }

    @Test
    fun `USB pad buttons reach the chrome as keys only while captured`() {
        val keys = mutableListOf<Pair<Int, Int>>()
        rig.handler.setChromeKeySink(
            ChromeKeySink {
                keys += it.action to it.keyCode
                true
            }
        )

        padOne.report(b)
        padOne.report(0)
        rig.advanceBy(1)
        assertThat(keys).isEmpty()

        rig.handler.setCaptured(true)
        padOne.report(a)
        padOne.report(a or GamepadButtons.DPAD_RIGHT)
        padOne.report(0)
        rig.advanceBy(1)

        assertThat(keys)
            .containsExactly(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_RIGHT,
            )
            .inOrder()
    }

    @Test
    fun `the left stick of a USB pad moves the chrome focus only while captured`() {
        val keys = mutableListOf<Pair<Int, Int>>()
        rig.handler.setChromeKeySink(
            ChromeKeySink {
                keys += it.action to it.keyCode
                true
            }
        )

        padOne.report(0, leftStickY = 1f)
        padOne.report(0)
        rig.advanceBy(1)
        assertThat(keys).isEmpty()

        rig.handler.setCaptured(true)
        padOne.report(0, leftStickY = 1f)
        padOne.report(0)
        rig.advanceBy(1)

        assertThat(keys)
            .containsExactly(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_DOWN,
            )
            .inOrder()
    }

    @Test
    fun `a USB pad held when the capture starts does not press in the menu`() {
        val keys = mutableListOf<Int>()
        rig.handler.setChromeKeySink(
            ChromeKeySink {
                keys += it.keyCode
                true
            }
        )
        padOne.report(a)

        rig.handler.setCaptured(true)
        padOne.report(a)
        rig.advanceBy(1)

        assertThat(keys).isEmpty()
    }
}
