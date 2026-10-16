package io.github.magalz.borealis.stream

/** The Borealis keyboard of the stream. */
object StreamKeyboards {
    /** The three-finger tap: opens the keyboard, or closes it when it is open. */
    @JvmStatic
    fun toggle(session: StreamSession) {
        val phase = session.state.value.phase
        if (phase is StreamPhase.Streaming && phase.panel == Panel.KEYBOARD) {
            session.closePanel()
        } else {
            session.openKeyboard()
        }
    }
}
