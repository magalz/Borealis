package io.github.magalz.borealis.stream.host

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.GamepadButtons
import io.github.magalz.borealis.stream.input.UsbChromeInput
import io.github.magalz.borealis.stream.input.UsbChromeKeys
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import java.time.Duration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A pad on the USB driver drives the menu of a phone, whose window starts in touch mode. Android
 * has no input device for such a pad, so its keys are made up by the gamepad handler and given to
 * the chrome without passing the window's input pipeline, which is what takes the window out of
 * touch mode when a key comes from a pad Android knows. In touch mode Compose does not give the
 * focus to a clickable row, so the keys of the pad would move and click nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class UsbPadTouchModeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()
    private val session: StreamSession = aSession(actions)
    private lateinit var host: StreamChromeHost

    /**
     * The pad as the gamepad handler wires it: its keys reach the chrome while the chrome is modal.
     */
    private val pad =
        UsbChromeInput(Handler(Looper.getMainLooper())) {
            if (session.chromeMode == ChromeMode.MODAL) ChromeKeySink(host::dispatchUsbPadKey)
            else null
        }

    private val panel: Panel
        get() = (session.state.value.phase as StreamPhase.Streaming).panel

    private val inTouchMode: Boolean
        get() {
            var touchMode = false
            composeRule.runOnUiThread { touchMode = host.isInTouchMode }
            return touchMode
        }

    @Before
    fun setUp() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            val stream = View(activity).apply { isFocusable = true }
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            host = StreamChromeHost(activity, session, stream, {}, {})
            activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        idle()
        // The window of a phone is in touch mode until a key of a pad Android knows leaves it.
        assertThat(inTouchMode).isTrue()
    }

    private fun idle(millis: Long = 200) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        composeRule.waitForIdle()
    }

    private fun openMenu() {
        session.openMenu()
        idle()
    }

    /** The pad presses [buttons] and lets them go, with the left stick at [stickY]. */
    private fun press(buttons: Int, stickY: Float = 0f) {
        pad.report(buttons, 0f, stickY)
        idle(20)
        pad.report(0, 0f, 0f)
        idle(20)
    }

    @Test
    fun `D-pad Down moves the focus in the menu`() {
        openMenu()

        press(GamepadButtons.DPAD_DOWN)

        // The menu starts on Back to stream, as on a TV, so one step down is Keyboard.
        composeRule.onNodeWithText("Keyboard").assertIsFocused()
    }

    @Test
    fun `the left stick pushed down moves the focus in the menu`() {
        openMenu()

        press(buttons = 0, stickY = 1f)

        composeRule.onNodeWithText("Keyboard").assertIsFocused()
    }

    @Test
    fun `A runs the focused row`() {
        openMenu()
        press(GamepadButtons.DPAD_DOWN)

        press(GamepadButtons.A)

        assertThat(panel).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `B closes the menu`() {
        openMenu()

        press(GamepadButtons.B)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a tap still acts after the pad has navigated`() {
        openMenu()
        press(GamepadButtons.DPAD_DOWN)
        press(GamepadButtons.DPAD_DOWN)
        composeRule.onNodeWithText("Send keys").assertIsFocused()

        composeRule.onNodeWithText("Keyboard").performTouchInput { click() }

        assertThat(panel).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `an idle chrome leaves a key of the pad to the stream and the window in touch mode`() {
        assertThat(panel).isEqualTo(Panel.NONE)
        val key =
            UsbChromeKeys.events(0, GamepadButtons.DPAD_DOWN, SystemClock.uptimeMillis()).single()

        var taken = true
        composeRule.runOnUiThread { taken = host.dispatchUsbPadKey(key) }

        assertThat(taken).isFalse()
        assertThat(inTouchMode).isTrue()
    }

    /**
     * On a device the window's pipeline has already dealt with the touch mode before such a key
     * reaches the chrome, so the chrome leaves it alone.
     */
    @Test
    fun `a key of a pad Android knows does not take the chrome out of touch mode itself`() {
        openMenu()

        composeRule.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            val source = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
            host.dispatchKeyEvent(
                KeyEvent(
                    time,
                    time,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    0,
                    0,
                    7,
                    0,
                    0,
                    source,
                )
            )
        }

        assertThat(inTouchMode).isTrue()
    }
}
