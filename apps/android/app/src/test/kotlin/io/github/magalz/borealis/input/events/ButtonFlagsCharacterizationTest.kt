package io.github.magalz.borealis.input.events

import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_1
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_C
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_L2
import android.view.KeyEvent.KEYCODE_BUTTON_MODE
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_R2
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_BUTTON_THUMBL
import android.view.KeyEvent.KEYCODE_BUTTON_THUMBR
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_BUTTON_Y
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT
import android.view.KeyEvent.KEYCODE_MEDIA_RECORD
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_SEARCH
import android.view.KeyEvent.KEYCODE_UNKNOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.B_FLAG
import com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG
import com.limelight.nvstream.input.ControllerPacket.LS_CLK_FLAG
import com.limelight.nvstream.input.ControllerPacket.MISC_FLAG
import com.limelight.nvstream.input.ControllerPacket.PADDLE1_FLAG
import com.limelight.nvstream.input.ControllerPacket.PADDLE2_FLAG
import com.limelight.nvstream.input.ControllerPacket.PADDLE3_FLAG
import com.limelight.nvstream.input.ControllerPacket.PADDLE4_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.RB_FLAG
import com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG
import com.limelight.nvstream.input.ControllerPacket.RS_CLK_FLAG
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import com.limelight.nvstream.input.ControllerPacket.TOUCHPAD_FLAG
import com.limelight.nvstream.input.ControllerPacket.UP_FLAG
import com.limelight.nvstream.input.ControllerPacket.X_FLAG
import com.limelight.nvstream.input.ControllerPacket.Y_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `handleButtonDown` and `handleButtonUp`: which key code sets which button of the packet, which
 * keys the handler leaves to the system, and what the keys do to the pad's state. The pad is a
 * system pad with no Select or Mode button, so Start reaches the PC at once.
 */
@RunWith(RobolectricTestRunner::class)
class ButtonFlagsCharacterizationTest {
    private val rig = EventsRig()
    private val pad = rig.addPad(7)

    /** Every key that is one button, with the flag it sets. */
    private val buttons =
        listOf(
            KEYCODE_BUTTON_A to A_FLAG,
            KEYCODE_DPAD_CENTER to A_FLAG,
            KEYCODE_BUTTON_B to B_FLAG,
            KEYCODE_BUTTON_X to X_FLAG,
            KEYCODE_BUTTON_Y to Y_FLAG,
            KEYCODE_BUTTON_L1 to LB_FLAG,
            KEYCODE_BUTTON_R1 to RB_FLAG,
            KEYCODE_BUTTON_THUMBL to LS_CLK_FLAG,
            KEYCODE_BUTTON_THUMBR to RS_CLK_FLAG,
            KEYCODE_BUTTON_MODE to SPECIAL_BUTTON_FLAG,
            KEYCODE_BUTTON_START to PLAY_FLAG,
            KEYCODE_MENU to PLAY_FLAG,
            KEYCODE_MEDIA_RECORD to MISC_FLAG,
            KEYCODE_BUTTON_1 to TOUCHPAD_FLAG,
            KEYCODE_DPAD_UP to UP_FLAG,
            KEYCODE_DPAD_DOWN to DOWN_FLAG,
            KEYCODE_DPAD_LEFT to LEFT_FLAG,
            KEYCODE_DPAD_RIGHT to RIGHT_FLAG,
            KEYCODE_DPAD_UP_LEFT to (UP_FLAG or LEFT_FLAG),
            KEYCODE_DPAD_UP_RIGHT to (UP_FLAG or RIGHT_FLAG),
            KEYCODE_DPAD_DOWN_LEFT to (DOWN_FLAG or LEFT_FLAG),
            KEYCODE_DPAD_DOWN_RIGHT to (DOWN_FLAG or RIGHT_FLAG),
        )

    @Test
    fun `each button key sets its flag in the packet when it goes down and clears it when it goes up`() {
        for ((keyCode, flag) in buttons) {
            val handledDown = pad.press(keyCode)
            val down = rig.packets.last()
            rig.advanceBy(100)
            val handledUp = pad.release(keyCode)
            val up = rig.packets.last()

            assertWithMessage("down $keyCode handled").that(handledDown).isTrue()
            assertWithMessage("down $keyCode").that(down.buttons).isEqualTo(flag)
            assertWithMessage("up $keyCode handled").that(handledUp).isTrue()
            assertWithMessage("up $keyCode").that(up.buttons).isEqualTo(0)
        }
    }

