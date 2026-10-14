package io.github.magalz.borealis.diagnostics

import java.io.OutputStream

/**
 * One file in the diagnostic bundle (ADR 0050), contributed by the module that owns the data, such
 * as the audit trail. The bundle writes each section under [fileName] at the root of the zip.
 */
interface DiagnosticSection {
    /** File name inside the zip, for example `audit.jsonl`; it must satisfy [isSafeFileName]. */
    val fileName: String

    /** Writes the section's content, already redacted, to [out]. Must not close [out]. */
    fun writeTo(out: OutputStream)

    companion object {
        /** A plain file name: no directories, no leading dot, at most 64 characters. */
        fun isSafeFileName(name: String): Boolean = false
    }
}
