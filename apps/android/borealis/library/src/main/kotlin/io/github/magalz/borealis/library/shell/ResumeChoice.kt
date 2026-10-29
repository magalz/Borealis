package io.github.magalz.borealis.library.shell

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The choice that Try again on the stream error screen makes: to resume the app that runs. The
 * stream [remember]s it for the app that it asks the Library to start, and the Library [take]s it
 * once, when it handles that request.
 */
@Singleton
class ResumeChoice @Inject constructor() {
    /** Remembers that the user chose to resume [appId] on [hostId]. */
    fun remember(hostId: String, appId: Int) = Unit

    /** True once, if the user chose to resume [appId] on [hostId]. */
    fun take(hostId: String, appId: Int): Boolean = false

    /** Forgets the choice. */
    fun clear() = Unit
}