    @Test
    fun `a button key sends one packet to controller 0 with the gamepad mask of the setting`() {
        pad.press(KEYCODE_BUTTON_A)

        assertThat(rig.packets)
            .containsExactly(Sent(controller = 0, activeMask = 1, buttons = A_FLAG))
    }

    @Test
    fun `Select and Back are the Select button, which a pad without one holds back for 60 ms`() {
        for (keyCode in listOf(KEYCODE_BUTTON_SELECT, KEYCODE_BACK)) {
            rig.connection.packets.clear()
            pad.set("hasSelect", false)

            pad.press(keyCode)
            val atOnce = rig.buttons()
            rig.advanceBy(60)
            val later = rig.buttons()
            pad.release(keyCode)

            assertWithMessage("$keyCode at once").that(atOnce).containsExactly(0)
            assertWithMessage("$keyCode after 60 ms")
                .that(later)
                .containsExactly(0, BACK_FLAG)
                .inOrder()
            assertWithMessage("$keyCode up").that(rig.packets.last().buttons).isEqualTo(0)
        }
    }

    @Test
    fun `a Select or Back press tells the context the pad has a Select button`() {
        assertThat(pad.getBoolean("hasSelect")).isFalse()

        pad.press(KEYCODE_BUTTON_SELECT)

        assertThat(pad.getBoolean("hasSelect")).isTrue()
    }

    @Test
    fun `a Back press tells the context the pad has a Select button`() {
        pad.press(KEYCODE_BACK)

        assertThat(pad.getBoolean("hasSelect")).isTrue()
    }

    @Test
    fun `a Mode press tells the context the pad has a Mode button`() {
        assertThat(pad.getBoolean("hasMode")).isFalse()

        pad.press(KEYCODE_BUTTON_MODE)

        assertThat(pad.getBoolean("hasMode")).isTrue()
    }

