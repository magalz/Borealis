package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Try again on the stream error screen leaves for the Library: the app that it asks the
 * Library to start again. The stream [remember]s it, and the Library [clear]s it when it handles
 * that request.
 *
 * It is not the user's choice to resume that app, because the host does not tell whose session
 * runs: another device may have started the app since. The Library starts the app as it does for a
 * shortcut, and the stream asks when the app runs. It is kept for the day the host tells the
 * session of this device apart, when Try again can join it again without a question.
 *
 * It travels inside the process, as [QuitNotice] does, and not in the extras of the request: the
 * Library is exported, so any app could then make this client act as if the user had chosen. There
 * is one at a time. A request that the Library handles ends it, whichever app that request is for,
 * so a shortcut or a TV program that comes later never finds it; and it is [clear]ed when the
 * Library goes before it handled the request.
 *
 * Everything here runs on the main thread: the stream's actions, and the Library's screens.
 */
@Singleton
class ResumeChoice @Inject constructor() {
    @Volatile private var remembered: Pair<String, Int>? = null

    /** Remembers that Try again asked the Library to start [appId] on [hostId] again. */
    fun remember(hostId: String, appId: Int) {
        remembered = hostId to appId
    }

    /**
     * True once, if Try again asked for [appId] on [hostId]. The request for any app ends the
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
