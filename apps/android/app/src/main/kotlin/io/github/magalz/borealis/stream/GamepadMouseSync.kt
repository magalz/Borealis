package io.github.magalz.borealis.stream

import java.util.function.BooleanSupplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the gamepad-mouse choice of the menu in step with the gamepad handler. */
object GamepadMouseSync {
    /**
     * Every time the menu opens, reads [current] (the Start hold toggles the mode on the pad
     * without telling the session) and tells [session] when it differs from what the menu shows.
     * There is nothing to keep in step while the session offers no gamepad choice.
     */
    @JvmStatic
    fun bind(scope: CoroutineScope, session: StreamSession, current: BooleanSupplier): Job =
        scope.launch {
            session.state
                .map { (it.phase as? StreamPhase.Streaming)?.panel == Panel.MENU }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    val shown = (session.state.value.phase as? StreamPhase.Streaming)?.gamepadMouse
                    val onThePad = current.asBoolean
                    if (shown != null && shown != onThePad) session.setGamepadMouse(onThePad)
                }
        }
}
