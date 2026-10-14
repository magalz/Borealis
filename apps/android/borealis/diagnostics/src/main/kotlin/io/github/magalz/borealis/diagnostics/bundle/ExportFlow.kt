package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.File
import java.time.Instant

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
    fun run(): ExportResult = TODO()
}

/** Naming and housekeeping of the bundle files. */
internal object BundleFiles {
    const val PREFIX = "borealis-diagnostics-"

    fun newName(now: Instant): String = TODO()

    /** Deletes earlier bundles (and unfinished ones) in [dir]. */
    fun pruneOld(dir: File): Unit = TODO()
}

/** Builds the bundle zip in `cacheDir/diagnostics`. */
internal object BundleBuilder {
    fun build(
        context: Context,
        sections: Collection<DiagnosticSection>,
        now: Instant = Instant.now(),
    ): File = TODO()
}
