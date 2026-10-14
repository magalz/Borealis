package io.github.magalz.borealis.host.caps

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds the [BorealisHostInfo] of a host from the `/serverinfo` values the upstream client reads.
 *
 * It runs on every `/serverinfo` poll of every host, so it only logs when the information of a host
 * differs from what it logged for that host before.
 */
class HostInfoReader(private val rules: HostRules) {
    private val logged = ConcurrentHashMap<String, BorealisHostInfo>()

    /**
     * [hostId] identifies the host for the logging only. [appVersion] and [codecMask] are the raw
     * `appversion` and `ServerCodecModeSupport` texts; an absent or unreadable mask counts as 0, so
     * a host with malformed values cannot break the poll. Null when the rules are unavailable or
     * reject [appVersion].
     */
    @Suppress("LongParameterList")
    fun read(
        hostId: String?,
        appVersion: String?,
        codecMask: String?,
        httpsPort: Int,
        httpPort: Int,
    ): BorealisHostInfo? {
        // The low 32 bits are the mask: 0x80000000 arrives as 2147483648.
        val mask = codecMask?.trim()?.toLongOrNull()?.toInt() ?: 0
        val caps = rules.parseCaps(appVersion, mask) ?: return null
        val info = BorealisHostInfo(caps, httpsPort, httpPort)
        if (logged.put(hostId.orEmpty(), info) != info) log(info)
        return info
    }

    private fun log(info: BorealisHostInfo) {
        val caps = info.caps
        BorealisLog.i(
            LogTag.HOST,
            "host capabilities",
            "family" to caps.family,
            "version" to caps.version.joinToString("."),
            "hevc" to caps.hevc,
            "hevc_main10" to caps.hevcMain10,
            "av1" to caps.av1,
            "av1_main10" to caps.av1Main10,
            "yuv444" to caps.yuv444,
            "pyrowave" to caps.pyroWaveProfiles.sorted().joinToString(","),
            "http_port" to info.httpPort,
            "https_port" to info.httpsPort,
        )
    }

    companion object {
        /** The reader the upstream client uses, on the native rules. */
        @JvmField val SHARED = HostInfoReader(NativeHostRules)
    }
}
