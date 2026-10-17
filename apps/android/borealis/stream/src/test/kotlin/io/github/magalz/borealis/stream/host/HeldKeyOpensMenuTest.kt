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
 * A key that was down when the menu opened (a remote's OK held for the menu) acts in the menu only
 * once it has been let go and pressed again: its repeats and its release never reach the focused
 * row, which would otherwise be clicked as the key comes up.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class HeldKeyOpensMenuTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()
    private val session: StreamSession = aSession(actions)
    private lateinit var host: StreamChromeHost

    private val panel: Panel
        get() = (session.state.value.phase as StreamPhase.Streaming).panel

    @Before
    fun setUp() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            // A remote takes the window out of touch mode, where the chrome can take the focus.
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

    /** What the chrome answered to one key event of the press that went down at [downTime]. */
    private fun key(
        action: Int,
        keyCode: Int,
        downTime: Long,
        repeat: Int = 0,
        flags: Int = 0,
    ): Boolean {
        var taken = false
        composeRule.runOnUiThread {
            val event =
                KeyEvent(
                    downTime,
                    SystemClock.uptimeMillis(),
                    action,
                    keyCode,
                    repeat,
                    0,
                    REMOTE,
                    0,
                    flags,
                    InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD,
                )
            taken = host.dispatchKeyEvent(event)
        }
        return taken
    }

    /** A key goes down while the stream has the keys, and is still down when the menu opens. */
    private fun holdThroughOpening(keyCode: Int): Long {
        val downTime = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, keyCode, downTime)
        idle(100)
        session.openMenu()
        idle()
        composeRule.onNodeWithText("Back to stream").assertIsFocused()
        return downTime
    }

    private fun repeatsThenRelease(keyCode: Int, downTime: Long): List<Boolean> {
        val answers = mutableListOf<Boolean>()
        for (repeat in 1..3) {
            answers +=
                key(
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    downTime,
                    repeat,
                    if (repeat > 1) KeyEvent.FLAG_LONG_PRESS else 0,
                )
            idle(30)
        }
        answers += key(KeyEvent.ACTION_UP, keyCode, downTime)
        idle(30)
        return answers
    }

    private fun tap(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, keyCode, downTime)
        idle(20)
        key(KeyEvent.ACTION_UP, keyCode, downTime)
        idle(20)
    }

    @Test
    fun `the release of an OK held to open the menu does not click the focused row`() {
        val downTime = holdThroughOpening(KeyEvent.KEYCODE_DPAD_CENTER)

        repeatsThenRelease(KeyEvent.KEYCODE_DPAD_CENTER, downTime)

        assertThat(panel).isEqualTo(Panel.MENU)
        composeRule.onNodeWithText("Back to stream").assertIsFocused()
        assertThat(actions.calls.filter { it != "menuHintShown" }).isEmpty()
    }

    @Test
    fun `the repeats and the release of a held OK are consumed`() {
        val downTime = holdThroughOpening(KeyEvent.KEYCODE_DPAD_CENTER)

        assertThat(repeatsThenRelease(KeyEvent.KEYCODE_DPAD_CENTER, downTime)).doesNotContain(false)
    }

    @Test
    fun `a held Enter does not click the focused row either`() {
        val downTime = holdThroughOpening(KeyEvent.KEYCODE_ENTER)

        repeatsThenRelease(KeyEvent.KEYCODE_ENTER, downTime)

        assertThat(panel).isEqualTo(Panel.MENU)
    }

    @Test
    fun `the keys that can open the menu are held through it as well`() {
        for (keyCode in
            listOf(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_A,
            )) {
            val downTime = holdThroughOpening(keyCode)

            assertThat(repeatsThenRelease(keyCode, downTime)).doesNotContain(false)

            assertThat(panel).isEqualTo(Panel.MENU)
            session.closePanel()
            idle()
        }
    }

    @Test
    fun `a short OK press after the held one is let go clicks the focused row`() {
        val downTime = holdThroughOpening(KeyEvent.KEYCODE_DPAD_CENTER)
        repeatsThenRelease(KeyEvent.KEYCODE_DPAD_CENTER, downTime)
        assertThat(panel).isEqualTo(Panel.MENU)

        tap(KeyEvent.KEYCODE_DPAD_CENTER)

        // The focus starts on Back to stream.
        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a different key works while OK is still held`() {
        val downTime = holdThroughOpening(KeyEvent.KEYCODE_DPAD_CENTER)

        tap(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, downTime, repeat = 5)
        idle(30)

        composeRule.onNodeWithText("Keyboard").assertIsFocused()
        assertThat(panel).isEqualTo(Panel.MENU)
    }

    @Test
    fun `keys pressed after the menu opened work at once, held or not`() {
        session.openMenu()
        idle()

        val downTime = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, downTime)
        idle(30)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, downTime, repeat = 1)
        idle(30)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, downTime)
        idle(30)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a stream key that went down and up before the menu opened leaves no trace`() {
        val downTime = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, downTime)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, downTime)
        idle(100)
        session.openMenu()
        idle()

        tap(KeyEvent.KEYCODE_DPAD_CENTER)

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a system key held since before the menu opened is not consumed`() {
        for (keyCode in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            val downTime = holdThroughOpening(keyCode)

            assertThat(repeatsThenRelease(keyCode, downTime)).doesNotContain(true)

            session.closePanel()
            idle()
        }
    }

    @Test
    fun `after the menu is closed and opened again only a key held across the reopening is spent`() {
        session.openMenu()
        idle()
        session.closePanel()
        idle()
        // Pressed and let go while the menu was closed: nothing of it is left for the next menu.
        val pressed = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, pressed)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, pressed)
        idle(100)
        val held = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, held)
        idle(100)
        session.openMenu()
        idle()

        repeatsThenRelease(KeyEvent.KEYCODE_DPAD_CENTER, held)
        assertThat(panel).isEqualTo(Panel.MENU)

        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a quick Back that opens the menu is followed by a fresh Back that closes it`() {
        val downTime = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, downTime)
        val taken = key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, downTime)
        // The activity turns a release that nobody took into this call.
        if (!taken) pressBack()
        idle()
        assertThat(panel).isEqualTo(Panel.MENU)

        val fresh = SystemClock.uptimeMillis()
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, fresh)
        if (!key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, fresh)) pressBack()
        idle()

        assertThat(panel).isEqualTo(Panel.NONE)
    }

    private fun pressBack() = composeRule.runOnUiThread {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    private companion object {
        const val REMOTE = 11
    }
}
