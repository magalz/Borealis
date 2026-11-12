package io.github.magalz.borealis.stream.host

import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StreamBackTest {
    private lateinit var activity: ComponentActivity
    private val actions = RecordingActions()
    private val session: StreamSession = aSession(actions)

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = StreamChromeHost(activity, session, View(activity), {}, {})
        activity.addContentView(host, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
    }

    private fun back() {
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
    }

    /**
     * Lets the main thread run in steps until [done], for a call that a recomposition makes, which
     * lands a frame or two after the state changed and not at a fixed time. The wait ends before
     * the menu hint's own six seconds, so a call that only the timer made would not count.
     */
    private fun idleUntil(done: () -> Boolean) {
        repeat(MAX_STEPS) {
            if (done()) return
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
        }
        check(done()) { "Nothing happened in ${MAX_STEPS * STEP_MS} ms; calls: ${actions.calls}" }
    }

    private fun stream() {
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
    }

    private val panel: Panel
        get() = (session.state.value.phase as StreamPhase.Streaming).panel

    @Test
    fun `back leaves while the stream starts`() {
        back()

        assertThat(actions.calls).containsExactly("leave")
    }

    @Test
    fun `back leaves after a failure`() {
        session.onFailed(aFailure)

        back()

        assertThat(actions.calls).containsExactly("leave")
    }

    @Test
    fun `back cancels the question about the running app`() {
        session.onConflict("Steam", "Desktop", sameApp = false, answer = ConflictAnswer())

        back()

        assertThat(actions.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    fun `back opens the menu while streaming with nothing open`() {
        stream()

        activity.onBackPressedDispatcher.onBackPressed()
        idleUntil { "menuHintShown" in actions.calls }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SETTLE_MS))

        assertThat(panel).isEqualTo(Panel.MENU)
        // The menu hint was waiting: opening the menu marks it as seen, and nothing else happens.
        assertThat(actions.calls).containsExactly("menuHintShown")
    }

    @Test
    fun `back closes the open panel`() {
        stream()
        session.openMenu()

        back()
        assertThat(panel).isEqualTo(Panel.NONE)

        session.openKeyboard()
        back()
        assertThat(panel).isEqualTo(Panel.NONE)
    }
}

private const val STEP_MS = 50L
private const val MAX_STEPS = 80
private const val SETTLE_MS = 200L
