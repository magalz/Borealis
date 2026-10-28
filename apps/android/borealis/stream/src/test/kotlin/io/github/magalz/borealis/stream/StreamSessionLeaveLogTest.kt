package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.RecordingLogSink
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Rule
import org.junit.Test

/** The log of a Quit: where its time goes, without a word about the host. */
class StreamSessionLeaveLogTest {
    @get:Rule val log = RecordingLogSink()

    private var now = 0L
    private val actions = RecordingActions()

    private fun streaming(): StreamSession =
        aSession(actions, clockMs = { now }).also {
            it.onConnectionStarted(aSessionInfo)
            it.onFirstFrame()
        }

    private fun lines(): List<String> = log.records.map { it.message }

    @Test
    fun `confirming a quit logs that it was accepted, at info`() {
        val session = streaming()

        session.quitApp()

        val accepted = log.records.single { it.message == "quit accepted" }
        assertThat(accepted.level).isEqualTo(LogLevel.INFO)
    }

    @Test
    fun `a quit that is under way is accepted once`() {
        val session = streaming()
        session.quitApp()

        session.quitApp()

        assertThat(lines().filter { it == "quit accepted" }).hasSize(1)
    }

    @Test
    fun `the end of the stream while quitting is the quit's, with its code and the time since it`() {
        val session = streaming()
        now = 1_000L
        session.quitApp()
        now = 1_250L

        val byQuit = session.endedByQuit(-1)

        assertThat(byQuit).isTrue()
        val ended = log.records.single { it.message == "quit stream ended" }
        assertThat(ended.level).isEqualTo(LogLevel.INFO)
        assertThat(ended.fields).containsExactly("code" to "-1", "since_quit_ms" to "250").inOrder()
    }

    @Test
    fun `the end of a stream nobody quit is not the quit's and logs nothing`() {
        val session = streaming()

        val byQuit = session.endedByQuit(-1)

        assertThat(byQuit).isFalse()
        assertThat(lines()).doesNotContain("quit stream ended")
    }

    @Test
    fun `a quit that failed leaves the next end of the stream to the failure path`() {
        val session = streaming()
        session.quitApp()
        actions.quitFailed()

        val byQuit = session.endedByQuit(-1)

        assertThat(byQuit).isFalse()
    }
}
