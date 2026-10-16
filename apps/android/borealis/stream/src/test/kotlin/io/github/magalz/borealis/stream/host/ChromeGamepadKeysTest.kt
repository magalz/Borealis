package io.github.magalz.borealis.stream.host

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.input.MenuChordDetector
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.TV
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
 * The gamepad keys of the modal chrome, with its real screens. A system pad and a USB-driver pad
 * (whose keys the gamepad handler makes up, with device 0) act the same, and no key is left to the
 * system fallbacks that turn Start into OK.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
abstract class ChromeGamepadKeysBase(private val deviceId: Int, private val sources: Int) {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()
    private val session: StreamSession = aSession(actions)
    private lateinit var host: StreamChromeHost

    /** What the session forwarded, without the note that the first-run hint was shown. */
    private val calls: List<String>
        get() = actions.calls.filter { it != "menuHintShown" }

    private val panel: Panel
        get() = (session.state.value.phase as StreamPhase.Streaming).panel

    @Before
    fun setUp() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            // A gamepad takes the window out of touch mode, where the chrome can take the focus.
            val root = activity.window.decorView.parent
            root.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.java).run {
                isAccessible = true
                invoke(root, false)
            }
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

    private fun openMenu() {
        session.openMenu()
        idle()
    }

    private fun openKeyboard() {
        session.openKeyboard()
        idle()
    }

    /** What the chrome answered to one key event. */
    private fun key(action: Int, keyCode: Int, repeat: Int = 0): Boolean {
        var taken = false
        composeRule.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            val event = KeyEvent(time, time, action, keyCode, repeat, 0, deviceId, 0, 0, sources)
            taken = host.dispatchKeyEvent(event)
        }
        return taken
    }

    private fun down(keyCode: Int, repeat: Int = 0) = key(KeyEvent.ACTION_DOWN, keyCode, repeat)

    private fun up(keyCode: Int) = key(KeyEvent.ACTION_UP, keyCode)

    /** Presses and releases [keyCode]; true when the chrome took both events. */
    private fun tap(keyCode: Int): Boolean {
        val down = down(keyCode)
        idle(20)
        val up = up(keyCode)
        idle(20)
        return down && up
    }

    /** Walks the focus down the menu to Quit, its last row. */
    private fun focusQuit() = repeat(12) { tap(KeyEvent.KEYCODE_DPAD_DOWN) }

    private fun holdChord() {
        down(KeyEvent.KEYCODE_BUTTON_SELECT)
        down(KeyEvent.KEYCODE_BUTTON_START)
    }

    @Test
    fun `OK clicks the focused row and the D-pad moves the focus`() {
        openMenu()
        composeRule.onNodeWithText("Back to stream").assertIsFocused()

        tap(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithText("Keyboard").assertIsFocused()
        tap(KeyEvent.KEYCODE_DPAD_CENTER)

        assertThat(panel).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `A clicks the focused row`() {
        openMenu()

        // The focus starts on Back to stream.
        tap(KeyEvent.KEYCODE_BUTTON_A)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `B closes the menu`() {
        openMenu()

        tap(KeyEvent.KEYCODE_BUTTON_B)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `Start, Select, X, the stick buttons and Mode click nothing and are consumed`() {
        val unused =
            listOf(
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_BUTTON_MODE,
            )
        openMenu()
        for (keyCode in unused) {
            assertThat(tap(keyCode)).isTrue()

            assertThat(panel).isEqualTo(Panel.MENU)
            assertThat(calls).isEmpty()
        }
    }

    @Test
    fun `with the focus on Quit, Start does not ask to quit`() {
        openMenu()
        focusQuit()
        composeRule.onNodeWithText("Quit Desktop").assertIsFocused()

        tap(KeyEvent.KEYCODE_BUTTON_START)

        // The question about quitting would have taken the focus.
        composeRule.onNodeWithText("Quit Desktop").assertIsFocused()
        assertThat(calls).isEmpty()
    }

    @Test
    fun `with the focus on Quit, A asks and B takes the question back`() {
        openMenu()
        focusQuit()

        tap(KeyEvent.KEYCODE_BUTTON_A)
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        tap(KeyEvent.KEYCODE_BUTTON_B)

        composeRule.onNodeWithText("Quit Desktop").assertIsFocused()
        assertThat(panel).isEqualTo(Panel.MENU)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `Y does nothing in the menu`() {
        openMenu()

        assertThat(tap(KeyEvent.KEYCODE_BUTTON_Y)).isTrue()

        assertThat(panel).isEqualTo(Panel.MENU)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `in the keyboard A types the focused key, Y deletes and B closes`() {
        openKeyboard()
        assertThat(panel).isEqualTo(Panel.KEYBOARD)

        tap(KeyEvent.KEYCODE_BUTTON_A)
        assertThat(calls.single()).startsWith("send ")
        actions.calls.clear()

        tap(KeyEvent.KEYCODE_BUTTON_Y)
        assertThat(calls).containsExactly("send 8")

        tap(KeyEvent.KEYCODE_BUTTON_B)
        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `holding Select and Start for one second closes the menu`() {
        openMenu()

        holdChord()
        idle(MenuChordDetector.HOLD_MS - 100)
        assertThat(panel).isEqualTo(Panel.MENU)
        idle(200)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `holding Select and Start for one second closes the keyboard`() {
        openKeyboard()

        holdChord()
        idle(MenuChordDetector.HOLD_MS + 100)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a quick Select and Start does nothing`() {
        openMenu()

        holdChord()
        idle(300)
        up(KeyEvent.KEYCODE_BUTTON_START)
        up(KeyEvent.KEYCODE_BUTTON_SELECT)
        idle(MenuChordDetector.HOLD_MS * 2)

        assertThat(panel).isEqualTo(Panel.MENU)
    }

    @Test
    fun `Select and Start held from before the menu opened do not close it`() {
        openMenu()

        // The buttons went down before the chrome took the input; the window only repeats them.
        down(KeyEvent.KEYCODE_BUTTON_SELECT, repeat = 4)
        down(KeyEvent.KEYCODE_BUTTON_START, repeat = 4)
        idle(MenuChordDetector.HOLD_MS * 2)

        assertThat(panel).isEqualTo(Panel.MENU)
    }

    @Test
    fun `an idle chrome leaves the gamepad keys to the stream`() {
        assertThat(panel).isEqualTo(Panel.NONE)

        assertThat(down(KeyEvent.KEYCODE_BUTTON_START)).isFalse()
        assertThat(up(KeyEvent.KEYCODE_BUTTON_START)).isFalse()
        assertThat(down(KeyEvent.KEYCODE_BUTTON_A)).isFalse()
        assertThat(calls).isEmpty()
    }
}

/** A pad Android knows. */
class ChromeGamepadKeysSystemPadTest :
    ChromeGamepadKeysBase(7, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)

/** A pad on the USB driver: the gamepad handler makes its keys up, with device 0. */
class ChromeGamepadKeysUsbPadTest : ChromeGamepadKeysBase(0, InputDevice.SOURCE_GAMEPAD)
