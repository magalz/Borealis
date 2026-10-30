package io.github.magalz.borealis.stream.host

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

class BackWhileDisconnectingTest {
    private val actions = RecordingActions()

    @Test
    fun `back does nothing while a disconnect is under way, which ends on its own`() {
        val session = aSession(actions)
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        session.disconnect()
        actions.calls.clear()

        handleBack(session)

        assertThat(actions.calls).isEmpty()
        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Disconnecting)
    }
}
