package io.github.magalz.borealis.stream.host

import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aDefaults
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
    // No background, which animates on the screens outside the game: a test that ends with one
    // still drawing leaves the frames of the next test unscheduled in the same JVM.
    private val session: StreamSession =
        aSession(
            actions,
            aDefaults.copy(visuals = StreamVisuals(backgroundStyle = BackgroundStyle.Off)),
        )

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

        back()

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
