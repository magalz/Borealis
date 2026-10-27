package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.game.GRAB_DELAY_MS
import io.github.magalz.borealis.stream.game.session
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * Which motion events the stream activity takes at all: the chrome takes them first, a pointer that
 * is not grabbed leaves them to the system, a joystick source goes to the controller handler, and a
 * source that is none of the classes that the activity reads is not consumed.
 */
internal class MotionGateTest : MotionTestBase() {
    private val joystickAxis = Axes.X

    private fun joystick(deflection: Float, device: InputDevice? = null) =
        motionEventOf(InputDevice.SOURCE_JOYSTICK, MotionEvent.ACTION_MOVE) {
            this.device = device
            pointer(0f, 0f, MotionEvent.TOOL_TYPE_UNKNOWN, mapOf(joystickAxis to deflection))
        }

    private fun press() = mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, MotionEvent.BUTTON_PRIMARY)

    private fun fingerDown() = fingerEvent(MotionEvent.ACTION_DOWN, listOf(100f to 200f))

    private fun hover() = mouseEvent(MotionEvent.ACTION_HOVER_MOVE, 10f, 20f)

    private fun relativeMove() =
        motionEventOf(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_MOVE) {
            pointer(5f, 6f)
        }

    // ----- The chrome first -----

    @Test
    fun `a modal chrome takes every motion event, even where the pointer is still grabbed`() {
        streaming()
        rig.session.openMenu()
        // The chrome released the pointer when it took the input: grab it again, so that the chrome
        // is the only reason the events do not get through.
        ReflectionHelpers.setField(rig.game, "grabbedInput", true)
        assertThat(rig.session.chromeMode).isEqualTo(ChromeMode.MODAL)
        forgetSent()

        val results =
            mapOf(
                "a mouse press on the stream view" to rig.game.onGenericMotion(streamView, press()),
                "a finger on the background view" to rig.game.onTouch(backgroundView, fingerDown()),
                "a joystick" to rig.game.onGenericMotion(streamView, joystick(1f)),
                "a hover of the activity" to rig.game.onGenericMotionEvent(hover()),
                "a captured mouse" to
                    capturedPointerListener().onCapturedPointer(streamView, relativeMove()),
            )

        for ((what, handled) in results) {
            assertWithMessage(what).that(handled).isFalse()
        }
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `a chrome that is not modal leaves the motion to the stream`() {
        streaming()
        rig.session.setOverlay(OverlayMode.COMPACT)
        assertThat(rig.session.chromeMode).isEqualTo(ChromeMode.OVERLAY)

        val handled = rig.game.onGenericMotion(streamView, press())

        assertThat(handled).isTrue()
        assertThat(sent())
            .containsExactly(position(10, 20), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
    }

    // ----- A pointer that is not grabbed -----

    /**
     * The stream is up and its chrome is out of the way, but the pointer is grabbed half a second
     * later: the state of the activity in between.
     */
    private fun streamingBeforeTheGrab() {
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()
        assertThat(rig.session.chromeMode).isNotEqualTo(ChromeMode.MODAL)
        assertThat(rig.field<Boolean>("grabbedInput")).isFalse()
    }

    @Test
    fun `a pointer that is not grabbed leaves every kind of motion to the system`() {
        streamingBeforeTheGrab()

        val results =
            mapOf(
                "a mouse press" to rig.game.onGenericMotion(streamView, press()),
                "a relative mouse" to rig.game.onGenericMotion(streamView, relativeMove()),
                "a finger" to rig.game.onTouch(backgroundView, fingerDown()),
                "a joystick" to rig.game.onGenericMotion(streamView, joystick(1f)),
                "a hover of the activity" to rig.game.onGenericMotionEvent(hover()),
            )

        for ((what, handled) in results) {
            assertWithMessage(what).that(handled).isFalse()
        }
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `the same motion is taken once the pointer is grabbed`() {
        streamingBeforeTheGrab()
        idle(GRAB_DELAY_MS)

        assertThat(rig.game.onGenericMotion(streamView, press())).isTrue()
        assertThat(sent())
            .containsExactly(position(10, 20), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
    }

    // ----- The joystick -----

    @Test
    fun `a joystick source goes to the controller handler, which sends the stick to the host`() {
        streaming()

        val handled = rig.game.onGenericMotion(streamView, joystick(1f))

        assertThat(handled).isTrue()
        val packet = ShadowRecordingBridge.named("sendMultiControllerInput").single()
        // The left stick, pushed fully to the right: +0x7FFE.
        assertThat(packet.args[5]).isEqualTo(32766.toShort())
        assertThat(sentBesidesControllerPackets()).isEmpty()
    }

    @Test
    fun `a pad whose touchpad is clicked reports the touchpad button to the host, not a mouse click`() {
        streaming()
        addGamepadContext(PAD_ID, PAD_NUMBER)
        val click =
            motionEventOf(InputDevice.SOURCE_MOUSE, MotionEvent.ACTION_DOWN) {
                device = pad()
                pointer(5f, 5f)
                buttonState = MotionEvent.BUTTON_PRIMARY
            }

        val handled = rig.game.onGenericMotion(streamView, click)

        assertThat(handled).isTrue()
        val packet = ShadowRecordingBridge.named("sendMultiControllerInput").single()
        assertThat(packet.args[0]).isEqualTo(PAD_NUMBER)
        assertThat(packet.args[2]).isEqualTo(ControllerPacket.TOUCHPAD_FLAG)
        assertThat(sentBesidesControllerPackets()).isEmpty()
    }

    @Test
    fun `a mouse of a device that is not a pad known to the controller handler is a plain mouse`() {
        streaming()
        val click =
            motionEventOf(InputDevice.SOURCE_MOUSE, MotionEvent.ACTION_DOWN) {
                device = pad()
                pointer(5f, 5f)
                buttonState = MotionEvent.BUTTON_PRIMARY
            }

        val handled = rig.game.onGenericMotion(streamView, click)

        assertThat(handled).isTrue()
        assertThat(sent())
            .containsExactly(position(5, 5), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
    }

    @Test
    fun `a pad touchpad that the user maps to the mouse clicks the mouse as well`() {
        rig.preference("checkbox_gamepad_touchpad_as_mouse", true)
        streaming()
        addGamepadContext(PAD_ID, PAD_NUMBER)
        val click =
            motionEventOf(InputDevice.SOURCE_MOUSE, MotionEvent.ACTION_DOWN) {
                device = pad()
                pointer(5f, 5f)
                buttonState = MotionEvent.BUTTON_PRIMARY
            }

        val handled = rig.game.onGenericMotion(streamView, click)

        assertThat(handled).isTrue()
        assertThat(sentBesidesControllerPackets())
            .containsExactly(position(5, 5), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
        assertThat(ShadowRecordingBridge.named("sendMultiControllerInput")).hasSize(1)
    }

    // ----- A source that is none of the classes -----

    @Test
    fun `a source that is not a pointer, a position or a joystick is not consumed`() {
        streaming()

        for (source in
            listOf(
                InputDevice.SOURCE_KEYBOARD,
                InputDevice.SOURCE_TRACKBALL,
                InputDevice.SOURCE_DPAD,
                InputDevice.SOURCE_UNKNOWN,
            )) {
            val event =
                motionEventOf(source, MotionEvent.ACTION_MOVE) {
                    pointer(10f, 20f, MotionEvent.TOOL_TYPE_UNKNOWN)
                }

            assertWithMessage("source $source")
                .that(rig.game.onGenericMotion(streamView, event))
                .isFalse()
        }
        assertThat(sent()).isEmpty()
    }

    private fun pad(): InputDevice =
        inputDeviceOf(
            PAD_ID,
            InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_MOUSE,
        )

    private fun sentBesidesControllerPackets() =
        sent().filterNot { it.name == "sendMultiControllerInput" }

    private companion object {
        const val PAD_ID = 9
        const val PAD_NUMBER: Short = 2
    }
}
