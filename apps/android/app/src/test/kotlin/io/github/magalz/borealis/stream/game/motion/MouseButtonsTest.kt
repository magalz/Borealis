package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * The mouse buttons: what a change of the button state of a mouse event sends to the host, in which
 * order, and how the activity remembers the buttons that are down. A mouse event with a view first
 * places the pointer, so every event here sends the position before its buttons.
 */
internal class MouseButtonsTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT
    private val middle = MouseButtonPacket.BUTTON_MIDDLE
    private val right = MouseButtonPacket.BUTTON_RIGHT
    private val back = MouseButtonPacket.BUTTON_X1
    private val forward = MouseButtonPacket.BUTTON_X2

    /** The mouse reports [buttons] as down, at (10, 20) of the stream view. */
    private fun mouse(action: Int, buttons: Int): Boolean =
        rig.game.onGenericMotion(streamView, mouseEvent(action, 10f, 20f, buttons))

    private fun thePointer() = position(10, 20)

    private fun assertSent(vararg calls: Sent) {
        assertThat(sent()).containsExactlyElementsIn(calls).inOrder()
        forgetSent()
    }

    // ----- One button at a time -----

    @Test
    fun `each button of the mouse is the button of the host that it stands for`() {
        rig.preference("checkbox_mouse_nav_buttons", true)
        streaming()
        val table =
            listOf(
                MotionEvent.BUTTON_PRIMARY to left,
                MotionEvent.BUTTON_SECONDARY to right,
                MotionEvent.BUTTON_STYLUS_PRIMARY to right,
                MotionEvent.BUTTON_TERTIARY to middle,
                MotionEvent.BUTTON_STYLUS_SECONDARY to middle,
                MotionEvent.BUTTON_BACK to back,
                MotionEvent.BUTTON_FORWARD to forward,
            )

        for ((state, button) in table) {
            // The press, the move that holds it and the release.
            assertWithMessage("down $state").that(mouse(MotionEvent.ACTION_DOWN, state)).isTrue()
            assertSent(thePointer(), press(button))
            assertWithMessage("move $state").that(mouse(MotionEvent.ACTION_MOVE, state)).isTrue()
            assertSent(thePointer())
            assertWithMessage("up $state").that(mouse(MotionEvent.ACTION_UP, 0)).isTrue()
            assertSent(thePointer(), release(button))
        }
    }

    @Test
    fun `a mouse that moves with no button down sends only where it is`() {
        streaming()

        assertThat(mouse(MotionEvent.ACTION_HOVER_MOVE, 0)).isTrue()

        assertSent(thePointer())
    }

    @Test
    fun `the buttons that the mouse has beyond the three are sent only when the setting is on`() {
        streaming()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD)
        assertSent(thePointer())
        mouse(MotionEvent.ACTION_UP, 0)
        assertSent(thePointer())
    }

    @Test
    fun `buttons that change together are sent in the order left, right, middle, back, forward`() {
        rig.preference("checkbox_mouse_nav_buttons", true)
        streaming()
        val all =
            MotionEvent.BUTTON_PRIMARY or
                MotionEvent.BUTTON_SECONDARY or
                MotionEvent.BUTTON_TERTIARY or
                MotionEvent.BUTTON_BACK or
                MotionEvent.BUTTON_FORWARD

        mouse(MotionEvent.ACTION_DOWN, all)
        assertSent(
            thePointer(),
            press(left),
            press(right),
            press(middle),
            press(back),
            press(forward),
        )

        mouse(MotionEvent.ACTION_UP, 0)
        assertSent(
            thePointer(),
            release(left),
            release(right),
            release(middle),
            release(back),
            release(forward),
        )
    }

    @Test
    fun `only the buttons that changed are sent`() {
        streaming()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        assertSent(thePointer(), press(left))
        // The right button joins; the left button is still down and is not pressed again.
        mouse(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)
        assertSent(thePointer(), press(right))
        // The left button goes up; the right button is still down.
        mouse(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_SECONDARY)
        assertSent(thePointer(), release(left))
    }

    @Test
    fun `two buttons of a group share the host button, which is pressed again at every change`() {
        streaming()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        assertSent(thePointer(), press(right))
        // The stylus button joins the mouse button: the host button is pressed again.
        mouse(
            MotionEvent.ACTION_MOVE,
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY,
        )
        assertSent(thePointer(), press(right))
        // The mouse button goes up while the stylus button is down: pressed again, not released.
        mouse(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertSent(thePointer(), press(right))
        mouse(MotionEvent.ACTION_UP, 0)
        assertSent(thePointer(), release(right))
    }

    // ----- The Samsung DeX desktop mouse -----

    // The touchpad of DeX reports its pointer as a finger, which only its source makes a mouse.
    private fun dex(action: Int, buttons: Int = 0) =
        motionEventOf(SAMSUNG_DEX_SOURCE, action) {
            pointer(10f, 20f, MotionEvent.TOOL_TYPE_FINGER)
            buttonState = buttons
        }

    @Test
    fun `the DeX touchpad clicks the left button with a plain touch down and up`() {
        streaming()

        assertThat(rig.game.onGenericMotion(streamView, dex(MotionEvent.ACTION_DOWN))).isTrue()
        assertSent(thePointer(), press(left))
        // The left button that the activity made up is still down while the pointer moves.
        rig.game.onGenericMotion(streamView, dex(MotionEvent.ACTION_MOVE))
        assertSent(thePointer())
        rig.game.onGenericMotion(streamView, dex(MotionEvent.ACTION_UP))
        assertSent(thePointer(), release(left))
        // Moving with nothing down sends nothing more than where the pointer is.
        rig.game.onGenericMotion(streamView, dex(MotionEvent.ACTION_HOVER_MOVE))
        assertSent(thePointer())
    }

    @Test
    fun `the DeX touchpad right click comes with the left button that it did not send`() {
        streaming()

        rig.game.onGenericMotion(
            streamView,
            dex(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY),
        )
        assertSent(thePointer(), press(left), press(right))
        rig.game.onGenericMotion(streamView, dex(MotionEvent.ACTION_UP, 0))
        assertSent(thePointer(), release(left), release(right))
    }

    @Test
    fun `a mouse that goes down with no button state clicks nothing`() {
        streaming()

        mouse(MotionEvent.ACTION_DOWN, 0)
        assertSent(thePointer())
        mouse(MotionEvent.ACTION_UP, 0)
        assertSent(thePointer())
    }

    // ----- A pointer that is not captured -----

    @Test
    fun `a mouse is consumed and ignored while the activity does not capture the pointer`() {
        streaming()
        captureProvider().disableCapture()

        assertThat(mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)).isTrue()
        assertThat(
                rig.game.onGenericMotion(
                    streamView,
                    motionEventOf(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_MOVE) {
                        pointer(5f, 6f)
                    },
                )
            )
            .isTrue()

        assertThat(sent()).isEmpty()
    }

    @Test
    fun `an ignored mouse press is not remembered, the press is sent when the capture is back`() {
        streaming()
        captureProvider().disableCapture()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        captureProvider().enableCapture()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)

        assertSent(thePointer(), press(left))
    }

    @Test
    fun `a finger does not depend on the capture of the pointer`() {
        streaming()
        rig.session.setTouchMode(TouchMode.DIRECT)
        captureProvider().disableCapture()

        assertThat(
                rig.game.onTouch(
                    streamView,
                    fingerEvent(MotionEvent.ACTION_DOWN, listOf(100f to 50f)),
                )
            )
            .isTrue()
        rig.game.onTouch(streamView, fingerEvent(MotionEvent.ACTION_UP, listOf(100f to 50f)))

        assertSent(position(100, 50), press(left))
    }

    // ----- The buttons that are down when the chrome takes the input -----

    @Test
    fun `a button that is down when the chrome takes the input is released, and pressed anew after`() {
        streaming()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        forgetSent()

        rig.session.openMenu()
        assertSent(release(left))

        // The chrome gives the input back and the pointer is grabbed again; the button that the
        // user still holds is a new press for the host.
        rig.session.closePanel()
        idle(1)
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        assertSent(thePointer(), press(left))
    }

    private companion object {
        /** A touchpad of Samsung DeX: the sources of a touch screen and a mouse, together. */
        const val SAMSUNG_DEX_SOURCE = 12290
    }
}
