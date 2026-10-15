package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import io.github.magalz.borealis.library.domain.Host

/** The name of the running app when the cached app list does not know it (upstream uses it too). */
internal const val UNKNOWN_APP_NAME = "app"

/** The apps in a raw `/applist` answer; empty when [raw] is null or cannot be read. */
internal fun parseApps(raw: String?): List<NvApp> = TODO()

/**
 * The host as the Library shows it. [knownApps] is the parsed app list when the caller has it;
 * otherwise the cached raw list is parsed when a running app needs its name.
 */
internal fun ComputerDetails.toHost(knownApps: List<NvApp>? = null): Host = TODO()