    @Test
    fun `the digital triggers set the whole trigger byte and a release clears it`() {
        assertThat(pad.press(KEYCODE_BUTTON_L2)).isTrue()
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0, leftTrigger = -1))
        pad.release(KEYCODE_BUTTON_L2, downTime = rig.now - 100)
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0))

        assertThat(pad.press(KEYCODE_BUTTON_R2)).isTrue()
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0, rightTrigger = -1))
        pad.release(KEYCODE_BUTTON_R2, downTime = rig.now - 100)
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0))
    }

    @Test
    fun `a digital trigger is ignored, down and up, once the analog trigger axis has been used`() {
        pad.set("leftTriggerAxisUsed", true)
        pad.set("rightTriggerAxisUsed", true)
        val packets = rig.packets.size

        assertThat(pad.press(KEYCODE_BUTTON_L2)).isTrue()
        assertThat(pad.release(KEYCODE_BUTTON_L2, downTime = rig.now - 100)).isTrue()
        assertThat(pad.press(KEYCODE_BUTTON_R2)).isTrue()
        assertThat(pad.release(KEYCODE_BUTTON_R2, downTime = rig.now - 100)).isTrue()

        assertThat(rig.packets).hasSize(packets)
    }

    @Test
    fun `an analog trigger on one side does not silence the digital trigger of the other`() {
        pad.set("leftTriggerAxisUsed", true)

        pad.press(KEYCODE_BUTTON_R2)

        assertThat(rig.packets.last().rightTrigger).isEqualTo(-1)
    }

    @Test
    fun `a key the handler does not know is left to the system, down and up`() {
        for (keyCode in listOf(KEYCODE_A, KEYCODE_VOLUME_UP, KEYCODE_SEARCH, KEYCODE_BUTTON_C)) {
            assertWithMessage("down $keyCode").that(pad.press(keyCode)).isFalse()
            assertWithMessage("up $keyCode")
                .that(pad.release(keyCode, downTime = rig.now - 100))
                .isFalse()
        }

        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `an unknown key code with no paddles on the pad is left to the system`() {
        assertThat(pad.press(KEYCODE_UNKNOWN, KeyDetail(scanCode = 0x2c4))).isFalse()
        assertThat(
                pad.release(KEYCODE_UNKNOWN, KeyDetail(scanCode = 0x2c4), downTime = rig.now - 100)
            )
            .isFalse()

        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the paddles are the evdev trigger-happy buttons 5 to 8 on a pad that has paddles`() {
        pad.set("hasPaddles", true)
        val paddles =
            listOf(
                0x2c4 to PADDLE1_FLAG,
                0x2c5 to PADDLE2_FLAG,
                0x2c6 to PADDLE3_FLAG,
                0x2c7 to PADDLE4_FLAG,
            )

        for ((scanCode, flag) in paddles) {
            assertWithMessage("down $scanCode")
                .that(pad.press(KEYCODE_UNKNOWN, KeyDetail(scanCode = scanCode)))
                .isTrue()
            assertWithMessage("down $scanCode flag")
                .that(rig.packets.last().buttons)
                .isEqualTo(flag)
            rig.advanceBy(100)
            assertWithMessage("up $scanCode")
                .that(pad.release(KEYCODE_UNKNOWN, KeyDetail(scanCode = scanCode)))
                .isTrue()
            assertWithMessage("up $scanCode flag").that(rig.packets.last().buttons).isEqualTo(0)
        }
    }

    @Test
    fun `an unknown scan code on a pad with paddles leaves the whole event to the system, down and up`() {
        pad.set("hasPaddles", true)

        for (scanCode in listOf(0x2c8, 0x2c9, 0)) {
            assertWithMessage("down $scanCode")
                .that(pad.press(KEYCODE_UNKNOWN, KeyDetail(scanCode = scanCode)))
                .isFalse()
            assertWithMessage("up $scanCode")
                .that(
                    pad.release(
                        KEYCODE_UNKNOWN,
                        KeyDetail(scanCode = scanCode),
                        downTime = rig.now - 100,
                    )
                )
                .isFalse()
        }

        // Nothing after the switch ran for them: no packet, and no quit combo or emulation check.
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the buttons of a pad add up in the packet and each one goes up on its own`() {
        pad.press(KEYCODE_BUTTON_A)
        pad.press(KEYCODE_BUTTON_X)
        pad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_X)

        assertThat(rig.buttons())
            .containsExactly(
                A_FLAG,
                A_FLAG or X_FLAG,
                A_FLAG or X_FLAG or LB_FLAG,
                A_FLAG or LB_FLAG,
            )
            .inOrder()
    }

    @Test
    fun `a key that goes down again while it is held sends its packet again`() {
        pad.press(KEYCODE_BUTTON_A)
        pad.press(KEYCODE_BUTTON_A, KeyDetail(repeat = 1))
        pad.press(KEYCODE_BUTTON_A, KeyDetail(repeat = 2))

        assertThat(rig.buttons()).containsExactly(A_FLAG, A_FLAG, A_FLAG)
    }

    @Test
    fun `a key up for a button that is not down still sends a packet`() {
        pad.release(KEYCODE_BUTTON_B, downTime = rig.now - 100)

        assertThat(rig.packets).containsExactly(Sent(0, 1, 0))
    }

    @Test
    fun `flipping the face buttons swaps A and B and X and Y, on the way down and up`() {
        val flipped = EventsRig { flipFaceButtons = true }
        val flippedPad = flipped.addPad(7)
        val faces =
            listOf(
                KEYCODE_BUTTON_A to B_FLAG,
                KEYCODE_BUTTON_B to A_FLAG,
                KEYCODE_BUTTON_X to Y_FLAG,
                KEYCODE_BUTTON_Y to X_FLAG,
                // The other buttons stay as they are.
                KEYCODE_BUTTON_L1 to LB_FLAG,
                KEYCODE_DPAD_CENTER to A_FLAG,
            )

        for ((keyCode, flag) in faces) {
            flippedPad.press(keyCode)
            val down = flipped.packets.last().buttons
            flipped.advanceBy(100)
            flippedPad.release(keyCode)
            val up = flipped.packets.last().buttons

            assertWithMessage("down $keyCode").that(down).isEqualTo(flag)
            assertWithMessage("up $keyCode").that(up).isEqualTo(0)
        }
    }

    @Test
    fun `the faces keep their own flags when the setting is off`() {
        pad.press(KEYCODE_BUTTON_A)
        assertThat(rig.packets.last().buttons).isEqualTo(A_FLAG)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_A)

        pad.press(KEYCODE_BUTTON_Y)
        assertThat(rig.packets.last().buttons).isEqualTo(Y_FLAG)
    }
}
