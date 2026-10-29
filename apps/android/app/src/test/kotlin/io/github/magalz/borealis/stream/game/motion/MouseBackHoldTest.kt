package io.github.magalz.borealis.stream.game.motion

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.keys.mouseBack
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import org.junit.Test

/**
 * The Back button of the mouse, the one under the thumb. Held for a second it opens the in-game
 * menu and sends nothing to the host; pressed for less it is the X1 button of the host, sent when
 * it comes up. The Back that Android makes of a right click is another thing and never opens the
 * menu.
 */
internal class MouseBackHoldTest : MotionTestBase() {
    private val back = MouseButtonPacket.BUTTON_X1
    private val forward = MouseButtonPacket.BUTTON_X2
    private val right = MouseButtonPacket.BUTTON_RIGHT

    /** The mouse reports [buttons] as down, at (10, 20) of the stream view. */
    private fun mouse(action: Int, buttons: Int): Boolean =
        rig.game.onGenericMotion(streamView, mouseEvent(action, 10f, 20f, buttons))

    private fun streamingWithNavigationButtons() {
        rig.preference("checkbox_mouse_nav_buttons", true)
        streaming()
    }

    private fun thePointer() = position(10, 20)

    private fun panel(): Panel = (rig.phase as StreamPhase.Streaming).panel

    /** The buttons that reached the host: the calls of the mouse buttons, nothing else. */
    private fun buttonsSent() = sent().filter { it.name == "sendMouseButton" }

    private fun assertSent(vararg calls: Sent) {
        assertThat(sent()).containsExactlyElementsIn(calls).inOrder()
        forgetSent()
    }

    // ----- A hold -----

    @Test
    fun `held for a second the Back button opens the menu`() {
        streamingWithNavigationButtons()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(999)
        assertThat(panel()).isEqualTo(Panel.NONE)
        idle(1)

        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `a hold sends nothing to the host, when the menu opens or when the button comes up`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        forgetSent()

        idle(1_000)
        mouse(MotionEvent.ACTION_UP, 0)
        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.MENU)
        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `the release of a hold that the chrome did not take the input for is eaten too`() {
        streamingWithNavigationButtons()
        // In picture-in-picture the chrome is hidden and takes nothing, so the stream sees the
        // release.
        rig.session.onPip(true)
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(1_000)
        assertThat(panel()).isEqualTo(Panel.MENU)
        forgetSent()

        mouse(MotionEvent.ACTION_UP, 0)

        assertSent(thePointer())
    }

    @Test
    fun `a press after a hold is a new press, short or long`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(1_000)
        // The chrome has the input and the release goes to it; the menu closes.
        rig.session.closePanel()
        idle(1)
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        mouse(MotionEvent.ACTION_UP, 0)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a menu that opened another way during the hold is not opened again`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(500)
        rig.session.openMenu()
        idle(1)
        rig.session.closePanel()
        idle(1)

        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(buttonsSent()).isEmpty()
    }

    // ----- A short press -----

    @Test
    fun `a short press is the X1 button of the host, sent when the button comes up`() {
        streamingWithNavigationButtons()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        assertSent(thePointer())
        idle(500)
        mouse(MotionEvent.ACTION_UP, 0)

        assertSent(thePointer(), press(back), release(back))
        idle(2_000)
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a press that is one millisecond short of a second is a short press`() {
        streamingWithNavigationButtons()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(999)
        mouse(MotionEvent.ACTION_UP, 0)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
        idle(2_000)
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the other buttons are sent at once while Back waits`() {
        streamingWithNavigationButtons()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD)

        assertSent(thePointer(), press(forward))
    }

    // ----- The Back that is not the button -----

    @Test
    fun `the Back that Android makes of a right click never opens the menu`() {
        streamingWithNavigationButtons()

        for (source in listOf(InputDevice.SOURCE_MOUSE, InputDevice.SOURCE_MOUSE_RELATIVE)) {
            rig.game.handleKeyDown(mouseBack(KeyEvent.ACTION_DOWN, source))
            idle(2_000)
            rig.game.handleKeyUp(mouseBack(KeyEvent.ACTION_UP, source))
        }

        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `a right click held for seconds never opens the menu`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        idle(2_000)
        mouse(MotionEvent.ACTION_UP, 0)

        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(buttonsSent()).containsExactly(press(right), release(right)).inOrder()
    }

    @Test
    fun `without the navigation buttons the Back of a mouse is a right click and never opens the menu`() {
        streaming()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        rig.game.handleKeyDown(mouseBack(KeyEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE))
        idle(2_000)
        rig.game.handleKeyUp(mouseBack(KeyEvent.ACTION_UP, InputDevice.SOURCE_MOUSE))
        mouse(MotionEvent.ACTION_UP, 0)

        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(buttonsSent()).containsExactly(press(right), release(right)).inOrder()
    }
}
