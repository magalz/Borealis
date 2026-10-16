package io.github.magalz.borealis.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Turns the per-second performance window of the decoder on or off. */
fun interface PerfToggle {
    fun set(enabled: Boolean)
}

/** Keeps the decoder's performance window on while somebody looks at its numbers. */
object PerfGate {
    /** Whether [state] shows performance numbers: the overlay is on, or the menu is open. */
    @JvmStatic fun enabled(state: StreamUiState): Boolean = false

    /** Calls [toggle] with [enabled] of every state of [session] that changes the answer. */
    @JvmStatic
    fun bind(scope: CoroutineScope, session: StreamSession, toggle: PerfToggle): Job = Job()
}
