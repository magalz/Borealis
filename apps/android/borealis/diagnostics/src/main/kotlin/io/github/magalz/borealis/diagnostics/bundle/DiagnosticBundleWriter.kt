package io.github.magalz.borealis.diagnostics.bundle

import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writes the diagnostic bundle zip: `manifest.json`, `decoders.json`, one entry per section and the
 * log files under `logs/`. Every log and section line goes through [redact] before it is written,
 * so the current address-masking setting applies even to lines written earlier.
 *
 * The uncompressed content stays within [maxBytes]. Sections are placed first; the log files get
 * what is left, newest first (crash reports, then `borealis.log`, then `borealis.log.1`, ...). The
 * first file that does not fit keeps its newest lines and every older one is dropped. What was cut
 * is listed in `manifest.json`.
 */
class DiagnosticBundleWriter(
    private val logDir: File,
    private val sections: Collection<DiagnosticSection>,
    private val redact: (String) -> String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    /** Runs once before the log files are read, so queued log lines reach them first. */
    private val flushLogs: () -> Unit = {},
    private val readBytes: (File) -> ByteArray = File::readBytes,
) {
    private class Entry(val name: String, val bytes: ByteArray)

    /** Signals that a section wrote more than it may; [seen] is how much it had written. */
    private class SizeLimitExceeded(val seen: Long) : RuntimeException()

    private class CappedBuffer(private val limit: Long) : ByteArrayOutputStream() {
        @Synchronized
        override fun write(b: Int) {
            if (count + 1L > limit) throw SizeLimitExceeded(count + 1L)
            super.write(b)
        }

        @Synchronized
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (count + len.toLong() > limit) throw SizeLimitExceeded(count + len.toLong())
            super.write(b, off, len)
        }
    }

    private class LogPlan(val kept: List<Entry>, val truncated: List<TruncatedEntry>) {
        val keptBytes: Long = kept.sumOf { it.bytes.size.toLong() }
    }

    /** Writes the zip to [out] and leaves [out] open. */
    fun write(manifest: BundleManifest, decoders: DecodersReport, out: OutputStream) {
        val decodersBytes = json.encodeToString(decoders).toByteArray(Charsets.UTF_8)
        val sectionBudget =
            maxBytes - decodersBytes.size - encode(manifest).size - ENTRY_RESERVE * sections.size
        val sectionTruncated = mutableListOf<TruncatedEntry>()
        val sectionEntries = collectSections(sectionBudget, sectionTruncated)
        runCatching(flushLogs).onFailure {
            BorealisLog.w(
                LogTag.APP,
                "log flush failed before the diagnostic export",
                "error" to it.javaClass.simpleName,
            )
        }
        val logs = readLogs()
        val fixedBytes = decodersBytes.size + sectionEntries.sumOf { it.bytes.size.toLong() }

        var budget = maxBytes - fixedBytes - encode(manifest).size
        var plan = planLogs(logs, budget)
        var manifestBytes = encode(manifest.copy(truncated = sectionTruncated + plan.truncated))
        // The manifest grows with what it lists, so shrink the log budget until all of it fits.
        // Every pass lowers the budget, so this ends when it fits or when no log content is left.
        var over = fixedBytes + manifestBytes.size + plan.keptBytes - maxBytes
        while (over > 0 && budget > 0) {
            budget -= over
            plan = planLogs(logs, budget)
            manifestBytes = encode(manifest.copy(truncated = sectionTruncated + plan.truncated))
            over = fixedBytes + manifestBytes.size + plan.keptBytes - maxBytes
        }

        // finish() writes the zip trailer without closing [out].
        val zip = ZipOutputStream(out)
        zip.put(MANIFEST, manifestBytes)
        zip.put(DECODERS, decodersBytes)
        sectionEntries.forEach { zip.put(it.name, it.bytes) }
        plan.kept.forEach { zip.put(it.name, it.bytes) }
        zip.finish()
    }

    private fun encode(manifest: BundleManifest) =
        json.encodeToString(manifest).toByteArray(Charsets.UTF_8)

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun collectSections(budget: Long, truncated: MutableList<TruncatedEntry>): List<Entry> {
        var remaining = budget.coerceAtLeast(0)
        val entries = mutableListOf<Entry>()
        for (section in usableSections()) {
            val entry = capture(section, remaining, truncated) ?: continue
            remaining -= entry.bytes.size
            entries += entry
        }
        return entries
    }

    /** Sections with a safe name that no earlier entry of the zip uses, in name order. */
    private fun usableSections(): List<DiagnosticSection> {
        val taken = mutableSetOf(MANIFEST, DECODERS, LOGS_DIR)
        return sections
            .sortedBy { it.fileName }
            .filter { section ->
                val name = section.fileName
                val usable = DiagnosticSection.isSafeFileName(name) && taken.add(name)
                if (!usable) {
                    BorealisLog.w(
                        LogTag.APP,
                        "diagnostic section skipped: unusable name",
                        "name" to name,
                    )
                }
                usable
            }
    }

    /** The section's redacted content, or null (and a note in [truncated]) when it is left out. */
    private fun capture(
        section: DiagnosticSection,
        remaining: Long,
        truncated: MutableList<TruncatedEntry>,
    ): Entry? {
        val name = section.fileName
        val buffer = CappedBuffer(remaining)
        val failure = runCatching { section.writeTo(buffer) }.exceptionOrNull()
        if (failure != null) {
            truncated += sectionFailure(name, failure)
            return null
        }
        val bytes = redactLines(buffer.toByteArray())
        if (bytes.size > remaining) {
            truncated += TruncatedEntry(name, SIZE_LIMIT, 0, bytes.size.toLong())
            return null
        }
        return Entry(name, bytes)
    }

    private fun sectionFailure(name: String, failure: Throwable): TruncatedEntry =
        if (failure is SizeLimitExceeded) {
            TruncatedEntry(name, SIZE_LIMIT, 0, failure.seen)
        } else {
            BorealisLog.w(LogTag.APP, "diagnostic section failed", "name" to name)
            TruncatedEntry(name, SECTION_ERROR, 0, 0)
        }

    /** Redacted log files, newest first: crash reports, then `borealis.log`, `.1`, `.2`, ... */
    private fun readLogs(): List<Entry> {
        val files = logDir.listFiles().orEmpty().filter { it.isFile }
        val crashes = files.filter { CRASH_FILE.matches(it.name) }.sortedByDescending { it.name }
        val rotated =
            files
                .mapNotNull { file -> LOG_FILE.matchEntire(file.name)?.let { file to it } }
                .sortedBy { (_, match) -> match.groupValues[1].toIntOrNull() ?: 0 }
                .map { (file, _) -> file }
        return (crashes + rotated).mapNotNull { file ->
            runCatching { Entry("$LOGS_DIR/${file.name}", redactLines(file.readBytes())) }
                .getOrNull()
        }
    }

    /** Keeps whole entries while they fit in [budget], then the newest lines of the next one. */
    private fun planLogs(logs: List<Entry>, budget: Long): LogPlan {
        var remaining = budget.coerceAtLeast(0)
        var cut = false
        val kept = mutableListOf<Entry>()
        val truncated = mutableListOf<TruncatedEntry>()
        for (log in logs) {
            val size = log.bytes.size.toLong()
            val bytes =
                when {
                    cut -> ByteArray(0)
                    size <= remaining -> log.bytes
                    else -> newestLines(log.bytes, remaining)
                }
            if (bytes.size.toLong() != size) {
                cut = true
                truncated += TruncatedEntry(log.name, SIZE_LIMIT, bytes.size.toLong(), size)
            }
            if (bytes.isNotEmpty() || size == 0L) kept += Entry(log.name, bytes)
            remaining -= bytes.size
        }
        return LogPlan(kept, truncated)
    }

    /** The tail of [bytes] of at most [limit] bytes that starts at a line boundary. */
    private fun newestLines(bytes: ByteArray, limit: Long): ByteArray {
        var start = (bytes.size - limit).toInt().coerceAtLeast(0)
        while (start in 1 until bytes.size && bytes[start - 1] != NEWLINE) start++
        return bytes.copyOfRange(start, bytes.size)
    }

    private fun redactLines(bytes: ByteArray): ByteArray {
        val text = StringBuilder()
        bytes.inputStream().bufferedReader(Charsets.UTF_8).forEachLine {
            text.append(redact(it)).append('\n')
        }
        return text.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 16L * 1024 * 1024
        const val MANIFEST = "manifest.json"
        const val DECODERS = "decoders.json"
        const val LOGS_DIR = "logs"

        private const val SIZE_LIMIT = "size-limit"
        private const val SECTION_ERROR = "section-error"
        private const val NEWLINE = '\n'.code.toByte()

        /** Room kept in the budget for each section's possible line in the manifest. */
        private const val ENTRY_RESERVE = 512L

        private val CRASH_FILE = Regex("crash-[A-Za-z0-9._-]+\\.txt")
        private val LOG_FILE = Regex("borealis\\.log(?:\\.([1-9][0-9]{0,3}))?")
        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
