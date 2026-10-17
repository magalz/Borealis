package io.github.magalz.borealis.library

import java.io.FileNotFoundException

/** What the exported `PosterContentProvider` accepts in the URI of a poster. */
object PosterRequest {
    /** The app id of a request for the poster of [appId] on [hostId]. */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun checkedAppId(hostId: String, appId: String): Int = appId.toInt()
}
