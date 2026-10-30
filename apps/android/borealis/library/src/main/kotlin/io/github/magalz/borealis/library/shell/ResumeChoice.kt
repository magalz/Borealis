package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The choice that Try again on the stream error screen makes: to resume the app that runs. The
 * stream [remember]s it for the app that it asks the Library to start, and the Library [take]s it
 * once, when it handles that request.
 *
 * It travels inside the process, as [QuitNotice] does, and not in the extras of the request: the
 * Library is exported, so any app could then make this client resume what runs on the host with no
 * choice of the user (host contract 7.6). There is one choice at a time. A request that the Library
 * handles takes it, whichever app that request is for, so a shortcut or a TV program that comes
 * later never finds it; and it is [clear]ed when the Library goes before it handled the request.
 *
 * Everything here runs on the main thread: the stream's actions, and the Library's screens.
 */
@Singleton
class ResumeChoice @Inject constructor() {
    @Volatile private var remembered: Pair<String, Int>? = null

    /** Remembers that the user chose to resume [appId] on [hostId]. */
    fun remember(hostId: String, appId: Int) {
        remembered = hostId to appId
    }

    /**
     * True once, if the user chose to resume [appId] on [hostId]. The request for any app ends the
     * choice.
     */
    fun take(hostId: String, appId: Int): Boolean {
        val chosen = remembered == hostId to appId
        remembered = null
        return chosen
    }

    /** Forgets the choice. */
    fun clear() {
        remembered = null
    }
}
