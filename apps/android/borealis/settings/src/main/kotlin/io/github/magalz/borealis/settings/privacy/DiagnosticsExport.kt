package io.github.magalz.borealis.settings.privacy

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.diagnostics.bundle.DiagnosticsExporter
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.logging.LogSwitches

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
    /**
     * Builds the exporter here, not through its own `@Inject` constructor: a Kotlin `Set` parameter
     * becomes `Set<? extends DiagnosticSection>` in Java, which Dagger does not match with the
     * multibinding, so the wildcards are suppressed on this parameter.
     */
    @Provides
    fun diagnosticsExport(
        @ApplicationContext context: Context,
        sections: Set<@JvmSuppressWildcards DiagnosticSection>,
        switches: LogSwitches,
    ): DiagnosticsExport =
        DiagnosticsExport(DiagnosticsExporter(context, sections, switches)::export)
}
