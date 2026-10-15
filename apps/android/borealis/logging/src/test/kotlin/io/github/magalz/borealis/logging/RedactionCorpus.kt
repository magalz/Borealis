package io.github.magalz.borealis.logging

import java.io.File

/** One row of `core/tests/fixtures/redaction/secret-canaries.tsv`. */
data class Canary(val id: String, val input: String, val expected: String, val secret: String)

/** The shared secret-canary corpus, located through `borealis.fixtures.dir`. */
object RedactionCorpus {
    private const val DIR_PROPERTY = "borealis.fixtures.dir"
    private const val FILE = "redaction/secret-canaries.tsv"

    fun canaries(): List<Canary> {
        val root =
            checkNotNull(System.getProperty(DIR_PROPERTY)) {
                "System property $DIR_PROPERTY is not set: unit-test tasks get it from the " +
                    "borealis.android.library convention plugin (build-logic)."
            }
        val file = File(root, FILE)
        check(file.isFile) { "Fixture not found: $FILE (looked in $root)" }
        return file
            .readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .drop(1) // header
            .map { line ->
                val cells = line.split('\t')
                check(cells.size == 4) { "Malformed canary row: ${cells.firstOrNull()}" }
                Canary(cells[0], cells[1], cells[2], cells[3])
            }
    }
}
