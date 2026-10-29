package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

/**
 * The visuals of a stream are what its defaults carried when it started (ADR 0061, decision 1): the
 * session keeps them in its state for the whole stream, so the chrome and the menu read the same
 * ones whatever phase the stream is in.
 */
class StreamSessionVisualsTest {
    private val chosen =
        StreamVisuals(
            menuGlass = MenuGlass.SOLID,
            background = BackgroundMode.STILL,
            backgroundStyle = BackgroundStyle.NorthernLights,
        )

    private fun session(visuals: StreamVisuals) =
        aSession(defaults = aDefaults.copy(visuals = visuals))

    @Test
    fun `a stream with nothing chosen is Blur over a moving background of the default style`() {
        assertThat(StreamVisuals())
            .isEqualTo(
                StreamVisuals(MenuGlass.BLUR, BackgroundMode.MOVING, BackgroundStyle.Default)
            )
        assertThat(aSession().state.value.visuals).isEqualTo(StreamVisuals())
    }

    @Test
    fun `the state starts with the visuals that the defaults carry`() {
        assertThat(session(chosen).state.value.visuals).isEqualTo(chosen)
    }

    @Test
    fun `the visuals stay the same through the phases of the stream`() {
        val session = session(chosen)

        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        assertThat(session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(session.state.value.visuals).isEqualTo(chosen)

        session.openMenu()
        session.onPip(true)
        session.onFailed(aFailure)
        assertThat(session.state.value.visuals).isEqualTo(chosen)
    }

    @Test
    fun `the visuals stay the same when the stream is left`() {
        val quitting = session(chosen)
        quitting.quitApp()
        val disconnecting = session(chosen)
        disconnecting.disconnect()

        assertThat(quitting.state.value.visuals).isEqualTo(chosen)
        assertThat(disconnecting.state.value.visuals).isEqualTo(chosen)
    }
}
