package io.github.magalz.borealis.library

import android.app.Activity
import com.limelight.nvstream.http.NvApp
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.library.domain.StreamRequest
import javax.inject.Inject
import javax.inject.Singleton

/** [StreamLauncher] over `ServerHelper.doStart`, which builds the intent of the stream activity. */
@Singleton
internal class GameStreamLauncher
@Inject
constructor(private val connection: ComputerManagerConnection) : StreamLauncher {
    override fun start(activity: Activity, request: StreamRequest): Boolean {
        val binder = connection.connectedBinder ?: return false
        // The service's own object has the pinned certificate; the last update is the fallback.
        val computer =
            binder.getComputer(request.hostId)
                ?: connection.updates.value[request.hostId]
                ?: return false
        val cached = parseApps(computer.rawAppList).firstOrNull { it.appId == request.appId }
        val hdr = cached?.isHdrSupported ?: request.hdrSupported
        // A resume knows no name; the cached list may still have it.
        val name = request.appName ?: cached?.appName ?: UNKNOWN_APP_NAME
        binder.startStream(
            activity,
            NvApp(name, request.appId, hdr),
            computer,
        )
        return true
    }
}
