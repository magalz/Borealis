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
import io.github.magalz.borealis.stream.MenuGlass
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamUiState
import io.github.magalz.borealis.stream.ui.background.OutsideGameBackground
import io.github.magalz.borealis.stream.ui.glass.LocalFrozenFrame
import io.github.magalz.borealis.stream.ui.glass.OverFrozenFrame

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
 *
 * Every phase but Streaming stands on one background, in one host that stays while the phase moves
 * between them (Starting, the question about a running app, a failure, Quitting, Disconnecting),
 * and that goes when the phase becomes Streaming: the first frame can come before the connection
 * reports the stream, so the cut follows the phase and not the stage. The one exception is the
 * failure of a stream that ran, when the activity asked for a copy of its last frame
 * ([LocalFrozenFrame]): the popup of the error stands on that frame, frozen, in place of the
 * background, and on black while the copy is not there yet.
 */
@Composable
internal fun StreamChromeRoot(session: StreamSession, report: (Rect?) -> Unit, look: BorealisLook) {
    val state by session.state.collectAsStateWithLifecycle()
    val perf by session.perf.collectAsStateWithLifecycle()
    if (state.inPip) return
    StreamChromeTheme(look) {
        CompositionLocalProvider(LocalMenuHandleBounds provides report) {
            val phase = state.phase
            if (phase is StreamPhase.Streaming) {
                StreamingLayer(
                    state.appName,
                    state.hostName,
                    phase,
                    perf,
                    session,
                    videoGlass = state.visuals.menuGlass == MenuGlass.BLUR,
                )
            } else {
                // The failure of a stream that ran stands on its last frame once the copy of it is
                // asked for. The host of the background and the popup stay the same in all that the
                // failure goes through (no copy, a pending one, the frame, a failed copy). Only
                // what is drawn behind the popup changes, so the popup is not made again.
                val frozen = LocalFrozenFrame.current
                val failed = frozen != null && phase is StreamPhase.Failed
                OutsideGameBackground(
                    state.visuals,
                    state.decoderHeld,
                    showsBackground = !(failed && frozen.coversBackground),
                ) {
                    if (failed) {
                        OverFrozenFrame(frozen, state.visuals.menuGlass == MenuGlass.BLUR) {
                            OutsideGameScreen(state, session)
                        }
                    } else {
                        OutsideGameScreen(state, session)
                    }
                }
            }
        }
    }
}

/** The screen of a phase outside the game, over the background. */
@Composable
private fun OutsideGameScreen(state: StreamUiState, session: StreamSession) {
    when (val phase = state.phase) {
        is StreamPhase.Starting -> StartingScreen(state.appName, state.hostName, phase, session)
        is StreamPhase.Conflict -> ConflictDialog(phase, session)
        is StreamPhase.Failed ->
            ConnectionErrorScreen(state.appName, state.hostName, phase.failure, session)
        is StreamPhase.Quitting -> QuittingScreen(state.appName)
        is StreamPhase.Disconnecting -> DisconnectingScreen(state.appName)
        // The streaming layer has no background: the chrome root draws it instead of this.
        is StreamPhase.Streaming -> Unit
    }
}
