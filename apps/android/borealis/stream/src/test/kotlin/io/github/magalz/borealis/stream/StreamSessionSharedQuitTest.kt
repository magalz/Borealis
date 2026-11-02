package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.RecordingLogSink
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Rule
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
 * A Quit that the host answers with 599 because it keeps the app for another device (the one that
 * started it, or one that joined), and that ends only this stream, with the quit's ENet disconnect
 * or the graceful termination, in either order. The session does not know who started the app: it
 * is the same for a stream that launched it and for one that joined it. Time is the test's: the
 * clock, and the one task that the session schedules.
 */
class StreamSessionSharedQuitTest {
    @get:Rule val log = RecordingLogSink()

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

    private fun streaming(): StreamPhase {
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

    // The graceful termination, as the activity handles it: it finishes with no failure to report,
    // so onFailed is never called, and it tells the session when the end was the quit's.
    private fun streamEndsGracefully(): Boolean {
        val byQuit = session.endedByQuit(GRACEFUL_TERMINATION)
        if (byQuit) session.onGracefulEnd()
        return byQuit
    }

    private fun runScheduled() =
        scheduled.toList().also { scheduled.clear() }.forEach { it.second() }

    @Test
    fun `a stream that ended before the answer of 599 is a clean leave of the app that keeps running`() {
        val running = streaming()
        session.quitApp()
        streamEnds()

        actions.quitFailed(shared)

        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
    }

    @Test
    fun `an end that the activity reports after the answer of 599 is still a clean leave`() {
        val running = streaming()
        session.quitApp()
        assertThat(session.endedByQuit(ENET_DISCONNECT)).isTrue()

        actions.quitFailed(shared)
        session.onFailed(aFailure)

        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
    }

    @Test
    fun `a stream that ends after the answer of 599 is a clean leave, not a failure`() {
        val running = streaming()
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
        val running = streaming()
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
        val running = streaming()
        session.quitApp()
        actions.quitFailed(shared)

        session.quitApp()
        runScheduled()

        assertThat(shared.calls).isEmpty()
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running))
    }

    @Test
    fun `a clean leave in the window leaves nothing for the held answer to show`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        streamEnds()

        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `the end of the stream exactly at the end of the window is still the quit's`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        now = WINDOW_MS

        assertThat(streamEnds()).isTrue()

        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `an end of the stream after the window is a failure as it was`() {
        streaming()
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
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        now = 1_000L

        assertThat(streamEnds(code = -100)).isFalse()

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(shared.calls).doesNotContain("leave Desktop")
    }

    @Test
    fun `an end with another code before the answer of 599 is a failure as it was`() {
        streaming()
        session.quitApp()
        streamEnds(code = -100)

        actions.quitFailed(shared)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `another error of the quit is a failure as it was`() {
        streaming()
        session.quitApp()
        streamEnds()
        val other = RecordingQuitFailure(notTheStarter = false)

        actions.quitFailed(other)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
        assertThat(other.calls).containsExactly("show")
    }

    @Test
    fun `another error of the quit is shown at once and holds nothing`() {
        val running = streaming()
        session.quitApp()
        val other = RecordingQuitFailure(notTheStarter = false)

        actions.quitFailed(other)

        assertThat(phase).isEqualTo(running)
        assertThat(other.calls).containsExactly("show")
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `a quit that succeeds is unchanged`() {
        val running = streaming()
        session.quitApp()

        streamEnds()

        assertThat(phase).isEqualTo(StreamPhase.Quitting(running, aFailure))
        assertThat(shared.calls).isEmpty()
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `a Disconnect in the window drops the held answer, so it is not shown after the window`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)

        session.disconnect()
        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).isEmpty()
    }

    @Test
    fun `the end of the stream after a Disconnect in the window is not the quit's, and no line says so`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        session.disconnect()
        now = 1_000L

        val byQuit = streamEnds()

        assertThat(byQuit).isFalse()
        assertThat(log.records.map { it.message }).doesNotContain("quit stream ended")
        assertThat(log.records.map { it.message }).doesNotContain("quit of a shared app is a leave")
        assertThat(shared.calls).isEmpty()
        assertThat(phase).isEqualTo(StreamPhase.Disconnecting)
    }

    @Test
    fun `a leave in the window drops the held answer, so it is not shown after the window`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)

        session.leave()
        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).isEmpty()
    }

    @Test
    fun `the stop of the connection in the window drops the held answer, so no toast follows the activity`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)

        session.onConnectionStopped()
        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).isEmpty()
        assertThat(session.endedByQuit(ENET_DISCONNECT)).isFalse()
    }

    @Test
    fun `a Disconnect that is refused keeps the held answer`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        streamEnds(code = -100)
        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))

        // The session failed, so the Disconnect is refused and the answer is still to be shown.
        session.disconnect()
        now = WINDOW_MS
        runScheduled()

        assertThat(shared.calls).containsExactly("show")
    }

    // A host that sends the graceful termination before it ends a cancelled session
    // (NortheBridge/luminalshine#232) ends the stream with code 0, not with the ENet disconnect.

    @Test
    fun `a graceful end before the answer of 599 is a clean leave of the app that keeps running`() {
        val running = streaming()
        session.quitApp()
        assertThat(streamEndsGracefully()).isTrue()

        actions.quitFailed(shared)

        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(phase).isEqualTo(StreamPhase.Quitting(running))
        runScheduled()
        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `a graceful end after the answer of 599 is a clean leave, not a failure`() {
        val running = streaming()
        session.quitApp()
        actions.quitFailed(shared)
        now = 1_000L

        val byQuit = streamEndsGracefully()

        assertThat(byQuit).isTrue()
        assertThat(shared.calls).containsExactly("leave Desktop")
        assertThat(log.records.map { it.message }).contains("quit of a shared app is a leave")
        assertThat(phase).isEqualTo(running)
        runScheduled()
        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `a graceful end exactly at the end of the window is still the quit's`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        now = WINDOW_MS

        assertThat(streamEndsGracefully()).isTrue()

        assertThat(shared.calls).containsExactly("leave Desktop")
    }

    @Test
    fun `a graceful end after the window is not the quit's, the answer is shown as it was`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        now = WINDOW_MS + 1

        assertThat(streamEndsGracefully()).isFalse()

        runScheduled()
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `a graceful end after a Disconnect in the window is not the quit's, and no line says so`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)
        session.disconnect()
        now = 1_000L

        assertThat(streamEndsGracefully()).isFalse()

        assertThat(log.records.map { it.message }).doesNotContain("quit stream ended")
        assertThat(log.records.map { it.message }).doesNotContain("quit of a shared app is a leave")
        assertThat(shared.calls).isEmpty()
    }

    @Test
    fun `a graceful end that was not the quit's leaves the held answer to its window`() {
        streaming()
        session.quitApp()
        actions.quitFailed(shared)

        session.onGracefulEnd()

        assertThat(shared.calls).isEmpty()
        now = WINDOW_MS
        runScheduled()
        assertThat(shared.calls).containsExactly("show")
    }

    @Test
    fun `a graceful end that ends a simple quit leaves nothing for the session to do`() {
        val running = streaming()
        session.quitApp()

        assertThat(streamEndsGracefully()).isTrue()

        assertThat(phase).isEqualTo(StreamPhase.Quitting(running))
        assertThat(shared.calls).isEmpty()
        assertThat(scheduled).isEmpty()
    }

    private companion object {
        const val ENET_DISCONNECT = -1
        const val GRACEFUL_TERMINATION = 0
        const val WINDOW_MS = 5_000L
    }
}
