package io.github.magalz.borealis.nativecore

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/** Kotlin entry point to borealis-core. */
object BorealisCore {
    private val library = NativeLibrary("borealis_jni")

    /** The borealis-core version, or null when the native library is unavailable. */
    fun version(): String? = library.call("version") { CoreJni.version() }

    /**
     * Logs `core=<version>` and cross-checks it against [bridgeVersion], the version the ndk-build
     * bridge in `:app` reports for the same library. Mismatches and unavailable libraries are
     * warnings; nothing here throws. [coreVersion] is a seam for tests.
     */
    fun logStartup(bridgeVersion: String?, coreVersion: String? = version()) {
        if (coreVersion == null) {
            BorealisLog.w(LogTag.CORE, "borealis-core unavailable", "bridge" to bridgeVersion)
            return
        }
        BorealisLog.i(LogTag.CORE, "borealis-core loaded", "core" to coreVersion)
        when (bridgeVersion) {
            coreVersion -> Unit
            null ->
                BorealisLog.w(LogTag.CORE, "ndk-build bridge unavailable", "core" to coreVersion)
            else ->
                BorealisLog.w(
                    LogTag.CORE,
                    "ndk-build bridge reports a different core version",
                    "core" to coreVersion,
                    "bridge" to bridgeVersion,
                )
        }
    }
}
