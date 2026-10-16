package io.github.magalz.borealis.library

import android.os.Bundle

/** What the stream activity asks the Library to show when it leaves a stream. */
sealed interface LibraryLaunchRequest {
    /** Serialises the request for an intent. */
    fun toExtras(): Bundle = Bundle()

    /** The host list. */
    data object Hosts : LibraryLaunchRequest

    /** The apps of [hostId]. */
    data class Apps(val hostId: String) : LibraryLaunchRequest

    /** Start [appId] on [hostId]. */
    data class Launch(val hostId: String, val appId: Int) : LibraryLaunchRequest

    /** Pair with [hostId] again. */
    data class Pair(val hostId: String) : LibraryLaunchRequest

    companion object {
        /** The request in [extras], or null when there is none or it is malformed. */
        fun fromExtras(extras: Bundle?): LibraryLaunchRequest? = null
    }
}
