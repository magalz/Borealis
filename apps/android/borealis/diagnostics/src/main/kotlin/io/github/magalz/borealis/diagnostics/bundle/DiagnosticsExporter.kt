package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.diagnostics.R
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports the diagnostic bundle for a screen that has no framework dialogs of its own. A phone gets
 * a file it can share ([ExportResult.Share]); a TV, or a phone with no receiver, gets it saved to
 * Downloads ([ExportResult.Saved], with the `adb pull` command). The content is the redacted bundle
 * of ADR 0050; this class only decides what to do with it.
 */
class DiagnosticsExporter
internal constructor(
    private val context: Context,
    private val sections: () -> Set<DiagnosticSection>,
    private val switches: LogSwitches,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uriFor: (File) -> Uri = {
        FileProvider.getUriForFile(context, fileProviderAuthority(context.packageName), it)
    },
    private val flushLogs: () -> Unit = { DiagnosticBundleWriter.flushOpenSinks() },
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        sections: Set<@JvmSuppressWildcards DiagnosticSection>,
        switches: LogSwitches,
    ) : this(context, { sections }, switches)

    /**
     * Builds and delivers the bundle off the main thread; never throws, apart from cancellation.
     */
    suspend fun export(): ExportResult =
        withContext(ioDispatcher) {
            runCatching { newFlow().run() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    BorealisLog.e(LogTag.APP, "diagnostic export failed", error)
                    ExportResult.Failed
                }
        }

    private fun newFlow() =
        ExportFlow(
            chooserTitle = context.getString(R.string.borealis_export_chooser_title),
            buildBundle = {
                BundleBuilder.build(context, sections(), switches, flushLogs = flushLogs)
            },
            uriFor = uriFor,
            canShare = { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() },
            isTelevision = isTelevision(context),
            store = defaultBundleStore(context),
        )
}
