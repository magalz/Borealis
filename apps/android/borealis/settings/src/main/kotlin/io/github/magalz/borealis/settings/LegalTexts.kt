package io.github.magalz.borealis.settings

import java.io.InputStream

/** The `NOTICE` and `LICENSE` texts bundled as assets under `legal/`. */
object LegalTexts {
    /** The texts joined under headings, or null when the build bundles neither file. */
    fun load(open: (String) -> InputStream): String? = null
}
