package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession

/**
 * Reports where the menu handle is, in the coordinates of the chrome root (`boundsInRoot`), or null
 * when it is not shown. The chrome host takes a touch that starts there and lets every other touch
 * through to the stream, so `StreamingLayer` reports the handle it draws.
 */
internal val LocalMenuHandleBounds = staticCompositionLocalOf<(Rect?) -> Unit> { {} }

/**
 * The theme of the stream chrome: the form factor of the window and the [look] that the viewer
 * chose when the stream started.
 */
@Composable
internal fun StreamChromeTheme(look: BorealisLook, content: @Composable () -> Unit) {
    BorealisTheme(rememberFormFactor(), look, content)
}

/**
 * The chrome over the stream: the screen for the phase of [session], nothing in picture-in-picture.
 * Every screen is inside [BorealisTheme] for the form factor of the window, so the screens read
 * `LocalFormFactor` and never apply the theme themselves. The chrome host learns the menu handle
 * bounds through [report], and the screens draw with the [look] that the stream started with.
 */
@Composable
internal fun StreamChromeRoot(session: StreamSession, report: (Rect?) -> Unit, look: BorealisLook) {
    val state by session.state.collectAsStateWithLifecycle()
    val perf by session.perf.collectAsStateWithLifecycle()
    if (state.inPip) return
    StreamChromeTheme(look) {
        CompositionLocalProvider(LocalMenuHandleBounds provides report) {
            when (val phase = state.phase) {
                is StreamPhase.Starting ->
                    StartingScreen(state.appName, state.hostName, phase, session)
                is StreamPhase.Conflict -> ConflictDialog(phase, session)
                is StreamPhase.Failed ->
                    ConnectionErrorScreen(state.appName, state.hostName, phase.failure, session)
                is StreamPhase.Quitting -> QuittingScreen(state.appName)
                is StreamPhase.Disconnecting -> DisconnectingScreen(state.appName)
                is StreamPhase.Streaming ->
                    StreamingLayer(state.appName, state.hostName, phase, perf, session)
            }
        }
    }
}
