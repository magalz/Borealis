package io.github.magalz.borealis.stream.host

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.GamepadButtons
import io.github.magalz.borealis.stream.input.UsbChromeInput
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
 * The keyboard of a phone is the one a pad can use. The session is created before any pad is there,
 * the way a stream starts, and the pad turns up later: the keyboard is chosen when it opens. A pad
 * on the app's USB driver is no Android input device, so it reaches the chrome through the keys the
 * gamepad handler makes up for it, and only while the chrome is modal.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w780dp-h360dp-land-finger-nonav")
class PadKeyboardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()

    /** Whether the app's USB driver holds a pad; the keyboard asks when it opens. */
    private var usbPadHeld = false
    private lateinit var session: StreamSession
    private lateinit var host: StreamChromeHost

    /**
     * The USB pad as the gamepad handler wires it: its keys reach the chrome while the chrome is
     * modal.
     */
    private val pad =
        UsbChromeInput(Handler(Looper.getMainLooper())) {
            if (session.chromeMode == ChromeMode.MODAL) ChromeKeySink(host::dispatchUsbPadKey)
            else null
        }

    /** What the session forwarded, without the note that the first-run hint was shown. */
    private val calls: List<String>
        get() = actions.calls.filter { it != "menuHintShown" }

    private val panel: Panel
        get() = (session.state.value.phase as StreamPhase.Streaming).panel

    @Before
    fun setUp() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            session =
                aSession(
                    actions,
                    dockKeyboard = { StreamChromeHost.dockedKeyboard(activity, usbPadHeld) },
                )
            val stream = View(activity).apply { isFocusable = true }
            activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            host = StreamChromeHost(activity, session, stream, {}, {})
            activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        idle()
    }

    private fun idle(millis: Long = 200) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        composeRule.waitForIdle()
    }

    private fun openKeyboard() {
        session.openKeyboard()
        idle()
    }

    /** The pad presses [buttons] and lets them go. */
    private fun press(buttons: Int) {
        pad.report(buttons, 0f, 0f)
        idle(20)
        pad.report(0, 0f, 0f)
        idle(20)
    }

    private fun connectGamepad() {
        val sources =
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        connect(composeRule.activity, inputDevice(id = 3, sources = sources))
    }

    @Test
    fun `with no pad the keyboard opens docked`() {
        openKeyboard()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.DOCKED)
    }

    @Test
    fun `a pad on the USB driver gets the covering keyboard`() {
        usbPadHeld = true

        openKeyboard()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a pad that Android knows, connected after the stream started, gets the covering keyboard`() {
        connectGamepad()

        openKeyboard()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `D-pad Down on the USB pad moves the focus in the keyboard`() {
        usbPadHeld = true
        openKeyboard()

        press(GamepadButtons.DPAD_DOWN)

        composeRule.onNodeWithTag("key:Q").assertIsFocused()
    }

    @Test
    fun `A on the USB pad types the focused key`() {
        usbPadHeld = true
        openKeyboard()
        press(GamepadButtons.DPAD_DOWN)

        press(GamepadButtons.A)

        assertThat(calls.map { it.substringBefore(' ') }).containsExactly("send")
    }

    @Test
    fun `B on the USB pad closes the keyboard`() {
        usbPadHeld = true
        openKeyboard()

        press(GamepadButtons.B)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a keyboard that opened docked stays docked when a pad turns up`() {
        openKeyboard()

        usbPadHeld = true
        connectGamepad()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.DOCKED)
    }

    @Test
    fun `a keyboard that opened covering stays covering when the pad goes away`() {
        usbPadHeld = true
        openKeyboard()

        usbPadHeld = false

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
    }
}
