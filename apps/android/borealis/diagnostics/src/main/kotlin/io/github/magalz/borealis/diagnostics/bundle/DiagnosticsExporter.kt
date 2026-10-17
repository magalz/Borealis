package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.LogSwitches
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Skeleton: the behaviour follows in the next commit. */
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
    suspend fun export(): ExportResult = ExportResult.Failed
}
