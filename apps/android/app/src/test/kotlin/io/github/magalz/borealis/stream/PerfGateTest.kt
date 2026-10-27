package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.testing.unconfinedScope
import kotlinx.coroutines.cancel
import org.junit.Test

class PerfGateTest {
    private fun enabled(session: StreamSession) = PerfGate.enabled(session.state.value)

    @Test
    fun `the compact and the detailed overlay turn the numbers on`() {
        assertThat(enabled(streamingSession(OverlayMode.COMPACT))).isTrue()
        assertThat(enabled(streamingSession(OverlayMode.DETAILED))).isTrue()
    }

    @Test
    fun `with the overlay off and nothing open the numbers stay off`() {
        assertThat(enabled(streamingSession(OverlayMode.OFF))).isFalse()
    }

    @Test
    fun `the open menu turns the numbers on for the session info`() {
        val session = streamingSession(OverlayMode.OFF)

        session.openMenu()

        assertThat(enabled(session)).isTrue()
    }

    @Test
    fun `the keyboard does not need the numbers`() {
        val session = streamingSession(OverlayMode.OFF)

        session.openKeyboard()

        assertThat(enabled(session)).isFalse()
    }

    @Test
    fun `a stream that is not running needs no numbers`() {
        val starting =
            StreamSession(
                "App",
                "PC",
                StreamDefaults(OverlayMode.DETAILED, null, null, false),
                { false },
                RecordingStreamActions(),
            )
        assertThat(enabled(starting)).isFalse()

        starting.onFailed(StreamFailure(FailureKind.GENERIC, "Error 1", null))
        assertThat(enabled(starting)).isFalse()
    }

    @Test
    fun `binding follows the overlay and the menu, and tells only about changes`() {
        val session = streamingSession(OverlayMode.OFF)
        val told = mutableListOf<Boolean>()
        val scope = unconfinedScope()

        PerfGate.bind(scope, session) { told += it }
        session.setOverlay(OverlayMode.DETAILED)
        session.setOverlay(OverlayMode.COMPACT)
        session.setOverlay(OverlayMode.OFF)
        session.openMenu()
        session.closePanel()
        scope.cancel()

        assertThat(told).containsExactly(false, true, false, true, false).inOrder()
    }

    @Test
    fun `binding stops with its scope`() {
        val session = streamingSession(OverlayMode.OFF)
        val told = mutableListOf<Boolean>()
        val scope = unconfinedScope()
        PerfGate.bind(scope, session) { told += it }

        scope.cancel()
        session.setOverlay(OverlayMode.DETAILED)

        assertThat(told).containsExactly(false)
    }
}
