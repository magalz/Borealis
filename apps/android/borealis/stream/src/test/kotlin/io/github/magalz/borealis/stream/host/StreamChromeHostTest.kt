package io.github.magalz.borealis.stream.host

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Stands in for the Compose content of the chrome: it takes every touch it is given and, like a
 * button, the focus.
 */
private class TouchProbe(activity: ComponentActivity) : View(activity) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    val touches = mutableListOf<Int>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touches += event.actionMasked
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class StreamChromeHostTest {
    private lateinit var activity: ComponentActivity
    private lateinit var stream: View
    private lateinit var probe: TouchProbe
    private lateinit var host: StreamChromeHost
    private val session: StreamSession = aSession(dockedKeyboard = true)
    private var captured = 0
    private var released = 0

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        stream =
            View(activity).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
        activity.addContentView(stream, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        stream.requestFocus()
        host = StreamChromeHost(activity, session, stream, { captured++ }, { released++ })
        probe = TouchProbe(activity)
        host.addView(probe, MATCH_PARENT, MATCH_PARENT)
        activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

    private fun stream() {
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        idle()
    }

    private fun touch(action: Int, x: Float = 5f, y: Float = 5f): MotionEvent =
        MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0)

    private fun mouse(action: Int): MotionEvent =
        touch(action).also { it.source = InputDevice.SOURCE_MOUSE }

    @Test
    fun `the chrome takes the focus while it is modal`() {
        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
        assertThat(host.hasFocus()).isTrue()
        assertThat(stream.hasFocus()).isFalse()
    }

    @Test
    fun `a modal chrome consumes every touch and pointer motion, so none reaches the stream`() {
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))).isTrue()
        assertThat(probe.touches).containsExactly(MotionEvent.ACTION_DOWN)
        assertThat(host.dispatchGenericMotionEvent(mouse(MotionEvent.ACTION_SCROLL))).isTrue()
        assertThat(host.dispatchGenericMotionEvent(mouse(MotionEvent.ACTION_HOVER_MOVE))).isTrue()
    }

    @Test
    fun `a modal chrome leaves joystick and hat motion unconsumed, so Android makes a D-pad of it`() {
        val hat = touch(MotionEvent.ACTION_MOVE).also { it.source = InputDevice.SOURCE_JOYSTICK }
        val gamepad =
            touch(MotionEvent.ACTION_MOVE).also {
                it.source = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD
            }

        assertThat(host.dispatchGenericMotionEvent(hat)).isFalse()
        assertThat(host.dispatchGenericMotionEvent(gamepad)).isFalse()
    }

    @Test
    fun `an idle chrome does not take the focus and ignores touch and motion`() {
        stream()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.IDLE)
        assertThat(host.hasFocus()).isFalse()
        assertThat(stream.hasFocus()).isTrue()
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))).isFalse()
        assertThat(host.dispatchGenericMotionEvent(mouse(MotionEvent.ACTION_HOVER_MOVE))).isFalse()
        assertThat(probe.touches).isEmpty()
    }

    @Test
    fun `the focus goes back to the stream when the chrome stops being modal`() {
        stream()
        session.openMenu()
        idle()
        assertThat(host.hasFocus()).isTrue()

        session.closePanel()
        idle()

        assertThat(host.hasFocus()).isFalse()
        assertThat(stream.hasFocus()).isTrue()
    }

    @Test
    fun `the capture callbacks follow the modal mode`() {
        // The session starts modal; the first frame leaves it.
        assertThat(captured).isEqualTo(1)
        assertThat(released).isEqualTo(0)

        stream()
        assertThat(released).isEqualTo(1)

        session.openMenu()
        idle()
        assertThat(captured).isEqualTo(2)

        session.closePanel()
        idle()
        assertThat(released).isEqualTo(2)
    }

    @Test
    fun `an idle chrome takes only a touch that starts on the menu handle, and all of its gesture`() {
        stream()
        host.setMenuHandleBounds(Rect(0f, 0f, 20f, 20f))

        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 50f, 50f))).isFalse()
        assertThat(probe.touches).isEmpty()

        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 10f, 10f))).isTrue()
        // The finger left the handle; the gesture still belongs to the chrome.
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, 80f, 80f))).isTrue()
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 80f, 80f))).isTrue()
        assertThat(probe.touches)
            .containsExactly(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
            )
            .inOrder()

        // The next gesture starts outside the handle again.
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 50f, 50f))).isFalse()
    }

    @Test
    fun `a docked chrome gets touches but not the focus`() {
        stream()
        session.openKeyboard()
        idle()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.DOCKED)
        assertThat(host.hasFocus()).isFalse()
        assertThat(stream.hasFocus()).isTrue()
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))).isTrue()
        assertThat(probe.touches).containsExactly(MotionEvent.ACTION_DOWN)
    }

    @Test
    fun `the chrome is hidden in picture-in-picture and takes nothing`() {
        stream()
        session.openMenu()
        idle()

        activity.onPictureInPictureModeChanged(true)
        idle()

        assertThat(session.state.value.inPip).isTrue()
        assertThat(session.chromeMode).isEqualTo(ChromeMode.HIDDEN)
        assertThat(host.hasFocus()).isFalse()
        assertThat(host.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN))).isFalse()

        activity.onPictureInPictureModeChanged(false)
        idle()

        assertThat(session.chromeMode).isEqualTo(ChromeMode.MODAL)
        assertThat(host.hasFocus()).isTrue()
    }
}
