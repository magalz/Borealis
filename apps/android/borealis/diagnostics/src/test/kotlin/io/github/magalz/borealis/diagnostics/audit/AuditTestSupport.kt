package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import java.io.File

fun event(
    at: Long = 1_700_000_000_000L,
    type: AuditType = AuditType.PAIRING_START,
    host: String? = "den-pc",
    detail: String? = "detail",
    outcome: AuditOutcome = AuditOutcome.SUCCESS,
) = AuditEvent(at, type, outcome, host, detail, "1.0.0")

const val MINUTE = 60_000L
const val DAY = 24 * 60 * MINUTE

/** One row of `core/tests/fixtures/redaction/secret-canaries.tsv`. */
data class Canary(val id: String, val input: String, val secret: String)

/** The shared secret-canary corpus, located through `borealis.fixtures.dir`. */
object RedactionCorpus {
    fun canaries(): List<Canary> {
        val root =
            checkNotNull(System.getProperty("borealis.fixtures.dir")) {
                "System property borealis.fixtures.dir is not set"
            }
        val file = File(root, "redaction/secret-canaries.tsv")
        check(file.isFile) { "Fixture not found: $file" }
        return file
            .readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .drop(1)
            .map { line ->
                val cells = line.split('\t')
                check(cells.size == 4) { "Malformed canary row: ${cells.firstOrNull()}" }
                Canary(cells[0], cells[1], cells[3])
            }
    }
}
