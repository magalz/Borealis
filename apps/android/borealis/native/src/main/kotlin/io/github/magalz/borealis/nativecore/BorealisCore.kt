package io.github.magalz.borealis.nativecore

/** Kotlin entry point to borealis-core. */
@Suppress(
    "FunctionOnlyReturningConstant",
    "UnusedParameter",
) // Stub until the tested implementation.
object BorealisCore {
    /** The borealis-core version, or null when the native library is unavailable. */
    fun version(): String? = null

    /**
     * Logs the core version and cross-checks it against the version the ndk-build bridge reports.
     */
    fun logStartup(bridgeVersion: String?, coreVersion: String? = version()) = Unit
}
