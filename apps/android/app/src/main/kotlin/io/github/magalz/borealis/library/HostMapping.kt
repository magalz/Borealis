package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostCaps
import java.io.IOException
import java.io.StringReader
import org.xmlpull.v1.XmlPullParserException

/** The name that upstream gives an app it only resumes; never shown, only passed to the stream. */
internal const val UNKNOWN_APP_NAME = "app"

/** The apps in a raw `/applist` answer; empty when [raw] is null or cannot be read. */
internal fun parseApps(raw: String?): List<NvApp> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        NvHTTP.getAppListByReader(StringReader(raw))
    } catch (e: XmlPullParserException) {
        unreadable(e)
    } catch (e: IOException) {
        unreadable(e)
    } catch (e: NumberFormatException) {
        // A root element without a status code, which the upstream parser does not guard against.
        unreadable(e)
    }
}

/** Parses a raw app list. Not remembering anything yet. */
internal class ParsedAppLists(private val parse: (String?) -> List<NvApp> = ::parseApps) {
    fun of(raw: String?): List<NvApp> = parse(raw)
}

internal val sharedAppLists = ParsedAppLists()

private fun unreadable(error: Exception): List<NvApp> {
    BorealisLog.w(LogTag.HOST, "app list unreadable", "error" to error.javaClass.simpleName)
    return emptyList()
}

/**
 * The host as the Library shows it. [knownApps] is the parsed app list when the caller has it;
 * otherwise the cached raw list is parsed when a running app needs its name or the host info makes
 * the HDR capability worth asking. A host that is not paired has no app list, so it shows no
 * running app; a paired host whose list does not name the running app shows it without a name.
 *
 * The capabilities come from the host info: a host without it shows none. There is no host-level
 * HDR flag, so HDR is set when any app of the list supports it. The version is not shown: Sunshine
 * and LuminalShine both report the fixed GameStream version, so it says nothing about the host.
 */
internal fun ComputerDetails.toHost(
    knownApps: List<NvApp>? = null,
    appLists: ParsedAppLists = sharedAppLists,
): Host {
    val apps by lazy { knownApps ?: appLists.of(rawAppList) }
    val running =
        runningGameId
            .takeIf { it != 0 && pairState == PairState.PAIRED }
            ?.let { id -> RunningApp(id, apps.firstOrNull { it.appId == id }?.appName) }
    val caps = borealis?.caps
    return Host(
        id = uuid,
        name = name.orEmpty(),
        reachability =
            when (state) {
                ComputerDetails.State.ONLINE -> Reachability.ONLINE
                ComputerDetails.State.OFFLINE -> Reachability.OFFLINE
                else -> Reachability.UNKNOWN
            },
        paired = pairState == PairState.PAIRED,
        runningApp = running,
        canWake = macAddress != null,
        address = (activeAddress ?: manualAddress ?: localAddress)?.address,
        capabilities =
            caps?.let { capabilities(it, apps.any { app -> app.isHdrSupported }) }.orEmpty(),
    )
}

private fun capabilities(caps: HostCaps, hdr: Boolean): Set<HostCapability> =
    listOfNotNull(
            HostCapability.HEVC.takeIf { caps.hevc },
            HostCapability.HEVC_10_BIT.takeIf { caps.hevcMain10 },
            HostCapability.AV1.takeIf { caps.av1 },
            HostCapability.AV1_10_BIT.takeIf { caps.av1Main10 },
            HostCapability.YUV_444.takeIf { caps.yuv444 },
            HostCapability.HDR.takeIf { hdr },
        )
        .toSet()
