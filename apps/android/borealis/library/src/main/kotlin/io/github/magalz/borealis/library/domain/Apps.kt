package io.github.magalz.borealis.library.domain

import android.app.Activity
import kotlinx.coroutines.flow.Flow

data class App(val id: Int, val name: String, val hdrSupported: Boolean, val hidden: Boolean)

/** One state of a host's app list. */
data class AppsSnapshot(
    /** The host, or null when it is no longer known. */
    val host: Host?,
    /** Every app, hidden ones included. */
    val apps: List<App>,
    /** True until the first app list arrives. */
    val loading: Boolean,
)

sealed interface QuitResult {
    data object Quit : QuitResult

    /** The localized text to show, or null for the generic text. */
    data class Failed(val message: String?) : QuitResult
}

/** The apps of one host. The app module implements it over the app list poller and NvHTTP. */
interface AppRepository {
    /** The apps of [hostId]. Collecting polls the app list; cancelling stops the poller. */
    fun apps(hostId: String): Flow<AppsSnapshot>

    /** Hides an app from this list on this device. Shared with the 12.2 app list. */
    suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean)

    /** Quits the running app on the host (GameStream `/cancel`). */
    suspend fun quit(hostId: String): QuitResult
}

data class StreamRequest(
    val hostId: String,
    val appId: Int,
    /** Null when the name is not known; the launcher then uses the name upstream uses to resume. */
    val appName: String?,
    val hdrSupported: Boolean,
)

/** Starts or resumes a stream in the upstream stream activity. */
fun interface StreamLauncher {
    /** Returns false when the host is no longer known. */
    fun start(activity: Activity, request: StreamRequest): Boolean
}

/** Upstream work that the 12.2 host list did and the new launcher must keep doing. */
interface LegacyBridge {
    /**
     * Once per launcher creation: default preferences, the GL renderer probe, decoder crash notice.
     */
    fun onLauncherCreated(activity: Activity)

    fun onLauncherResumed(activity: Activity)

    /** Opens the upstream stream settings screen, which links to the Borealis settings. */
    fun openStreamSettings(activity: Activity)
}
