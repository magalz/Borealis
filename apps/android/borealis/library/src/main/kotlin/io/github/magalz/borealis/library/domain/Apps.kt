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
    /**
     * True only when the user chose to resume the app: Resume or Continue on an app that the
     * Library showed running, or Try again on the stream error screen. The stream then joins that
     * app without asking if it runs. A start, a pinned shortcut, a TV program and every other way
     * in leave it false, and the stream asks.
     */
    val resumeChosen: Boolean = false,
)

/** Starts or resumes a stream in the upstream stream activity. */
fun interface StreamLauncher {
    /** Returns false when the host is no longer known. */
    fun start(activity: Activity, request: StreamRequest): Boolean
}

/** Upstream work that the 12.2 host list did and the new launcher must keep doing. */
interface LegacyBridge {
    /** Once per launcher creation: the GL renderer probe. */
    fun onLauncherCreated(activity: Activity)

    /** Every resume: the decoder crash notice. */
    fun onLauncherResumed(activity: Activity)
}
