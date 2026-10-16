package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import org.junit.Test

class StreamKeyboardsTest {
    private fun panel(session: StreamSession) =
        (session.state.value.phase as StreamPhase.Streaming).panel

    @Test
    fun `the three-finger tap opens the Borealis keyboard`() {
        val session = streamingSession()

        StreamKeyboards.toggle(session)

        assertThat(panel(session)).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `the next tap closes it`() {
        val session = streamingSession()

        StreamKeyboards.toggle(session)
        StreamKeyboards.toggle(session)

        assertThat(panel(session)).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a stream that is not running has no keyboard to toggle`() {
        val failed =
            StreamSession(
                "App",
                "PC",
                StreamDefaults(OverlayMode.OFF, null, null, false),
                false,
                RecordingStreamActions(),
            )
        failed.onFailed(StreamFailure(FailureKind.GENERIC, "Error 1", null))

        StreamKeyboards.toggle(failed)

        assertThat(failed.state.value.phase).isInstanceOf(StreamPhase.Failed::class.java)
    }
}
