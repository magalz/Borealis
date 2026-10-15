package io.github.magalz.borealis.diagnostics.bundle

import java.io.File

/** One row of `core/tests/fixtures/redaction/secret-canaries.tsv` (as in `:borealis:logging`). */
internal data class Canary(val id: String, val input: String, val secret: String)

/** The shared secret-canary corpus, located through the `borealis.fixtures.dir` system property. */
internal object CanaryCorpus {
    fun canaries(): List<Canary> {
        val root =
            checkNotNull(System.getProperty("borealis.fixtures.dir")) {
                "System property borealis.fixtures.dir is not set (borealis.android.library plugin)."
            }
        val file = File(root, "redaction/secret-canaries.tsv")
        check(file.isFile) { "Fixture not found: ${file.path}" }
        return file
            .readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .drop(1) // header
            .map { line ->
                val cells = line.split('\t')
                check(cells.size == 4) { "Malformed canary row: ${cells.firstOrNull()}" }
                Canary(cells[0], cells[1], cells[3])
            }
    }
}
