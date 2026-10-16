package io.github.magalz.borealis.stream

import java.util.function.BooleanSupplier

/** The Borealis keyboard of the stream. */
object StreamKeyboards {
    /**
     * The three-finger tap: hides the Android keyboard when it is showing; otherwise opens the
     * Borealis keyboard, or closes it when it is open.
     */
    @JvmStatic
    fun toggle(session: StreamSession, imeVisible: BooleanSupplier, hideIme: Runnable) {
        if (imeVisible.asBoolean) {
            hideIme.run()
            return
        }
        val phase = session.state.value.phase
        if (phase is StreamPhase.Streaming && phase.panel == Panel.KEYBOARD) {
            session.closePanel()
        } else {
            session.openKeyboard()
        }
    }
}
