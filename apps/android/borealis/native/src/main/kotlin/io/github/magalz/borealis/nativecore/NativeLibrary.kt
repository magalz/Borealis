package io.github.magalz.borealis.nativecore

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/**
 * Safe loader for one JNI library. A missing library or symbol is reported once through
 * [BorealisLog] and never reaches the caller as an exception, so a broken native chain degrades the
 * feature instead of crashing the app.
 *
 * [loader] is the seam for JVM tests; production code uses [System.loadLibrary].
 */
class NativeLibrary(
    private val name: String,
    private val loader: (String) -> Unit = System::loadLibrary,
) {
    private val available: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { tryLoad() }

    /** Loads the library on first use (and only then) and returns whether it is usable. */
    fun load(): Boolean = available

    /** Runs a native call, returning null when the library is unavailable or the call fails. */
    fun <T> call(operation: String, block: () -> T?): T? {
        if (!load()) return null
        return try {
            block()
        } catch (error: LinkageError) {
            BorealisLog.e(
                LogTag.CORE,
                "native call failed",
                error,
                "library" to name,
                "operation" to operation,
            )
            null
        }
    }

    private fun tryLoad(): Boolean =
        try {
            loader(name)
            true
        } catch (error: LinkageError) {
            reportLoadFailure(error)
        } catch (error: SecurityException) {
            reportLoadFailure(error)
        }

    private fun reportLoadFailure(error: Throwable): Boolean {
        BorealisLog.e(LogTag.CORE, "native library unavailable", error, "library" to name)
        return false
    }
}
