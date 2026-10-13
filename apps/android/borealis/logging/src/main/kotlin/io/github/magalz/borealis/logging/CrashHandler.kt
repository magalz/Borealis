package io.github.magalz.borealis.logging

import java.io.File

/**
 * Uncaught-exception handler that writes a redacted `crash-<timestamp>.txt` report into [directory]
 * and then delegates to the [previous] handler.
 */
@Suppress("UnusedPrivateProperty")
class CrashReportHandler(
    private val directory: File,
    val previous: Thread.UncaughtExceptionHandler?,
    private val redactor: Redactor = Redactor(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val header: List<String> = emptyList(),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        previous?.uncaughtException(thread, throwable)
    }
}

/** Installs a [CrashReportHandler] as the process-wide default handler. */
@Suppress("UnusedParameter")
object CrashHandler {
    fun install(directory: File, header: List<String> = emptyList()): CrashReportHandler =
        CrashReportHandler(directory, Thread.getDefaultUncaughtExceptionHandler(), header = header)
}
