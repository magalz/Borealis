package io.github.magalz.borealis.stream.input

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
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

// The rows that set an option: Right goes into their segments, to the selected one, which is the
// first, and one Left brings the focus back to the row.
private val OPTION_ROWS = setOf("Touch", "Overlay")

/**
 * A pad on the USB driver drives the in-game menu with its left stick, through `UsbChromeInput`,
 * which turns the stick into D-pad keys and repeats them while it is held. Left and Right never
 * take the focus off the menu (Sprint 12 round finding D4).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class UsbChromeMenuFocusTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()
    private val session = aSession(actions)
    private lateinit var host: StreamChromeHost

    private val pad =
        UsbChromeInput(Handler(Looper.getMainLooper())) {
            if (session.chromeMode == ChromeMode.MODAL) ChromeKeySink(host::dispatchUsbPadKey)
            else null
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

    private fun holdsFocus(title: String) =
        composeRule
            .onNodeWithText(title)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused) == true

    @Test
    fun `the stick pushed right and left keeps a focus on every row of the menu`() {
        val lost = mutableListOf<String>()

        for (title in MENU_ROWS) {
            composeRule.onNodeWithText(title).performScrollTo().requestFocus()
            tapStick(x = 1f)
            // Right goes into the segments of an option row, and Left comes back to the row.
            if (holdsFocus(title) == (title in OPTION_ROWS)) lost += "$title: Right"
            tapStick(x = -1f)
            if (!holdsFocus(title)) lost += "$title: Left"
        }

        assertThat(lost).isEmpty()
    }

    @Test
    fun `a stick held to the right on Send keys keeps the focus there while it repeats`() {
        tapStick(y = 1f, times = 2)
        composeRule.onNodeWithText("Send keys").assertIsFocused()

        pad.report(0, 1f, 0f)
        idle(UsbChromeInput.REPEAT_DELAY_MS + 5 * UsbChromeInput.REPEAT_INTERVAL_MS)

        composeRule.onNodeWithText("Send keys").assertIsFocused()
        pad.report(0, 0f, 0f)
        idle(20)
    }

    // The round finding D4: Down several times, OK, Right, OK, and Quit was never reached.
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
