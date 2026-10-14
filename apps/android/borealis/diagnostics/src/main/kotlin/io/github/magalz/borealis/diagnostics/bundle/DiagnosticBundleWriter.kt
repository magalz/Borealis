package io.github.magalz.borealis.diagnostics.bundle

import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.File
import java.io.OutputStream

/**
 * Writes the diagnostic bundle zip. Every log and section line goes through [redact] before it is
 * written.
 */
class DiagnosticBundleWriter(
    private val logDir: File,
    private val sections: Collection<DiagnosticSection>,
    private val redact: (String) -> String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    /** Writes the zip to [out] and leaves [out] open. */
    fun write(manifest: BundleManifest, decoders: DecodersReport, out: OutputStream): Unit = TODO()

    companion object {
        const val DEFAULT_MAX_BYTES = 16L * 1024 * 1024
        const val MANIFEST = "manifest.json"
        const val DECODERS = "decoders.json"
        const val LOGS_DIR = "logs"
    }
}
