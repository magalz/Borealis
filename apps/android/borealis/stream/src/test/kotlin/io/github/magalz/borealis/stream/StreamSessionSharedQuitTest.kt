package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

/** A quit that did not happen, which records what the session chose to do about it. */
internal class RecordingQuitFailure(override val notTheStarter: Boolean = false) : QuitFailure {
    val calls = mutableListOf<String>()

    override fun show() {
        calls += "show"
    }

    override fun leaveKeepingApp(appName: String) {
        calls += "leave $appName"
    }
}

/**
 * A Quit of an app that the stream joined, once the host keeps the app for the device that started
 * it: the host answers the cancel with 599 and ends only this stream, with the quit's ENet
 * disconnect, in either order. Time is the test's: the clock, and the one task that the session
 * schedules.
 */
class StreamSessionSharedQuitTest {
    private val actions = RecordingActions()
    private var now = 0L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val session =
        StreamSession(
            "Desktop",
            "Gaming PC",
            aDefaults,
            { false },
            actions,
            { now },
            { delayMs, action -> scheduled += delayMs to action },
        )
    private val shared = RecordingQuitFailure(notTheStarter = true)

    private val phase: StreamPhase
        get() = session.state.value.phase

    private fun streaming(resume: Boolean): StreamPhase {
        session.launchRequested(resume)
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        return phase
    }

    // The activity reports an end as it does: the connection thread asks whether the quit caused
    // it, and the main thread then reports the failure.
    private fun streamEnds(code: Int = ENET_DISCONNECT): Boolean {
        val byQuit = session.endedByQuit(code)
        session.onFailed(aFailure)
        return byQuit
    }

    private fun runScheduled() =
        scheduled.toList().also { scheduled.clear() }.forEach { it.second() }

    @Test
    fun `a stream that ended before the answer of 599 is a clean leave of the app that keeps running`() {
        val running = streaming(resume = true)
        session.quitApp()
        streamEnds()

        actions.quitFailed(shared)

        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
    }

    @Test
    fun `an end that the activity reports after the answer of 599 is still a clean leave`() {
        val running = streaming(resume = true)
        session.quitApp()
        assertThat(session.endedByQuit(ENET_DISCONNECT)).isTrue()

        actions.quitFailed(shared)
        session.onFailed(aFailure)

        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
    }

    @Test
    fun `a stream that ends after the answer of 599 is a clean leave, not a failure`() {
        val running = streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)
        now = 1_000L

        val byQuit = streamEnds()

        assertThat(byQuit).isTrue()
        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(running)
    }

    @Test
    fun `the answer of 599 is held for the window, then shown if the stream is still up`() {
        val running = streaming(resume = true)
        session.quitApp()

        actions.quitFailed(shared)

        assertThat(phase).isEqualTo(running)
        assertThat(shared.calls).isEmpty()
        assertThat(scheduled.map { it.first }).containsExactly(WINDOW_MS)

        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).containsExactly("show")
        assertThat(phase).isEqualTo(running)
    }

    @Test
    fun `a new quit takes the held answer with it, so the old one is not shown after it`() {
        val running = streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)

        session.quitApp()
        runScheduled()

        assertThat(shared.calls).isEmpty()
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running))
    }

    @Test
    fun `a clean leave in the window leaves nothing for the held answer to show`() {
        streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)
        streamEnds()

        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `the end of the stream exactly at the end of the window is still the quit's`() {
        streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)
        now = WINDOW_MS

        assertThat(streamEnds()).isTrue()

        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `an end of the stream after the window is a failure as it was`() {
        streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)
        now = WINDOW_MS + 1

        assertThat(streamEnds()).isFalse()

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        runScheduled()
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `an end of the stream with another code in the window is a failure as it was`() {
        streaming(resume = true)
        session.quitApp()
        actions.quitFailed(shared)
        now = 1_000L

        assertThat(streamEnds(code = -100)).isFalse()

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(shared.calls).doesNotContain("leave Desktop")
    }

    @Test
    fun `an end with another code before the answer of 599 is a failure as it was`() {
        streaming(resume = true)
        session.quitApp()
        streamEnds(code = -100)

        actions.quitFailed(shared)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `a session that launched the app is not a clean leave, the quit fails as it did`() {
        streaming(resume = false)
        session.quitApp()
        streamEnds()

        actions.quitFailed(shared)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `a session that launched the app shows the answer of 599 at once and holds nothing`() {
        val running = streaming(resume = false)
        session.quitApp()

        actions.quitFailed(shared)

        assertThat(phase).isEqualTo(running)
        assertThat(shared.calls).containsExactly("show")
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `another error of the quit on a resumed session is a failure as it was`() {
        streaming(resume = true)
        session.quitApp()
        streamEnds()
        val other = RecordingQuitFailure(notTheStarter = false)

        actions.quitFailed(other)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(other.calls).containsExactly("show")
    }

    @Test
    fun `another error of the quit on a resumed session is shown at once and holds nothing`() {
        val running = streaming(resume = true)
        session.quitApp()
        val other = RecordingQuitFailure(notTheStarter = false)

        actions.quitFailed(other)

        assertThat(phase).isEqualTo(running)
        assertThat(other.calls).containsExactly("show")
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `a quit that succeeds on a resumed session is unchanged`() {
        val running = streaming(resume = true)
        session.quitApp()

        streamEnds()

        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
        assertThat(shared.calls).isEmpty()
        assertThat(scheduled).isEmpty()
    }

    private companion object {
        const val ENET_DISCONNECT = -1
        const val WINDOW_MS = 5_000L
    }
}
