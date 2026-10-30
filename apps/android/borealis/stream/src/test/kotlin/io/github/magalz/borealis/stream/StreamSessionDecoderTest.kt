package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

/**
 * When a decoder of the stream is held (ADR 0061, decision 6): from the first step of the
 * connection until the connection has stopped. A clip of the background plays only while none is,
 * so the screens read [StreamUiState.decoderHeld] to release it before the stream's decoder is
 * configured and to show its still while it is up.
 */
class StreamSessionDecoderTest {
    private val session = aSession()

    private val held: Boolean
        get() = session.state.value.decoderHeld

    @Test
    fun `none is held before the connection starts`() {
        assertThat(held).isFalse()

        session.onStage(StartStage.CONNECTING)
        assertThat(held).isFalse()

        session.onStage(StartStage.STARTING_APP)
        assertThat(held).isFalse()
    }

    @Test
    fun `none is held while the host asks for a retry or about another running app`() {
        session.onStage(StartStage.STARTING_APP)
        session.onLaunchRetry(LaunchRetry(null, attempt = 1, maxAttempts = 3, delayMs = 2000))
        assertThat(held).isFalse()

        session.onConflict("Steam", "Desktop", sameApp = false, answer = ConflictAnswer())
        assertThat(session.state.value.phase).isInstanceOf(StreamPhase.Conflict::class.java)
        assertThat(held).isFalse()
    }

    @Test
    fun `none is held when the start fails before the connection`() {
        session.onStage(StartStage.STARTING_APP)

        session.onFailed(aFailure)

        assertThat(held).isFalse()
    }

    @Test
    fun `one is held from the first step of the connection`() {
        session.onStage(StartStage.PREPARING_DISPLAY)

        assertThat(held).isTrue()
    }

    @Test
    fun `one is held until the first frame, and while streaming`() {
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onConnectionStarted(aSessionInfo)
        assertThat(held).isTrue()

        session.onFirstFrame()
        assertThat(session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(held).isTrue()
    }

    @Test
    fun `one is held once the stream is up, whichever event came first`() {
        session.onConnectionStarted(aSessionInfo)

        assertThat(held).isTrue()
    }

    @Test
    fun `one is held while the stream is left and while an error is on the screen`() {
        val quitting = aSession().also { it.onStage(StartStage.PREPARING_DISPLAY) }
        quitting.quitApp()
        val disconnecting = aSession().also { it.onStage(StartStage.PREPARING_DISPLAY) }
        disconnecting.disconnect()
        val failed = aSession().also { it.onStage(StartStage.PREPARING_DISPLAY) }
        failed.onFailed(aFailure)

        assertThat(quitting.state.value.decoderHeld).isTrue()
        assertThat(disconnecting.state.value.decoderHeld).isTrue()
        assertThat(failed.state.value.decoderHeld).isTrue()
    }

    @Test
    fun `a connection that starts after a failure holds its decoder, and the failure stays`() {
        session.onStage(StartStage.STARTING_APP)
        session.onFailed(aFailure)

        session.onConnectionStarted(aSessionInfo)

        assertThat(held).isTrue()
        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `none is held once the connection has stopped`() {
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        session.disconnect()

        session.onConnectionStopped()

        assertThat(held).isFalse()
        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Disconnecting)
    }

    @Test
    fun `a stop that nothing held changes nothing`() {
        session.onConnectionStopped()

        assertThat(held).isFalse()
        assertThat(session.state.value.phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))
    }
}
