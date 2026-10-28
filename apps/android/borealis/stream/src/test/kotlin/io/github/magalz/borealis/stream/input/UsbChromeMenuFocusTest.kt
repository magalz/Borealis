package io.github.magalz.borealis.stream.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.host.StreamChromeHost
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

// The rows of the menu in the stream chrome of these tests, from the first to the last. The chrome
// detects its own form factor, and under Robolectric that is a phone's: it shows Touch.
private val MENU_ROWS =
    listOf(
        "Back to stream",
        "Keyboard",
        "Send keys",
        "Touch",
        "Overlay",
        "Refresh the picture",
        "Session info",
        "Display for next launch",
        "Disconnect",
        "Quit Desktop",
    )

// The chips of Send keys, in reading order.
private val CHIPS =
    listOf(
        "Alt+Tab",
        "Win+Tab",
        "Win",
        "Ctrl+Shift+Esc",
        "Alt+F4",
        "Win+D",
        "Win+G",
        "Esc",
        "Win+Alt+B",
    )

private val LATERAL_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

/**
 * The in-game menu has no lateral movement (owner decision of 2026-10-28, issue #98): Left and
 * Right do nothing and the chrome takes them, so they are never left to the game or the PC, from
 * the D-pad and the left stick of a pad on the USB driver (through `UsbChromeInput`), from a pad
 * Android knows and from a keyboard. Up and Down reach everything.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class UsbChromeMenuFocusTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()
    private val session = aSession(actions)
    private lateinit var host: StreamChromeHost

    /** What the chrome answered to each key that the pad sent: its key code, action and answer. */
    private val answers = mutableListOf<Triple<Int, Int, Boolean>>()

    private val pad =
        UsbChromeInput(Handler(Looper.getMainLooper())) {
            if (session.chromeMode == ChromeMode.MODAL) {
                ChromeKeySink { key ->
                    host.dispatchUsbPadKey(key).also {
                        answers += Triple(key.keyCode, key.action, it)
                    }
                }
            } else {
                null
            }
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
        session.openMenu()
        idle()
    }

    private fun idle(millis: Long = 200) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        composeRule.waitForIdle()
    }

    /** The stick pushed to [x], [y] (down is positive) and let go, as a tap on a direction. */
    private fun tapStick(x: Float = 0f, y: Float = 0f, times: Int = 1) =
        repeat(times) {
            pad.report(0, x, y)
            idle(20)
            pad.report(0, 0f, 0f)
            idle(20)
        }

    private fun pressA() {
        pad.report(GamepadButtons.A, 0f, 0f)
        idle(20)
        pad.report(0, 0f, 0f)
        idle(20)
    }

    /** What the focused row or chip says first, or null unless exactly one has the focus. */
    private fun focusedTitle() =
        composeRule
            .onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .singleOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
            ?.text

    /** The answers to the lateral keys, down and up, that the pad sent. */
    private fun lateralAnswers() = answers.filter { it.first in LATERAL_KEYS }.map { it.third }

    @Test
    fun `the stick pushed right and left does nothing on every row and the chrome takes every key`() {
        val moved = mutableListOf<String>()

        for (title in MENU_ROWS) {
            composeRule.onNodeWithText(title).performScrollTo().requestFocus()
            tapStick(x = 1f)
            if (focusedTitle() != title) moved += "$title: Right"
            tapStick(x = -1f)
            if (focusedTitle() != title) moved += "$title: Left"
        }

        assertThat(moved).isEmpty()
        // Down and up of 10 rows, each way: none of them is left to the game or the PC.
        assertThat(lateralAnswers()).hasSize(MENU_ROWS.size * 4)
        assertThat(lateralAnswers()).doesNotContain(false)
    }

    @Test
    fun `a stick held to the right on an open Send keys keeps the focus and the chrome takes every repeat`() {
        // The window starts in touch mode: the first key of the pad leaves it.
        tapStick(y = 1f, times = 2)
        composeRule.onNodeWithText("Send keys").assertIsFocused()
        pressA()
        composeRule.onNodeWithText("Alt+Tab").assertExists()

        for (title in listOf("Send keys", CHIPS.first(), CHIPS.last())) {
            composeRule.onNodeWithText(title).performScrollTo().requestFocus()
            answers.clear()
            pad.report(0, 1f, 0f)
            idle(UsbChromeInput.REPEAT_DELAY_MS + 5 * UsbChromeInput.REPEAT_INTERVAL_MS)
            pad.report(0, 0f, 0f)
            idle(20)

            assertThat(focusedTitle()).isEqualTo(title)
            // The press, its repeats and the release.
            assertThat(lateralAnswers().size).isAtLeast(7)
            assertThat(lateralAnswers()).doesNotContain(false)
        }
    }

    @Test
    fun `the D-pad keys of a pad Android knows and of a keyboard are taken and move nothing`() {
        val sources =
            listOf(
                InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK,
                InputDevice.SOURCE_KEYBOARD,
            )
        tapStick(y = 1f, times = 2)
        composeRule.onNodeWithText("Send keys").assertIsFocused()
        val taken = mutableListOf<Boolean>()

        for (source in sources) {
            for (keyCode in LATERAL_KEYS) {
                for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    composeRule.runOnUiThread {
                        val time = SystemClock.uptimeMillis()
                        val key = KeyEvent(time, time, action, keyCode, 0, 0, 7, 0, 0, source)
                        taken += host.dispatchKeyEvent(key)
                    }
                    idle(20)
                }
            }
        }

        assertThat(taken).containsExactly(true, true, true, true, true, true, true, true)
        assertThat(focusedTitle()).isEqualTo("Send keys")
    }

    // The round finding D4: Down several times, OK, Right, OK, and Quit was never reached.
    @Test
    fun `the stick up and down and A alone reach Quit through the open chips of Send keys`() {
        tapStick(y = 1f, times = 2)
        composeRule.onNodeWithText("Send keys").assertIsFocused()
        pressA()
        composeRule.onNodeWithText("Alt+Tab").assertIsDisplayed()
        val rest = CHIPS + MENU_ROWS.drop(MENU_ROWS.indexOf("Send keys") + 1)

        val visited = List(rest.size) { tapStick(y = 1f).let { focusedTitle() } }

        assertThat(visited).isEqualTo(rest)
        composeRule.onNodeWithText("Quit Desktop").assertIsFocused()
        pressA()
        composeRule.onNodeWithText("Quit Desktop?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        assertThat(actions.calls.filter { it != "menuHintShown" }).isEmpty()
    }

    @Test
    fun `the stick down, A, the stick right, A and the stick down reach Quit and A asks to quit`() {
        tapStick(y = 1f, times = 2)
        composeRule.onNodeWithText("Send keys").assertIsFocused()
        pressA()
        composeRule.onNodeWithText("Alt+Tab").assertIsDisplayed()
        tapStick(x = 1f)
        composeRule.onNodeWithText("Send keys").assertIsFocused()
        pressA()
        composeRule.onNodeWithText("Alt+Tab").assertDoesNotExist()
        // Touch, Overlay, Refresh the picture, Session info, Display, Disconnect, Quit.
        tapStick(y = 1f, times = 7)
        composeRule.onNodeWithText("Quit Desktop").assertIsFocused()
        pressA()

        composeRule.onNodeWithText("Quit Desktop?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        assertThat(actions.calls.filter { it != "menuHintShown" }).isEmpty()
    }
}
