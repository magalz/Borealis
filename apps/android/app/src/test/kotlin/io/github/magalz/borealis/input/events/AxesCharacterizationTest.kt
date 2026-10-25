package io.github.magalz.borealis.input.events

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT
import android.view.MotionEvent.AXIS_BRAKE
import android.view.MotionEvent.AXIS_GAS
import android.view.MotionEvent.AXIS_HAT_X
import android.view.MotionEvent.AXIS_HAT_Y
import android.view.MotionEvent.AXIS_RX
import android.view.MotionEvent.AXIS_RY
import android.view.MotionEvent.AXIS_RZ
import android.view.MotionEvent.AXIS_X
import android.view.MotionEvent.AXIS_Y
import android.view.MotionEvent.AXIS_Z
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG
import com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG
import com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG
import com.limelight.nvstream.input.ControllerPacket.UP_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `handleMotionEvent` and `handleAxisSet`: sticks to signed shorts, triggers to bytes, the hat to
 * the D-pad, the deadzones, and which axes a context reads. Events of device id 0 reach the default
 * context, which reads the left stick from X and Y, the right stick from Z and RZ, the triggers
 * from BRAKE and GAS and the hat from HAT_X and HAT_Y; it has a stick deadzone from the setting
 * and no trigger deadzone.
 */
@RunWith(RobolectricTestRunner::class)
class AxesCharacterizationTest {
    private val rig = EventsRig { deadzonePercentage = 15 }
    private val pad = rig.default

    private fun send(vararg axes: Pair<Int, Float>): Sent {
        assertThat(pad.motion(axes.toMap())).isTrue()
        return rig.packets.last()
    }

    // ----- Sticks -----

    @Test
    fun `a stick at full deflection is plus or minus 0x7FFE, with up positive on the pad and negative in the packet`() {
        assertThat(send(AXIS_X to 1f)).isEqualTo(Sent(0, 1, 0, leftStickX = 32766))
        assertThat(send(AXIS_X to -1f)).isEqualTo(Sent(0, 1, 0, leftStickX = -32766))
        // Android's Y points down; the packet's Y points up.
        assertThat(send(AXIS_Y to 1f)).isEqualTo(Sent(0, 1, 0, leftStickY = -32766))
        assertThat(send(AXIS_Y to -1f)).isEqualTo(Sent(0, 1, 0, leftStickY = 32766))
        assertThat(send(AXIS_Z to 1f)).isEqualTo(Sent(0, 1, 0, rightStickX = 32766))
        assertThat(send(AXIS_Z to -1f)).isEqualTo(Sent(0, 1, 0, rightStickX = -32766))
        assertThat(send(AXIS_RZ to 1f)).isEqualTo(Sent(0, 1, 0, rightStickY = -32766))
        assertThat(send(AXIS_RZ to -1f)).isEqualTo(Sent(0, 1, 0, rightStickY = 32766))
    }

    @Test
    fun `a stick between the extremes is cut toward zero, for both signs`() {
        val expected =
            mapOf(0.5f to 16383, 0.3f to 9829, 0.25f to 8191, 0.999f to 32733, 0.8f to 26212, 0.6f to 19659)

        for ((value, short) in expected) {
            assertWithMessage("x $value").that(send(AXIS_X to value).leftStickX).isEqualTo(short)
            assertWithMessage("x -$value").that(send(AXIS_X to -value).leftStickX).isEqualTo(-short)
            assertWithMessage("y $value").that(send(AXIS_Y to value).leftStickY).isEqualTo(-short)
            assertWithMessage("y -$value").that(send(AXIS_Y to -value).leftStickY).isEqualTo(short)
        }
    }

