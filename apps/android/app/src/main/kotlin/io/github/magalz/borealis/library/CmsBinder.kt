package io.github.magalz.borealis.library

import android.app.Activity
import com.limelight.computers.ComputerManagerListener
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp

/**
 * The part of the upstream `ComputerManagerService.ComputerManagerBinder` that the Library adapters
 * use. The seam keeps the real service out of the unit tests.
 */
internal interface CmsBinder {
    val uniqueId: String

    fun startPolling(listener: ComputerManagerListener)

    fun stopPolling()

    /** Blocks until no poll is running. */
    fun waitForPollingStopped()

    /** Blocks while the host is polled; true when it answered. */
    fun addComputerBlocking(details: ComputerDetails): Boolean

    fun removeComputer(details: ComputerDetails)

    /** The live details object that the service keeps and polls, or null. */
    fun getComputer(uuid: String): ComputerDetails?

    fun invalidateStateForComputer(uuid: String)

    fun createAppListPoller(computer: ComputerDetails): AppListPoller

    /** `ServerHelper.doStart`; [resumeChosen] is `StreamRequest.resumeChosen`. */
    fun startStream(
        activity: Activity,
        app: NvApp,
        computer: ComputerDetails,
        resumeChosen: Boolean,
    )
}

/** The upstream `ApplistPoller`: its results arrive through the polling listener. */
internal interface AppListPoller {
    fun start()

    fun stop()
}

/** Binds the computer manager service. Returns when its first binder is ready. */
internal fun interface CmsConnector {
    suspend fun connect(): CmsBinder
}
