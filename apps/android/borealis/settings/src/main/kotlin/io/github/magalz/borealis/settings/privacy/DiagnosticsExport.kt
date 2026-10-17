package io.github.magalz.borealis.settings.privacy

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.diagnostics.bundle.DiagnosticsExporter
import io.github.magalz.borealis.diagnostics.bundle.ExportResult

/**
 * Builds the diagnostic bundle and delivers it. It is the seam between the pane and
 * [DiagnosticsExporter], which a test cannot build because its constructor is internal to the
 * diagnostics module.
 */
fun interface DiagnosticsExport {
    /** Never throws, apart from cancellation. */
    suspend fun export(): ExportResult
}

@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {
    @Provides
    fun diagnosticsExport(exporter: DiagnosticsExporter): DiagnosticsExport =
        DiagnosticsExport(exporter::export)
}
