package io.github.magalz.borealis.settings

import java.io.IOException
import java.io.InputStream

/**
 * The `NOTICE` and `LICENSE` texts and the licence of every bundled font, which the build bundles
 * as assets under `legal/` (the names are those of `LegalAssetsTask`).
 */
object LegalTexts {
    /** An asset under `legal/` and the heading that it is shown under. */
    private class LegalFile(val asset: String, val heading: String)

    private val FILES =
        listOf(
            LegalFile("NOTICE", "NOTICE"),
            LegalFile("LICENSE", "LICENSE"),
            LegalFile("ibm-plex-OFL.txt", "IBM Plex Sans and Mono: OFL.txt"),
            LegalFile("atkinson-next-OFL.txt", "Atkinson Hyperlegible Next: OFL.txt"),
            LegalFile("atkinson-mono-OFL.txt", "Atkinson Hyperlegible Mono: OFL.txt"),
            LegalFile("luciole-LICENSE.txt", "Luciole: LICENSE.txt"),
            LegalFile("lexend-OFL.txt", "Lexend: OFL.txt"),
            LegalFile("andika-OFL.txt", "Andika: OFL.txt"),
            LegalFile("b612-OFL.txt", "B612 and B612 Mono: OFL.txt"),
        )
    private const val SEPARATOR = "\n\n----------------------------------------\n\n"

    /** The texts joined under their headings, or null when the build bundles none of the files. */
    fun load(open: (String) -> InputStream): String? =
        FILES.mapNotNull { file -> read(open, file.asset)?.let { "${file.heading}\n\n$it" } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(SEPARATOR)

    private fun read(open: (String) -> InputStream, name: String): String? =
        try {
            open("legal/$name").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: IOException) {
            null
        }
}
