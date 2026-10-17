package io.github.magalz.borealis.library.testing

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.settings.privacy.DiagnosticsExport
import io.github.magalz.borealis.settings.privacy.PrivacyModule
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An export that answers [result]; a test sets it before it chooses Export. */
@Singleton
class FakeDiagnosticsExport @Inject constructor() : DiagnosticsExport {
    var result: ExportResult = ExportResult.Failed

    override suspend fun export(): ExportResult = result
}

/** What the real exporter answers on a TV: the bundle, saved to Downloads, in [dir]. */
fun savedBundle(dir: File): ExportResult.Saved {
    val bundle =
        File(dir, "borealis-diagnostics-20261016-224700.zip").apply { writeBytes(ByteArray(2048)) }
    return ExportResult.Saved(
        location = "/sdcard/Download/${bundle.name}",
        adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
        chooser = Intent(),
        bundle = bundle,
    )
}

/**
 * Replaces the diagnostic exporter of the app, which saves to the public Downloads folder, with
 * [FakeDiagnosticsExport] in the Library's Hilt tests.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PrivacyModule::class])
object FakeDiagnosticsExportModule {
    @Provides fun diagnosticsExport(fake: FakeDiagnosticsExport): DiagnosticsExport = fake
}
