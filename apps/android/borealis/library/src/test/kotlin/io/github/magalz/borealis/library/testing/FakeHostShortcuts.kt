package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.HostShortcuts

class FakeHostShortcuts(override var canPinApps: Boolean = true) : HostShortcuts {
    /** Each call of [reportHostOpened]: the host id and name. */
    val openedHosts = mutableListOf<Pair<String, String>>()

    /** One entry per [pinApp] call. */
    val pinned = mutableListOf<PinCall>()

    /** What [pinApp] answers. */
    var pinResult = true

    data class PinCall(
        val hostId: String,
        val hostName: String,
        val appId: Int,
        val appName: String,
        val hdrSupported: Boolean,
    )

    override fun reportHostOpened(hostId: String, hostName: String) {
        openedHosts += hostId to hostName
    }

    override suspend fun pinApp(
        hostId: String,
        hostName: String,
        appId: Int,
        appName: String,
        hdrSupported: Boolean,
    ): Boolean {
        pinned += PinCall(hostId, hostName, appId, appName, hdrSupported)
        return pinResult
    }
}
