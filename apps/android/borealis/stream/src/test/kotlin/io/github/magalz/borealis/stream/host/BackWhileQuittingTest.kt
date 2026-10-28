package io.github.magalz.borealis.stream.host

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

class BackWhileQuittingTest {
    private val actions = RecordingActions()

    @Test
    fun `back leaves the screen while a quit is under way`() {
        val session = aSession(actions)
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        val streaming = session.state.value.phase
        session.quitApp()
        session.onFailed(aFailure)
        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Quitting(streaming, aFailure))
        actions.calls.clear()

        handleBack(session)

        assertThat(actions.calls).containsExactly("leave")
    }
}
