package io.github.magalz.borealis.diagnostics.bundle

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What the settings screen does with a finished bundle. */
sealed interface ExportResult {
    /** Hand [chooser] to `startActivity`. */
    class Share(val chooser: Intent) : ExportResult

    /** Nothing can receive the zip (Google TV): it was saved to [file]; show how to fetch it. */
    class Saved(val file: File, val adbPullCommand: String) : ExportResult

    /** The bundle could not be built or saved; the cause is in the log. */
    data object Failed : ExportResult
}

/** Builds the bundle, then shares it, or saves it when no app can receive it. */
internal class ExportFlow(
    private val packageName: String,
    private val chooserTitle: CharSequence,
    private val buildBundle: () -> File,
    private val uriFor: (File) -> Uri,
    private val canShare: (Intent) -> Boolean,
    private val externalDir: () -> File?,
) {
    /** Blocking; never throws. */
    fun run(): ExportResult = runCatching {
        deliver()
    }
        .getOrElse { error ->
            BorealisLog.e(LogTag.APP, "diagnostic export failed", error)
            ExportResult.Failed
        }

    private fun deliver(): ExportResult {
        val zip = buildBundle()
        val uri = uriFor(zip)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(ZIP_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri(null, uri) }
        if (canShare(send)) {
            return ExportResult.Share(
                Intent.createChooser(send, chooserTitle)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
        val dir = checkNotNull(externalDir()) { "No external files directory" }
        check(dir.isDirectory || dir.mkdirs()) {
            "Cannot create the external diagnostics directory"
        }
        BundleFiles.pruneOld(dir)
        val saved = zip.copyTo(File(dir, zip.name), overwrite = true)
        return ExportResult.Saved(
            saved,
            "adb pull /sdcard/Android/data/$packageName/files/$EXTERNAL_DIR_NAME/${saved.name}",
        )
    }

    companion object {
        const val ZIP_MIME_TYPE = "application/zip"

        /** Sub-directory of `getExternalFilesDir` that holds the saved bundle. */
        const val EXTERNAL_DIR_NAME = "diagnostics"
    }
}

/**
 * The authority of the FileProvider that `AndroidManifest.xml` declares as `${applicationId}.…`.
 */
internal fun fileProviderAuthority(packageName: String) = "$packageName.diagnostics.fileprovider"

/** Naming and housekeeping of the bundle files. */
internal object BundleFiles {
    const val PREFIX = "borealis-diagnostics-"
    private const val SUFFIX = ".zip"
    private const val UNFINISHED_SUFFIX = ".zip.part"
    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun newName(now: Instant): String = PREFIX + TIMESTAMP.format(now) + SUFFIX

    fun unfinishedName(name: String): String = name + ".part"

    /** Deletes earlier bundles (and unfinished ones) in [dir]. */
    fun pruneOld(dir: File) {
        dir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(PREFIX) }
            .filter { it.name.endsWith(SUFFIX) || it.name.endsWith(UNFINISHED_SUFFIX) }
            .forEach { it.delete() }
    }
}

/** Builds the bundle zip in `cacheDir/diagnostics`. */
internal object BundleBuilder {
    private const val DIRECTORY = "diagnostics"

    // The directory BorealisApplication gives the rotating log files.
    private const val LOG_DIRECTORY = "logs"

    fun build(
        context: Context,
        sections: Collection<DiagnosticSection>,
        now: Instant = Instant.now(),
        flushLogs: () -> Unit = { DiagnosticBundleWriter.flushOpenSinks() },
    ): File {
        val dir = File(context.cacheDir, DIRECTORY)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
        BundleFiles.pruneOld(dir)
        val target = File(dir, BundleFiles.newName(now))
        val unfinished = File(dir, BundleFiles.unfinishedName(target.name))
        try {
            val writer =
                DiagnosticBundleWriter(
                    logDir = File(context.noBackupFilesDir, LOG_DIRECTORY),
                    sections = sections,
                    redact = BorealisLog::redact,
                    flushLogs = flushLogs,
                )
            unfinished.outputStream().buffered().use {
                writer.write(
                    ManifestCollector.collect(context, now),
                    DecoderCollector.collect(SystemDecoderSource(context)),
                    it,
                )
            }
            check(unfinished.renameTo(target)) { "Cannot move the finished bundle into place" }
        } finally {
            unfinished.delete()
        }
        return target
    }
}
