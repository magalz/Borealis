package io.github.magalz.borealis.logging

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Uncaught-exception handler that writes a redacted `crash-<timestamp>.txt` report into [directory]
 * and then delegates to the [previous] handler. Failing to write the report never prevents the
 * delegation.
 */
class CrashReportHandler(
    private val directory: File,
    val previous: Thread.UncaughtExceptionHandler?,
    private val redactor: Redactor = Redactor(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val header: List<String> = emptyList(),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { writeReport(thread, throwable) }
        previous?.uncaughtException(thread, throwable)
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        val now = clock()
        val report = buildString {
            appendLine("Borealis crash report")
            appendLine("time=${LogFormatter.formatTimestamp(now)}")
            appendLine("thread=${thread.name}")
            header.forEach { appendLine(it) }
            appendLine()
            append(throwable.stackTraceToString())
        }
        val file = newReportFile(now)
        file.writeText(redactor.redact(report, CRASH_MAX_LENGTH), Charsets.UTF_8)
        pruneOldReports()
    }

    /** Creates a fresh, uniquely named report file so crashes in one millisecond do not collide. */
    private fun newReportFile(now: Long): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val base = "crash-${FILE_TIMESTAMP.format(Instant.ofEpochMilli(now))}"
        var index = 0
        while (true) {
            val file = File(directory, if (index == 0) "$base.txt" else "$base-$index.txt")
            if (file.createNewFile()) return file
            index++
        }
    }

    /** Keeps only the newest [MAX_REPORTS] `crash-*.txt` files; other files are left alone. */
    private fun pruneOldReports() {
        // Names embed a fixed-width UTC timestamp (plus an optional -N suffix), so name order is
        // age order.
        directory
            .listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }
            .orEmpty()
            .sortedBy { it.name.removeSuffix(".txt") }
            .dropLast(MAX_REPORTS)
            .forEach { it.delete() }
    }

    private companion object {
        const val MAX_REPORTS = 10
        const val CRASH_MAX_LENGTH = 256 * 1024
        val FILE_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}

/** Installs a [CrashReportHandler] as the process-wide default handler. */
object CrashHandler {
    /**
     * Wraps the current default handler; installing again replaces the earlier report writer
     * instead of chaining a second one.
     */
    @Synchronized
    fun install(
        directory: File,
        header: List<String> = emptyList(),
        redactor: Redactor = Redactor(),
    ): CrashReportHandler {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        val previous = if (current is CrashReportHandler) current.previous else current
        return CrashReportHandler(directory, previous, header = header).also {
            Thread.setDefaultUncaughtExceptionHandler(it)
        }
    }
}
