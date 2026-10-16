package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession

/**
 * The chrome over the stream: the screen for the phase of [session], nothing in picture-in-picture.
 */
@Composable
fun StreamChromeRoot(session: StreamSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val perf by session.perf.collectAsStateWithLifecycle()
    if (state.inPip) return
    when (val phase = state.phase) {
        is StreamPhase.Starting -> StartingScreen(state.appName, state.hostName, phase, session)
        is StreamPhase.Conflict -> ConflictDialog(phase, session)
        is StreamPhase.Failed ->
            ConnectionErrorScreen(state.appName, state.hostName, phase.failure, session)
        is StreamPhase.Streaming ->
            StreamingLayer(state.appName, state.hostName, phase, perf, session)
    }
}
