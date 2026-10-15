package io.github.magalz.borealis.diagnostics.bundle

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogStorage
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What the settings screen does with a finished bundle. */
sealed interface ExportResult {
    /** Hand [chooser] to `startActivity`. */
    class Share(val chooser: Intent) : ExportResult

    /**
     * The zip was saved at [location] (a TV, or a phone with no receiver); show how to fetch it
     * with [adbPullCommand]. [chooser] shares it anyway, for a user with a mail or file app.
     */
    class Saved(
        val location: String,
        val adbPullCommand: String,
        val chooser: Intent,
        val bundle: File,
    ) : ExportResult

    /** The bundle could not be built or saved; the cause is in the log. */
    data object Failed : ExportResult
}

/**
 * Builds the bundle, then shares it. On a TV, or when no app can receive the zip, it saves it
 * through [store] instead: on a TV an app such as a video player often accepts any `ACTION_SEND`,
 * so a receiver there does not mean the user can send the file anywhere useful.
 */
internal class ExportFlow(
    private val chooserTitle: CharSequence,
    private val buildBundle: () -> File,
    private val uriFor: (File) -> Uri,
    private val canShare: (Intent) -> Boolean,
    private val isTelevision: Boolean,
    private val store: BundleStore,
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
        val chooser =
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!isTelevision && canShare(send)) return ExportResult.Share(chooser)
        val saved = store.save(zip)
        return ExportResult.Saved(saved.location, saved.adbPullCommand, chooser, zip)
    }

    companion object {
        const val ZIP_MIME_TYPE = "application/zip"

        /** Sub-directory of `getExternalFilesDir` that holds the saved bundle (API 28). */
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
                    logDir = File(context.noBackupFilesDir, LogStorage.DIRECTORY),
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