    @Test
    fun `a stick value past 1 wraps at the sign bit`() {
        // Android never reports more than 1, but the cast is a short of an int: 1.5 * 0x7FFE is 49149.
        assertThat(send(AXIS_X to 1.5f).leftStickX).isEqualTo(-16387)
        assertThat(send(AXIS_X to -1.5f).leftStickX).isEqualTo(16387)
        assertThat(send(AXIS_X to 2f).leftStickX).isEqualTo(-4)
        assertThat(send(AXIS_Y to 1.5f).leftStickY).isEqualTo(16387)
        assertThat(send(AXIS_Z to 1.5f).rightStickX).isEqualTo(-16387)
        assertThat(send(AXIS_RZ to 1.5f).rightStickY).isEqualTo(16387)
    }

    @Test
    fun `both sticks of one event keep their own values`() {
        val sent = send(AXIS_X to 0.5f, AXIS_Y to 0.25f, AXIS_Z to -0.8f, AXIS_RZ to 0.6f)

        assertThat(sent)
            .isEqualTo(
                Sent(
                    controller = 0,
                    activeMask = 1,
                    buttons = 0,
                    leftStickX = 16383,
                    leftStickY = -8191,
                    rightStickX = -26212,
                    rightStickY = -19659,
                )
            )
    }

