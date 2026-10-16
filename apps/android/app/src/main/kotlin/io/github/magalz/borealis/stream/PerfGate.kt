package io.github.magalz.borealis.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Turns the per-second performance window of the decoder on or off. */
fun interface PerfToggle {
    fun set(enabled: Boolean)
}

/** Keeps the decoder's performance window on while somebody looks at its numbers. */
object PerfGate {
    /** Whether [state] shows performance numbers: the overlay is on, or the menu is open. */
    @JvmStatic
    fun enabled(state: StreamUiState): Boolean {
        val phase = state.phase as? StreamPhase.Streaming ?: return false
        return phase.overlay != OverlayMode.OFF || phase.panel == Panel.MENU
    }

    /**
     * Calls [toggle] with [enabled] of every state of [session] that changes the answer, the first
     * one included. The snapshots themselves go to `session.onPerf`.
     */
    @JvmStatic
    fun bind(scope: CoroutineScope, session: StreamSession, toggle: PerfToggle): Job =
        scope.launch {
            session.state.map(::enabled).distinctUntilChanged().collect { toggle.set(it) }
        }
}
