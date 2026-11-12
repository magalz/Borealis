package io.github.magalz.borealis.stream.host

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Test

class BackWhileFailedTest {
    private val actions = RecordingActions()

    @Test
    fun `back leaves the screen after a failure, when the error popup does not take it`() {
        val session = aSession(actions)
        session.onFailed(aFailure)
        assertThat(session.state.value.phase).isInstanceOf(StreamPhase.Failed::class.java)

        handleBack(session)

        assertThat(actions.calls).containsExactly("leave")
    }
}
