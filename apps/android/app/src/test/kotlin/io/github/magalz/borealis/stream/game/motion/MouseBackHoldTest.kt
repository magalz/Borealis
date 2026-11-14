package io.github.magalz.borealis.stream.game.motion

import android.os.SystemClock
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
 * The side buttons of the mouse, the ones under the thumb. Back and Forward together, in either
 * order and within 250 ms of each other, open the in-game menu and send nothing to the host. Back
 * held for a second opens it too. Pressed for less, Back is the X1 button of the host, sent when it
 * comes up, and Forward is the X2 button, pressed once it has been down for 25 ms. A press that
 * comes up sooner than that is a pulse, as the Logi M650 sends them: the host gets it when the 250
 * ms are over, unless the other button joined. The Back that Android makes of a right click is
 * another thing and never opens the menu. Event times and the clock of the looper are one clock
 * here, which `idle` moves.
 */
internal class MouseBackHoldTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT
    private val back = MouseButtonPacket.BUTTON_X1
    private val forward = MouseButtonPacket.BUTTON_X2
    private val middle = MouseButtonPacket.BUTTON_MIDDLE
    private val right = MouseButtonPacket.BUTTON_RIGHT

    private val backState = MotionEvent.BUTTON_BACK
    private val forwardState = MotionEvent.BUTTON_FORWARD

    /** The buttons go down, stay down for [millis] and come up. */
    private fun tap(buttons: Int, millis: Long) {
        mouse(MotionEvent.ACTION_DOWN, buttons)
        idle(millis)
        mouse(MotionEvent.ACTION_UP, 0)
    }

    /** A pulse, as the side buttons of the Logi M650 send it. */
    private fun pulse(buttons: Int) = tap(buttons, PULSE_MS)

    /**
     * The side key of the mouse [code] goes down, the way the window hands it to the activity; the
     * result is the time when it did.
     */
    private fun keyDown(code: Int): Long {
        val at = SystemClock.uptimeMillis()
        rig.game.dispatchKeyEvent(mouseSideKey(KeyEvent.ACTION_DOWN, code, at))
        return at
    }

    private fun keyUp(code: Int, downTime: Long) {
        rig.game.dispatchKeyEvent(mouseSideKey(KeyEvent.ACTION_UP, code, downTime))
    }

    /** The side key goes down, stays down for [millis] and comes up. */
    private fun keyTap(code: Int, millis: Long) {
        val at = keyDown(code)
        idle(millis)
        keyUp(code, at)
    }

    private fun streamingWithNavigationButtons() {
        rig.preference("checkbox_mouse_nav_buttons", true)
        streaming()
    }

    private fun panel(): Panel = (rig.phase as StreamPhase.Streaming).panel

    /** The buttons that reached the host: the calls of the mouse buttons, nothing else. */
    private fun buttonsSent() = sent().filter { it.name == "sendMouseButton" }

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

        // A short press, not a pulse: it was down for as long as the mouse of a person takes.
        tap(MotionEvent.BUTTON_BACK, SETTLE_MS)

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

    @Test
    fun `a hold that the loss of the window focus ends does not open the menu`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(500)

        // The release of the button may come while another window has the focus.
        rig.game.onWindowFocusChanged(false)
        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.NONE)
        // No Back is left pressed: a release that is never seen is not a short press later.
        assertThat(rig.field<Int>("lastButtonState") and MotionEvent.BUTTON_BACK).isEqualTo(0)
        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `a hold that the stop of the activity ends does not open the menu`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK)
        idle(500)

        rig.stop()
        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a cancel of the mouse lets go of the buttons it holds, and the Back that waits never opens the menu`() {
        streamingWithNavigationButtons()
        val held =
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_BACK
        mouse(MotionEvent.ACTION_DOWN, held)
        // Forward comes after the 250 ms of Back, so the two are no chord.
        idle(WINDOW_MS)
        mouse(MotionEvent.ACTION_MOVE, held or MotionEvent.BUTTON_FORWARD)
        idle(SETTLE_MS)
        forgetSent()

        mouse(MotionEvent.ACTION_CANCEL, 0)
        idle(1_000)

        // The host holds no button now, and Back, which sent nothing yet, sends nothing: not the
        // X1 of a short press either.
        assertThat(buttonsSent())
            .containsExactly(release(right), release(middle), release(forward))
            .inOrder()
        assertThat(panel()).isEqualTo(Panel.NONE)
        // The activity forgot the buttons, so the next press of each is a press again (Back does
        // not join Forward here: it would be a chord).
        forgetSent()
        mouse(
            MotionEvent.ACTION_DOWN,
            MotionEvent.BUTTON_SECONDARY or
                MotionEvent.BUTTON_TERTIARY or
                MotionEvent.BUTTON_FORWARD,
        )
        idle(SETTLE_MS)
        assertThat(buttonsSent())
            .containsExactly(press(right), press(middle), press(forward))
            .inOrder()
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

        mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_SECONDARY)

        assertSent(thePointer(), press(right))
    }

    // ----- Back and Forward together -----

    @Test
    fun `Back and then Forward, pulses a moment apart, open the menu and neither reaches the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(backState)
        idle(100)
        assertThat(panel()).isEqualTo(Panel.NONE)
        mouse(MotionEvent.ACTION_DOWN, forwardState)
        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(PULSE_MS)
        mouse(MotionEvent.ACTION_UP, 0)
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `Forward and then Back, pulses a moment apart, open the menu and neither reaches the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(forwardState)
        idle(100)
        assertThat(panel()).isEqualTo(Panel.NONE)
        mouse(MotionEvent.ACTION_DOWN, backState)
        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(PULSE_MS)
        mouse(MotionEvent.ACTION_UP, 0)
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `Back and Forward in one event open the menu and neither reaches the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, backState or forwardState)

        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(1_000)
        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `a second button that comes 249 ms after the first one joins it`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(backState)
        idle(WINDOW_MS - 1 - PULSE_MS)
        mouse(MotionEvent.ACTION_DOWN, forwardState)

        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(1_000)
        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `a second button that comes 250 ms after the first one is alone, and the first went to the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(backState)
        idle(WINDOW_MS - PULSE_MS)
        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
        forgetSent()

        pulse(forwardState)
        assertThat(panel()).isEqualTo(Panel.NONE)
        idle(WINDOW_MS)

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `Back that stays down and Forward that joins it open the menu and neither reaches the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, backState)
        idle(100)
        mouse(MotionEvent.ACTION_MOVE, backState or forwardState)
        assertThat(panel()).isEqualTo(Panel.MENU)
        mouse(MotionEvent.ACTION_UP, 0)
        idle(2_000)

        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `a Forward that the host has already is let go when Back joins it`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, forwardState)
        idle(100)
        assertThat(buttonsSent()).containsExactly(press(forward)).inOrder()

        // This is the cost of a normal mouse, whose Forward must not wait for the 250 ms: the host
        // did get the press, and the chord lets it go once, so the host keeps nothing down.
        mouse(MotionEvent.ACTION_MOVE, backState or forwardState)

        assertThat(panel()).isEqualTo(Panel.MENU)
        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
    }

    @Test
    fun `after a chord the release of each button is eaten, and the next press is a new one`() {
        streamingWithNavigationButtons()
        // In picture-in-picture the chrome is hidden and takes nothing, so the stream sees the
        // releases.
        rig.session.onPip(true)
        mouse(MotionEvent.ACTION_DOWN, backState)
        idle(100)
        mouse(MotionEvent.ACTION_MOVE, backState or forwardState)
        assertThat(panel()).isEqualTo(Panel.MENU)
        forgetSent()

        mouse(MotionEvent.ACTION_MOVE, forwardState)
        mouse(MotionEvent.ACTION_UP, 0)
        assertSent(thePointer(), thePointer())

        tap(backState, SETTLE_MS)
        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
    }

    @Test
    fun `the hold of Back does not open the menu again after the chord`() {
        streamingWithNavigationButtons()
        mouse(MotionEvent.ACTION_DOWN, backState)
        idle(100)
        mouse(MotionEvent.ACTION_MOVE, backState or forwardState)
        assertThat(panel()).isEqualTo(Panel.MENU)
        rig.session.closePanel()
        idle(1)

        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    // ----- A pulse -----

    @Test
    fun `a lone Back pulse reaches the host when the 250 ms are over, not before`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(backState)
        idle(WINDOW_MS - 1 - PULSE_MS)
        assertThat(buttonsSent()).isEmpty()
        idle(1)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a lone Forward pulse reaches the host when the 250 ms are over, not before`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(forwardState)
        idle(WINDOW_MS - 1 - PULSE_MS)
        assertThat(buttonsSent()).isEmpty()
        idle(1)

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
    }

    @Test
    fun `a Back that is down for 25 ms is no pulse and goes to the host at once`() {
        streamingWithNavigationButtons()
        forgetSent()

        tap(backState, SETTLE_MS)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
    }

    @Test
    fun `a Back that is down for 24 ms is a pulse`() {
        streamingWithNavigationButtons()
        forgetSent()

        tap(backState, SETTLE_MS - 1)
        assertThat(buttonsSent()).isEmpty()
        idle(WINDOW_MS)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
    }

    @Test
    fun `a Forward that stays down is pressed after 25 ms and let go when it comes up`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, forwardState)
        idle(SETTLE_MS - 1)
        assertThat(buttonsSent()).isEmpty()
        idle(1)
        assertThat(buttonsSent()).containsExactly(press(forward)).inOrder()
        idle(500)
        mouse(MotionEvent.ACTION_UP, 0)

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
    }

    @Test
    fun `a Forward that the event times show was down for 25 ms is no pulse, whatever the timer did`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(MotionEvent.ACTION_DOWN, forwardState)
        // The looper is late: the event of the release is stamped 30 ms after the press, and the
        // task that presses Forward has not run.
        rig.game.onGenericMotion(
            streamView,
            mouseEvent(MotionEvent.ACTION_UP, 10f, 20f, 0, SystemClock.uptimeMillis() + 30),
        )

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
        idle(1_000)
        assertThat(buttonsSent()).hasSize(2)
    }

    @Test
    fun `a second pulse of a button within the 250 ms lets the first one go to the host`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(backState)
        idle(100)
        mouse(MotionEvent.ACTION_DOWN, backState)
        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
        idle(PULSE_MS)
        mouse(MotionEvent.ACTION_UP, 0)
        idle(WINDOW_MS)

        assertThat(buttonsSent())
            .containsExactly(press(back), release(back), press(back), release(back))
            .inOrder()
    }

    @Test
    fun `a pulse that waits is dropped when the window focus is lost`() {
        streamingWithNavigationButtons()
        forgetSent()
        pulse(backState)

        rig.game.onWindowFocusChanged(false)
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `a pulse that waits is dropped when the activity stops`() {
        streamingWithNavigationButtons()
        forgetSent()
        pulse(forwardState)

        rig.stop()
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `a pulse that waits does not send a release of its own when the menu opens another way`() {
        streamingWithNavigationButtons()
        forgetSent()
        pulse(forwardState)

        rig.session.openMenu()
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
    }

    @Test
    fun `a Forward that the host holds is let go once when the menu opens another way`() {
        streamingWithNavigationButtons()
        forgetSent()
        mouse(MotionEvent.ACTION_DOWN, forwardState)
        idle(SETTLE_MS)
        assertThat(buttonsSent()).containsExactly(press(forward)).inOrder()

        rig.session.openMenu()
        idle(1_000)

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
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

    // ----- Without the navigation buttons -----

    // The Back and Forward of the mouse are keys then, the Back standing for the right button.

    private fun keysOpenTheMenu(first: Int, second: Int) {
        streaming()
        forgetSent()

        keyTap(first, PULSE_MS)
        idle(100)
        assertThat(panel()).isEqualTo(Panel.NONE)
        val at = keyDown(second)
        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(PULSE_MS)
        // The key up comes to the open menu, and it does not close it.
        keyUp(second, at)
        idle(1_000)

        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    @Test
    fun `without the navigation buttons Back and then Forward keys open the menu and send nothing`() {
        keysOpenTheMenu(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_FORWARD)
    }

    @Test
    fun `without the navigation buttons a right button that the host has already is let go when Forward joins`() {
        streaming()
        forgetSent()

        // This is the cost of a normal mouse, whose Back must not wait for the 250 ms: the host did
        // get the right button, and the chord lets it go once, so the host keeps nothing down.
        keyDown(KeyEvent.KEYCODE_BACK)
        idle(100)
        assertThat(buttonsSent()).containsExactly(press(right)).inOrder()
        keyDown(KeyEvent.KEYCODE_FORWARD)

        assertThat(panel()).isEqualTo(Panel.MENU)
        assertThat(buttonsSent()).containsExactly(press(right), release(right)).inOrder()
    }

    @Test
    fun `without the navigation buttons Forward and then Back keys open the menu and send nothing`() {
        keysOpenTheMenu(KeyEvent.KEYCODE_FORWARD, KeyEvent.KEYCODE_BACK)
    }

    @Test
    fun `without the navigation buttons a lone Back pulse is a right click, sent when the 250 ms are over`() {
        streaming()
        forgetSent()

        keyTap(KeyEvent.KEYCODE_BACK, PULSE_MS)
        idle(WINDOW_MS - 1 - PULSE_MS)
        assertThat(buttonsSent()).isEmpty()
        idle(1)

        assertThat(buttonsSent()).containsExactly(press(right), release(right)).inOrder()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `without the navigation buttons a Back that stays down is the right button, pressed after 25 ms`() {
        streaming()
        forgetSent()

        val at = keyDown(KeyEvent.KEYCODE_BACK)
        idle(SETTLE_MS - 1)
        assertThat(buttonsSent()).isEmpty()
        idle(1)
        assertThat(buttonsSent()).containsExactly(press(right)).inOrder()
        // The repeats of a key that stays down are the same press.
        keyDown(KeyEvent.KEYCODE_BACK)
        idle(500)
        keyUp(KeyEvent.KEYCODE_BACK, at)

        assertThat(buttonsSent()).containsExactly(press(right), release(right)).inOrder()
        idle(2_000)
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `without the navigation buttons the Forward of a mouse sends nothing`() {
        streaming()
        forgetSent()

        keyTap(KeyEvent.KEYCODE_FORWARD, PULSE_MS)
        keyTap(KeyEvent.KEYCODE_FORWARD, 500)
        idle(2_000)

        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `without the navigation buttons the buttons of the motion events are not the keys`() {
        streaming()
        forgetSent()

        // The events that go with the keys carry the buttons too; only the keys count.
        pulse(backState)
        pulse(forwardState)
        idle(1_000)

        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(buttonsSent()).isEmpty()
    }

    // ----- The event that completes the chord -----

    @Test
    fun `a button pressed in the event that completes the chord is let go when the menu opens`() {
        streamingWithNavigationButtons()
        forgetSent()

        mouse(
            MotionEvent.ACTION_DOWN,
            MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or backState or forwardState,
        )

        assertThat(panel()).isEqualTo(Panel.MENU)
        // The chrome that takes the input lets go of the buttons that the mouse holds, so the event
        // has noted them before the menu opens.
        assertThat(buttonsSent().take(2)).containsExactly(press(left), press(right)).inOrder()
        assertThat(buttonsSent().drop(2)).containsExactly(release(left), release(right))
    }

    @Test
    fun `Forward pulsed, then Back and Forward together within the window, open the menu`() {
        streamingWithNavigationButtons()
        forgetSent()

        pulse(forwardState)
        idle(100)
        mouse(MotionEvent.ACTION_DOWN, backState or forwardState)

        assertThat(panel()).isEqualTo(Panel.MENU)
        idle(1_000)
        assertThat(buttonsSent()).isEmpty()
        assertThat(panel()).isEqualTo(Panel.MENU)
    }

    // ----- How long a side button was held -----

    /**
     * A mouse event with [samples] earlier samples batched on it: its down time is that much older.
     */
    private fun withHistory(action: Int, buttons: Int, eventTime: Long, samples: Int) =
        motionEventOf(InputDevice.SOURCE_MOUSE, action) {
            pointer(10f, 20f)
            buttonState = buttons
            this.eventTime = eventTime
            repeat(samples) { earlier(10f to 20f) }
        }

    private fun heldFor30MsByTheEventTimes(state: Int) {
        streamingWithNavigationButtons()
        forgetSent()
        val at = SystemClock.uptimeMillis()
        rig.game.onGenericMotion(
            streamView,
            mouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, state, at),
        )

        // The release batches 10 samples: its down time is 20 ms after the press, which is not how
        // long the button was held.
        rig.game.onGenericMotion(streamView, withHistory(MotionEvent.ACTION_UP, 0, at + 30, 10))
    }

    @Test
    fun `how long Back was held is read from the event times, not from the down time of the events`() {
        heldFor30MsByTheEventTimes(backState)

        assertThat(buttonsSent()).containsExactly(press(back), release(back)).inOrder()
    }

    @Test
    fun `how long Forward was held is read from the event times, not from the down time of the events`() {
        heldFor30MsByTheEventTimes(forwardState)

        assertThat(buttonsSent()).containsExactly(press(forward), release(forward)).inOrder()
    }

    private companion object {
        /** How long a side button of the Logi M650 stays down. */
        const val PULSE_MS = 7L

        /** The time that Back and Forward have to come together. */
        const val WINDOW_MS = 250L
    }
}