    @Test
    fun `a batched event is read at its latest value, whatever its history holds`() {
        assertThat(pad.motion(mapOf(AXIS_X to 0.5f), history = listOf(mapOf(AXIS_X to 1f), mapOf(AXIS_X to -1f))))
            .isTrue()

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0, leftStickX = 16383))
    }

    @Test
    fun `an event with no value for a stick brings it back to the center`() {
        send(AXIS_X to 1f, AXIS_Z to 1f)

        assertThat(send(AXIS_Y to 0.5f)).isEqualTo(Sent(0, 1, 0, leftStickY = -16383))
    }

    // ----- Deadzone -----

    @Test
    fun `a stick inside the deadzone circle is zero on both axes`() {
        // 15 percent: 0.1 on each axis is 0.14 from the center, inside the circle.
        assertThat(send(AXIS_X to 0.1f, AXIS_Y to 0.1f)).isEqualTo(Sent(0, 1, 0))
        assertThat(send(AXIS_X to 0.149f)).isEqualTo(Sent(0, 1, 0))
        assertThat(send(AXIS_Z to 0.1f, AXIS_RZ to 0.1f)).isEqualTo(Sent(0, 1, 0))
        assertThat(send(AXIS_Z to -0.149f)).isEqualTo(Sent(0, 1, 0))
    }

    @Test
    fun `a stick outside the circle keeps its value, which is not scaled to the deadzone`() {
        assertThat(send(AXIS_X to 0.151f).leftStickX).isEqualTo(4947)
        assertThat(send(AXIS_X to 0.16f).leftStickX).isEqualTo(5242)
        assertThat(send(AXIS_Z to -0.16f).rightStickX).isEqualTo(-5242)
        // 0.12 and 0.10 are each below 0.15, but the stick is 0.156 from the center.
        assertThat(send(AXIS_X to 0.12f, AXIS_Y to 0.1f))
            .isEqualTo(Sent(0, 1, 0, leftStickX = 3931, leftStickY = -3276))
    }

    @Test
    fun `the deadzone is a circle, not a square`() {
        // 0.12 on each axis is 0.17 from the center, outside 0.15, though each axis is inside.
        assertThat(send(AXIS_X to 0.12f, AXIS_Y to 0.12f))
            .isEqualTo(Sent(0, 1, 0, leftStickX = 3931, leftStickY = -3931))
    }

    @Test
    fun `each stick has its own deadzone check`() {
        assertThat(send(AXIS_X to 0.1f, AXIS_Y to 0.1f, AXIS_Z to 0.3f))
            .isEqualTo(Sent(0, 1, 0, rightStickX = 9829))
    }

    @Test
    fun `a deadzone setting of zero or less is 1 percent`() {
        for (percentage in listOf(0, -5)) {
            val small = EventsRig { deadzonePercentage = percentage }

            small.default.motion(mapOf(AXIS_X to 0.0099f))
            small.default.motion(mapOf(AXIS_X to 0.02f))

            assertWithMessage("setting $percentage")
                .that(small.packets.map { it.leftStickX })
                .containsExactly(0, 655)
                .inOrder()
        }
    }

    @Test
    fun `the deadzone follows the setting`() {
        val wide = EventsRig { deadzonePercentage = 50 }

        wide.default.motion(mapOf(AXIS_X to 0.49f))
        wide.default.motion(mapOf(AXIS_X to 0.51f))

        assertThat(wide.packets.map { it.leftStickX }).containsExactly(0, 16710).inOrder()
    }

    // ----- Triggers -----

    @Test
    fun `a trigger is its value times 255 in a byte, so it turns negative above one half`() {
        val expected = listOf(0.25f to 63, 0.5f to 127, 0.502f to -128, 0.75f to -65, 1f to -1, 1.2f to 50)

        for ((value, byte) in expected) {
            assertWithMessage("left $value").that(send(AXIS_BRAKE to value).leftTrigger).isEqualTo(byte)
            assertWithMessage("right $value").that(send(AXIS_GAS to value).rightTrigger).isEqualTo(byte)
        }
    }

    @Test
    fun `the triggers of one event are independent`() {
        assertThat(send(AXIS_BRAKE to 1f, AXIS_GAS to 0.25f))
            .isEqualTo(Sent(0, 1, 0, leftTrigger = -1, rightTrigger = 63))
    }

    @Test
    fun `the default context has no trigger deadzone, so anything above zero is sent`() {
        assertThat(send(AXIS_BRAKE to 0.001f).leftTrigger).isEqualTo(0)
        assertThat(send(AXIS_BRAKE to 0.01f).leftTrigger).isEqualTo(2)
        // Zero and below are the deadzone's 0, with a deadzone of 0.
        assertThat(send(AXIS_BRAKE to 0f).leftTrigger).isEqualTo(0)
        assertThat(send(AXIS_BRAKE to -0.5f).leftTrigger).isEqualTo(0)
    }

    @Test
    fun `a trigger at or below the deadzone of its context is zero`() {
        val deadzone = rig.addPad(7, fields = mapOf("triggerDeadzone" to 0.13f, "leftTriggerAxis" to AXIS_BRAKE, "rightTriggerAxis" to AXIS_GAS))
        fun send(vararg axes: Pair<Int, Float>): Sent {
            deadzone.motion(axes.toMap())
            return rig.packets.last()
        }

        assertThat(send(AXIS_BRAKE to 0.13f, AXIS_GAS to 0.13f)).isEqualTo(Sent(0, 1, 0))
        assertThat(send(AXIS_BRAKE to 0.131f, AXIS_GAS to 0.14f))
            .isEqualTo(Sent(0, 1, 0, leftTrigger = 33, rightTrigger = 35))
        assertThat(send(AXIS_BRAKE to 0.12f, AXIS_GAS to 0.5f))
            .isEqualTo(Sent(0, 1, 0, leftTrigger = 0, rightTrigger = 127))
    }

    @Test
    fun `a trigger that is used sets the flag that silences its digital key, and a zero does not`() {
        val used = rig.addPad(7, fields = mapOf("leftTriggerAxis" to AXIS_BRAKE, "rightTriggerAxis" to AXIS_GAS))

        used.motion(mapOf(AXIS_BRAKE to 0f, AXIS_GAS to 0f))
        assertThat(used.getBoolean("leftTriggerAxisUsed")).isFalse()
        assertThat(used.getBoolean("rightTriggerAxisUsed")).isFalse()

        used.motion(mapOf(AXIS_BRAKE to 0.05f))
        assertThat(used.getBoolean("leftTriggerAxisUsed")).isTrue()
        assertThat(used.getBoolean("rightTriggerAxisUsed")).isFalse()

        used.motion(mapOf(AXIS_GAS to -0.5f))
        assertThat(used.getBoolean("rightTriggerAxisUsed")).isTrue()
        // Once used, the flag stays.
        used.motion(mapOf())
        assertThat(used.getBoolean("leftTriggerAxisUsed")).isTrue()
    }

    // ----- Triggers that rest at -1 -----

    private fun idleNegativePad() =
        rig.addPad(
            7,
            fields =
                mapOf(
                    "leftTriggerAxis" to AXIS_Z,
                    "rightTriggerAxis" to AXIS_RZ,
                    "triggersIdleNegative" to true,
                    "triggerDeadzone" to 0.13f,
                ),
        )

    @Test
    fun `triggers that rest at -1 read the first zero as released and later values as -1 to 1`() {
        val idle = idleNegativePad()
        fun send(z: Float, rz: Float): Sent {
            idle.motion(mapOf(AXIS_Z to z, AXIS_RZ to rz))
            return rig.packets.last()
        }

        // The first events carry zero, which Android sends before the trigger is touched.
        assertThat(send(0f, 0f)).isEqualTo(Sent(0, 1, 0))
        // A value of -1 is a trigger at rest: the first nonzero value marks the trigger as used.
        assertThat(send(-1f, 0f)).isEqualTo(Sent(0, 1, 0))
        assertThat(idle.getBoolean("leftTriggerAxisUsed")).isTrue()
        assertThat(idle.getBoolean("rightTriggerAxisUsed")).isFalse()
        // From then on the range is -1 to 1, which is mapped to 0 to 1.
        assertThat(send(1f, 0f).leftTrigger).isEqualTo(-1)
        assertThat(send(0f, 0f).leftTrigger).isEqualTo(127)
        assertThat(send(0.5f, 0f).leftTrigger).isEqualTo(-65)
        assertThat(send(-0.5f, 0f).leftTrigger).isEqualTo(63)
        // The right trigger has not been used, so its zero is still a zero.
        assertThat(send(-1f, 0f).rightTrigger).isEqualTo(0)
    }

    @Test
    fun `the two triggers that rest at -1 become used on their own`() {
        val idle = idleNegativePad()

        idle.motion(mapOf(AXIS_Z to -1f, AXIS_RZ to -1f))
        val bothRest = rig.packets.last()
        idle.motion(mapOf(AXIS_Z to -1f, AXIS_RZ to 1f))
        val rightPulled = rig.packets.last()

        assertThat(bothRest).isEqualTo(Sent(0, 1, 0))
        assertThat(rightPulled).isEqualTo(Sent(0, 1, 0, leftTrigger = 0, rightTrigger = -1))
    }

    @Test
    fun `a trigger that rests at -1 goes through the deadzone after the mapping`() {
        val idle = idleNegativePad()

        idle.motion(mapOf(AXIS_Z to -1f))
        // -0.75 maps to 0.125, which is inside the deadzone of 0.13; -0.7 maps to 0.15, outside.
        idle.motion(mapOf(AXIS_Z to -0.75f))
        val inside = rig.packets.last().leftTrigger
        idle.motion(mapOf(AXIS_Z to -0.7f))
        val outside = rig.packets.last().leftTrigger

        assertThat(inside).isEqualTo(0)
        assertThat(outside).isEqualTo(38)
    }

    @Test
    fun `triggers that do not rest at -1 never map the range, however they are used`() {
        pad.motion(mapOf(AXIS_BRAKE to 0.5f))
        pad.motion(mapOf(AXIS_BRAKE to 0f))

        assertThat(rig.packets.map { it.leftTrigger }).containsExactly(127, 0).inOrder()
    }

    // ----- Hat -----

    @Test
    fun `the hat is the D-pad, with up at -1 on the Y axis`() {
        assertThat(send(AXIS_HAT_X to -1f).buttons).isEqualTo(LEFT_FLAG)
        assertThat(send(AXIS_HAT_X to 1f).buttons).isEqualTo(RIGHT_FLAG)
        assertThat(send(AXIS_HAT_Y to -1f).buttons).isEqualTo(UP_FLAG)
        assertThat(send(AXIS_HAT_Y to 1f).buttons).isEqualTo(DOWN_FLAG)
        assertThat(send(AXIS_HAT_X to -1f, AXIS_HAT_Y to -1f).buttons).isEqualTo(LEFT_FLAG or UP_FLAG)
        assertThat(send(AXIS_HAT_X to 1f, AXIS_HAT_Y to 1f).buttons).isEqualTo(RIGHT_FLAG or DOWN_FLAG)
        assertThat(send().buttons).isEqualTo(0)
    }

    @Test
    fun `the hat needs more than one half to count`() {
        assertThat(send(AXIS_HAT_X to -0.5f, AXIS_HAT_Y to 0.5f).buttons).isEqualTo(0)
        assertThat(send(AXIS_HAT_X to 0.5f, AXIS_HAT_Y to -0.5f).buttons).isEqualTo(0)
        assertThat(send(AXIS_HAT_X to -0.51f).buttons).isEqualTo(LEFT_FLAG)
        assertThat(send(AXIS_HAT_X to 0.51f).buttons).isEqualTo(RIGHT_FLAG)
        assertThat(send(AXIS_HAT_Y to -0.51f).buttons).isEqualTo(UP_FLAG)
        assertThat(send(AXIS_HAT_Y to 0.51f).buttons).isEqualTo(DOWN_FLAG)
    }

    @Test
    fun `an event moves the hat and leaves the other buttons`() {
        pad.press(KEYCODE_BUTTON_A)
        assertThat(send(AXIS_HAT_X to 1f).buttons).isEqualTo(A_FLAG or RIGHT_FLAG)
        assertThat(send(AXIS_HAT_X to 0f).buttons).isEqualTo(A_FLAG)
    }

    @Test
    fun `a motion event clears the D-pad keys of a pad that has a hat`() {
        pad.press(KEYCODE_DPAD_LEFT)
        pad.press(KEYCODE_DPAD_UP)
        assertThat(rig.packets.last().buttons).isEqualTo(LEFT_FLAG or UP_FLAG)

        assertThat(send().buttons).isEqualTo(0)
    }

    @Test
    fun `the hat marks its axes as used and keeps the mark`() {
        assertThat(pad.getBoolean("hatXAxisUsed")).isFalse()
        assertThat(pad.getBoolean("hatYAxisUsed")).isFalse()

        send(AXIS_HAT_X to -1f)
        assertThat(pad.getBoolean("hatXAxisUsed")).isTrue()
        assertThat(pad.getBoolean("hatYAxisUsed")).isFalse()

        send(AXIS_HAT_Y to 1f)
        send()
        assertThat(pad.getBoolean("hatXAxisUsed")).isTrue()
        assertThat(pad.getBoolean("hatYAxisUsed")).isTrue()
    }

    @Test
    fun `a hat that is only at rest leaves its axes unused`() {
        send(AXIS_HAT_X to 0.5f, AXIS_HAT_Y to -0.5f)

        assertThat(pad.getBoolean("hatXAxisUsed")).isFalse()
        assertThat(pad.getBoolean("hatYAxisUsed")).isFalse()
    }

    @Test
    fun `the D-pad keys are ignored, down and up, on the axis of a hat that was used`() {
        send(AXIS_HAT_X to 1f)
        send()
        val packets = rig.packets.size

        // X is used: left and right are suppressed, up and down still go through.
        assertThat(pad.press(KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(pad.press(KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(pad.release(KEYCODE_DPAD_LEFT, downTime = rig.now - 100)).isTrue()
        assertThat(pad.release(KEYCODE_DPAD_RIGHT, downTime = rig.now - 100)).isTrue()
        assertThat(rig.packets).hasSize(packets)

        pad.press(KEYCODE_DPAD_UP)
        assertThat(rig.packets.last().buttons).isEqualTo(UP_FLAG)
        pad.press(KEYCODE_DPAD_DOWN)
        assertThat(rig.packets.last().buttons).isEqualTo(UP_FLAG or DOWN_FLAG)
    }

    @Test
    fun `the Y axis of a used hat silences up and down the same way`() {
        send(AXIS_HAT_Y to 1f)
        send()
        val packets = rig.packets.size

        assertThat(pad.press(KEYCODE_DPAD_UP)).isTrue()
        assertThat(pad.press(KEYCODE_DPAD_DOWN)).isTrue()
        assertThat(pad.release(KEYCODE_DPAD_UP, downTime = rig.now - 100)).isTrue()
        assertThat(pad.release(KEYCODE_DPAD_DOWN, downTime = rig.now - 100)).isTrue()
        assertThat(rig.packets).hasSize(packets)
    }

    @Test
    fun `a diagonal key is ignored only once both axes of the hat were used`() {
        val diagonals =
            listOf(KEYCODE_DPAD_UP_LEFT, KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT)
        send(AXIS_HAT_X to 1f)
        send()
        val oneAxis = rig.packets.size

        for (keyCode in diagonals) {
            pad.press(keyCode)
            pad.release(keyCode, downTime = rig.now - 100)
        }
        // One axis used: every diagonal still sends a down and an up packet.
        assertThat(rig.packets.size - oneAxis).isEqualTo(8)

        send(AXIS_HAT_Y to 1f)
        send()
        val bothAxes = rig.packets.size
        for (keyCode in diagonals) {
            assertThat(pad.press(keyCode)).isTrue()
            assertThat(pad.release(keyCode, downTime = rig.now - 100)).isTrue()
        }

        assertThat(rig.packets).hasSize(bothAxes)
    }

    // ----- Which axes a context reads -----

    @Test
    fun `a context reads the axes it was given, not the default ones`() {
        val xbox =
            rig.addPad(
                7,
                fields =
                    mapOf(
                        "leftStickXAxis" to AXIS_X,
                        "leftStickYAxis" to AXIS_Y,
                        "rightStickXAxis" to AXIS_RX,
                        "rightStickYAxis" to AXIS_RY,
                        "leftTriggerAxis" to AXIS_Z,
                        "rightTriggerAxis" to AXIS_RZ,
                    ),
            )

        xbox.motion(mapOf(AXIS_RX to 1f, AXIS_RY to -1f, AXIS_Z to 1f, AXIS_RZ to 0.25f, AXIS_BRAKE to 1f))

        assertThat(rig.packets.last())
            .isEqualTo(
                Sent(0, 1, 0, leftTrigger = -1, rightTrigger = 63, rightStickX = 32766, rightStickY = 32766)
            )
    }

    @Test
    fun `a stick with only one of its axes is not read, and keeps what it had`() {
        val half =
            rig.addPad(
                7,
                fields =
                    mapOf(
                        "leftStickXAxis" to AXIS_X,
                        "leftStickYAxis" to -1,
                        "rightStickXAxis" to -1,
                        "rightStickYAxis" to AXIS_RZ,
                        "leftStickX" to 111.toShort(),
                        "leftStickY" to 222.toShort(),
                        "rightStickX" to 333.toShort(),
                        "rightStickY" to 444.toShort(),
                    ),
            )

        half.motion(mapOf(AXIS_X to 1f, AXIS_Y to 1f, AXIS_Z to 1f, AXIS_RZ to 1f))

        assertThat(rig.packets.last())
            .isEqualTo(Sent(0, 1, 0, leftStickX = 111, leftStickY = 222, rightStickX = 333, rightStickY = 444))
    }

    @Test
    fun `triggers with only one axis are not read, and keep what they had`() {
        val half =
            rig.addPad(
                7,
                fields =
                    mapOf(
                        "leftTriggerAxis" to AXIS_BRAKE,
                        "rightTriggerAxis" to -1,
                        "leftTrigger" to 5.toByte(),
                        "rightTrigger" to 6.toByte(),
                    ),
            )

        half.motion(mapOf(AXIS_BRAKE to 1f, AXIS_GAS to 1f))

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0, leftTrigger = 5, rightTrigger = 6))
        assertThat(half.getBoolean("leftTriggerAxisUsed")).isFalse()
    }

    @Test
    fun `a context with no hat axes keeps the D-pad keys through a motion event`() {
        val noHat = rig.addPad(7)

        noHat.press(KEYCODE_DPAD_LEFT)
        noHat.motion(mapOf(AXIS_HAT_X to 1f))

        assertThat(rig.packets.last().buttons).isEqualTo(LEFT_FLAG)
        assertThat(noHat.getBoolean("hatXAxisUsed")).isFalse()
    }

    @Test
    fun `a context with hat axes reads them from the hat axes whichever axes it names`() {
        val named = rig.addPad(7, fields = mapOf("hatXAxis" to AXIS_RX, "hatYAxis" to AXIS_RY))

        named.motion(mapOf(AXIS_HAT_X to 1f, AXIS_HAT_Y to 1f, AXIS_RX to -1f, AXIS_RY to -1f))

        assertThat(rig.packets.last().buttons).isEqualTo(RIGHT_FLAG or DOWN_FLAG)
    }

    // ----- Events -----

    @Test
    fun `every motion event sends a packet, even one that changes nothing`() {
        pad.motion(mapOf(AXIS_X to 0.5f))
        pad.motion(mapOf(AXIS_X to 0.5f))
        pad.motion(mapOf())

        assertThat(rig.packets).hasSize(3)
    }

    @Test
    fun `an event of a pad reaches the context of that pad and its controller number`() {
        val second = rig.addPad(7, controllerNumber = 2)

        second.motion(mapOf(AXIS_X to 1f))

        assertThat(rig.packets).containsExactly(Sent(2, 1, 0))
        // The pad has no axes yet: a context made by hand reads none, so nothing moved.
        assertThat(second.getShort("leftStickX")).isEqualTo(0)
    }

    @Test
    fun `an event of a device Android no longer has is taken and does nothing`() {
        val gone = Pad(rig, 55, null, rig.default.context)

        assertThat(gone.motion(mapOf(AXIS_X to 1f))).isTrue()
        assertThat(gone.press(KEYCODE_BUTTON_A)).isTrue()
        assertThat(gone.release(KEYCODE_BUTTON_A, downTime = rig.now - 100)).isTrue()

        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `events after the handler stopped are taken and do nothing`() {
        rig.handler.stop()

        assertThat(pad.motion(mapOf(AXIS_X to 1f))).isTrue()
        assertThat(pad.press(KEYCODE_BUTTON_A)).isTrue()
        assertThat(pad.release(KEYCODE_BUTTON_A, downTime = rig.now - 100)).isTrue()

        assertThat(rig.packets).isEmpty()
    }

    @Test
    @Config(sdk = [30])
    fun `on Android 11 the virtual device -1 is the default context even when a pad context has its id`() {
        val virtual = rig.addPad(-1, controllerNumber = 3)

        virtual.motion(mapOf(AXIS_X to 1f))

        // The default context read X as its left stick; the pad of id -1 read nothing.
        assertThat(rig.packets).containsExactly(Sent(0, 1, 0, leftStickX = 32766))
    }

    @Test
    @Config(sdk = [35])
    fun `on a newer Android the device -1 has its own context`() {
        val virtual = rig.addPad(-1, controllerNumber = 3)

        virtual.motion(mapOf(AXIS_X to 1f))

        assertThat(rig.packets).containsExactly(Sent(3, 1, 0))
    }
}
