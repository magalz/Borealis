package io.github.magalz.borealis.library

import android.os.Bundle

/** What the stream activity asks the Library to show when it leaves a stream. */
sealed interface LibraryLaunchRequest {
    /** The host list. */
    data object Hosts : LibraryLaunchRequest

    /** The apps of [hostId]. */
    data class Apps(val hostId: String) : LibraryLaunchRequest

    /** Start [appId] on [hostId]. */
    data class Launch(val hostId: String, val appId: Int) : LibraryLaunchRequest

    /** Pair with [hostId] again. */
    data class Pair(val hostId: String) : LibraryLaunchRequest

    /** Privacy and diagnostics: the screen on a phone, the Settings group on a TV or handheld. */
    data object Privacy : LibraryLaunchRequest

    /** Serialises the request for an intent. */
    fun toExtras(): Bundle =
        Bundle().apply {
            when (val request = this@LibraryLaunchRequest) {
                Hosts -> putString(KEY_REQUEST, HOSTS)
                is Apps -> {
                    putString(KEY_REQUEST, APPS)
                    putString(KEY_HOST_ID, request.hostId)
                }
                is Launch -> {
                    putString(KEY_REQUEST, LAUNCH)
                    putString(KEY_HOST_ID, request.hostId)
                    putInt(KEY_APP_ID, request.appId)
                }
                is Pair -> {
                    putString(KEY_REQUEST, PAIR)
                    putString(KEY_HOST_ID, request.hostId)
                }
                Privacy -> putString(KEY_REQUEST, PRIVACY)
            }
        }

    companion object {
        internal const val KEY_REQUEST = "io.github.magalz.borealis.library.REQUEST"
        internal const val KEY_HOST_ID = "io.github.magalz.borealis.library.HOST_ID"
        internal const val KEY_APP_ID = "io.github.magalz.borealis.library.APP_ID"

        private const val HOSTS = "hosts"
        private const val APPS = "apps"
        private const val LAUNCH = "launch"
        private const val PAIR = "pair"
        private const val PRIVACY = "privacy"

        /** The request in [extras], or null when there is none or it is malformed. */
        fun fromExtras(extras: Bundle?): LibraryLaunchRequest? {
            if (extras == null) return null
            val hostId = extras.getString(KEY_HOST_ID)?.takeIf { it.isNotBlank() }
            return when (extras.getString(KEY_REQUEST)) {
                HOSTS -> Hosts
                APPS -> hostId?.let(::Apps)
                PAIR -> hostId?.let(::Pair)
                PRIVACY -> Privacy
                LAUNCH -> launch(extras, hostId)
                else -> null
            }
        }

        private fun launch(extras: Bundle, hostId: String?): LibraryLaunchRequest? {
            // A value of another type reads as the default, so the sentinel also rejects it.
            val appId = extras.getInt(KEY_APP_ID, NO_APP)
            return if (hostId == null || appId == NO_APP) null else Launch(hostId, appId)
        }

        private const val NO_APP = Int.MIN_VALUE
    }
}
