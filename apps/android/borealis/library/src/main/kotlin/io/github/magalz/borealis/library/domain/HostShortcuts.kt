package io.github.magalz.borealis.library.domain

/**
 * The launcher shortcuts of hosts and apps. A shortcut opens the Library on the apps of the host,
 * or starts the app. The app module implements it over the platform shortcut manager.
 */
interface HostShortcuts {
    /**
     * True when this device can pin an app to the home screen: the launcher allows it, not a TV.
     */
    val canPinApps: Boolean

    /** The user opened [hostId]: publishes or updates its shortcut in the launcher. */
    fun reportHostOpened(hostId: String, hostName: String)

    /**
     * Asks the launcher to pin a shortcut that starts [appId] on [hostId], with the box art of the
     * app as its icon when there is some. The launcher may ask the user; false means it cannot.
     * Fetching the art may wait for the host, so this suspends.
     */
    suspend fun pinApp(
        hostId: String,
        hostName: String,
        appId: Int,
        appName: String,
        hdrSupported: Boolean,
    ): Boolean
}
