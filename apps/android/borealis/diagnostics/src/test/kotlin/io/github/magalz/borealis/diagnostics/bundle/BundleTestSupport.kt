package io.github.magalz.borealis.diagnostics.bundle

import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipInputStream

internal val SAMPLE_MANIFEST =
    BundleManifest(
        app = AppInfo("1.2.3", 7L, "abc1234", "release"),
        device = DeviceInfo("15", 35, "Acme", "Phone 1", listOf("arm64-v8a")),
        createdUtc = "2026-10-14T12:34:56Z",
        logSettings = LogSettingsInfo(verbose = false, maskAddresses = false),
    )

internal val SAMPLE_DECODERS =
    DecodersReport(
        decoders = listOf(DecoderEntry("c2.acme.hevc.decoder", "video/hevc", true, listOf("Main"))),
        displayHdrTypes = listOf("HDR10"),
    )

internal class TextSection(
    override val fileName: String,
    private val content: String = "",
    private val failure: RuntimeException? = null,
) : DiagnosticSection {
    override fun writeTo(out: OutputStream) {
        out.write(content.toByteArray(Charsets.UTF_8))
        failure?.let { throw it }
    }
}

/** The entries of a zip, in order, with their UTF-8 content. */
internal fun unzip(bytes: ByteArray): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }
            .forEach { entries[it.name] = zip.readBytes().decodeToString() }
    }
    return entries
}

internal fun File.writeLines(vararg lines: String) = writeText(lines.joinToString("") { it + "\n" })
