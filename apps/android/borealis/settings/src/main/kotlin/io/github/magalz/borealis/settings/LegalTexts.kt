package io.github.magalz.borealis.settings

import java.io.IOException
import java.io.InputStream

/** The `NOTICE` and `LICENSE` texts the build bundles as assets under `legal/`. */
object LegalTexts {
    private val FILES = listOf("NOTICE", "LICENSE")
    private const val SEPARATOR = "\n\n----------------------------------------\n\n"

    /** The texts joined under their file names, or null when the build bundles neither file. */
    fun load(open: (String) -> InputStream): String? =
        FILES.mapNotNull { name -> read(open, name)?.let { "$name\n\n$it" } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(SEPARATOR)

    private fun read(open: (String) -> InputStream, name: String): String? =
        try {
            open("legal/$name").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: IOException) {
            null
        }
}
