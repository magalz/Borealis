package io.github.magalz.borealis.library

import java.io.FileNotFoundException

/**
 * What the exported `PosterContentProvider` accepts in the URI of a poster. Any app can send it a
 * URI, and the host id becomes a directory name of the cache.
 */
object PosterRequest {
    private val HOST_ID = Regex("[0-9A-Fa-f-]{1,64}")

    /**
     * The app id of a request for the poster of [appId] on [hostId]; a [FileNotFoundException] when
     * [hostId] is not a plain host id (hexadecimal digits and hyphens, so no path) or [appId] is
     * not a non-negative number.
     */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun checkedAppId(hostId: String, appId: String): Int {
        if (!HOST_ID.matches(hostId)) throw FileNotFoundException("not a host id")
        return appId.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw FileNotFoundException("not an app id")
    }
}
