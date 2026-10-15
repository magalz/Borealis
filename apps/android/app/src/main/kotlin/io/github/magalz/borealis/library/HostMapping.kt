package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import java.io.StringReader
import org.xmlpull.v1.XmlPullParserException

/** The name of the running app when the cached app list does not know it (upstream uses it too). */
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

private fun unreadable(error: Exception): List<NvApp> {
    BorealisLog.w(LogTag.HOST, "app list unreadable", "error" to error.javaClass.simpleName)
    return emptyList()
}

/**
 * The host as the Library shows it. [knownApps] is the parsed app list when the caller has it;
 * otherwise the cached raw list is parsed when a running app needs its name.
 */
internal fun ComputerDetails.toHost(knownApps: List<NvApp>? = null): Host {
    val running =
        runningGameId
            .takeIf { it != 0 }
            ?.let { id ->
                val app = (knownApps ?: parseApps(rawAppList)).firstOrNull { it.appId == id }
                RunningApp(id, app?.appName ?: UNKNOWN_APP_NAME)
            }
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
    )
}
