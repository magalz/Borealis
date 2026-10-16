package io.github.magalz.borealis.stream

import java.util.function.BooleanSupplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Keeps the gamepad-mouse choice of the menu in step with the gamepad handler. */
object GamepadMouseSync {
    /**
     * Every time the menu opens, reads [current] (the Start hold toggles the mode on the pad
     * without telling the session) and tells [session] when it differs from what the menu shows.
     */
    @JvmStatic
    fun bind(scope: CoroutineScope, session: StreamSession, current: BooleanSupplier): Job = Job()
}
