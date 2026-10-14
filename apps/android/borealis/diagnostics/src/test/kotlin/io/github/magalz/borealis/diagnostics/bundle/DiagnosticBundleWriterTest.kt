package io.github.magalz.borealis.diagnostics.bundle

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.Redactor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticBundleWriterTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var logDir: File

    @Before
    fun setUp() {
        logDir = File(temp.root, "logs").apply { mkdirs() }
    }

    private fun bundle(
        sections: List<DiagnosticSection> = emptyList(),
        maxBytes: Long = DiagnosticBundleWriter.DEFAULT_MAX_BYTES,
        redact: (String) -> String = { it },
        flushLogs: () -> Unit = {},
    ): Map<String, String> {
        val out = ByteArrayOutputStream()
        DiagnosticBundleWriter(logDir, sections, redact, maxBytes, flushLogs)
            .write(SAMPLE_MANIFEST, SAMPLE_DECODERS, out)
        return unzip(out.toByteArray())
    }

    private fun manifestOf(entries: Map<String, String>): BundleManifest =
        Json.decodeFromString(entries.getValue("manifest.json"))

    private fun sizeOf(entries: Map<String, String>): Long =
        entries.values.sumOf { it.toByteArray().size.toLong() }

    @Test
    fun `an empty bundle holds the manifest and the decoders`() {
        val entries = bundle()

        assertThat(entries.keys).containsExactly("manifest.json", "decoders.json").inOrder()
        assertThat(manifestOf(entries)).isEqualTo(SAMPLE_MANIFEST)
        assertThat(Json.decodeFromString<DecodersReport>(entries.getValue("decoders.json")))
            .isEqualTo(SAMPLE_DECODERS)
    }

    @Test
    fun `logs and crash reports go under logs and other files are left out`() {
        File(logDir, "borealis.log").writeLines("a")
        File(logDir, "borealis.log.1").writeLines("b")
        File(logDir, "borealis.log.4").writeLines("c")
        File(logDir, "crash-20261014-120000-000.txt").writeLines("d")
        File(logDir, "audit.db").writeLines("not a log")
        File(logDir, "borealis.log.bak").writeLines("not a log")
        File(logDir, "notes.txt").writeLines("not a log")

        val entries = bundle()

        assertThat(entries.keys)
            .containsExactly(
                "manifest.json",
                "decoders.json",
                "logs/crash-20261014-120000-000.txt",
                "logs/borealis.log",
                "logs/borealis.log.1",
                "logs/borealis.log.4",
            )
        assertThat(entries["logs/borealis.log"]).isEqualTo("a\n")
        assertThat(entries["logs/crash-20261014-120000-000.txt"]).isEqualTo("d\n")
    }

    @Test
    fun `a missing log directory is fine`() {
        logDir.deleteRecursively()

        assertThat(bundle().keys).containsExactly("manifest.json", "decoders.json")
    }

    @Test
    fun `logs are redacted again line by line with the current redactor`() {
        File(logDir, "borealis.log")
            .writeLines("pairing pin=4821 ok", "connect to 192.0.2.5 failed", "", "last")
        val seen = mutableListOf<String>()

        val masked =
            bundle(
                redact = { line ->
                    seen += line
                    Redactor(maskAddresses = true).redact(line)
                }
            )

        assertThat(masked.getValue("logs/borealis.log"))
            .isEqualTo("pairing pin=[REDACTED] ok\nconnect to [IP] failed\n\nlast\n")
        assertThat(seen)
            .containsExactly("pairing pin=4821 ok", "connect to 192.0.2.5 failed", "", "last")

        val plain = bundle(redact = Redactor(maskAddresses = false)::redact)

        assertThat(plain.getValue("logs/borealis.log"))
            .isEqualTo("pairing pin=[REDACTED] ok\nconnect to 192.0.2.5 failed\n\nlast\n")
    }

    @Test
    fun `a last line without a newline is kept and redacted`() {
        File(logDir, "borealis.log").writeText("one\npin=4821")

        val entries = bundle(redact = Redactor()::redact)

        assertThat(entries.getValue("logs/borealis.log")).isEqualTo("one\npin=[REDACTED]\n")
    }

    @Test
    fun `each section becomes one entry with its content redacted`() {
        val entries =
            bundle(
                sections =
                    listOf(
                        TextSection("audit.jsonl", "{\"detail\":\"pin=4821\"}\n"),
                        TextSection("extra.txt", "hello\n"),
                    ),
                redact = Redactor()::redact,
            )

        assertThat(entries.keys)
            .containsExactly("manifest.json", "decoders.json", "audit.jsonl", "extra.txt")
        assertThat(entries["audit.jsonl"]).isEqualTo("{\"detail\":\"pin=[REDACTED]\"}\n")
        assertThat(entries["extra.txt"]).isEqualTo("hello\n")
    }

    @Test
    fun `sections with unsafe or colliding names are skipped`() {
        val entries =
            bundle(
                sections =
                    listOf(
                        TextSection("../evil.txt", "x"),
                        TextSection("logs/evil.txt", "x"),
                        TextSection(".hidden", "x"),
                        TextSection("manifest.json", "x"),
                        TextSection("decoders.json", "x"),
                        TextSection("logs", "x"),
                        TextSection("good.txt", "first\n"),
                        TextSection("good.txt", "second\n"),
                    )
            )

        assertThat(entries.keys).containsExactly("manifest.json", "decoders.json", "good.txt")
        assertThat(entries["good.txt"]).isEqualTo("first\n")
        assertThat(manifestOf(entries)).isEqualTo(SAMPLE_MANIFEST)
    }

    @Test
    fun `a section that fails is left out and recorded`() {
        val entries =
            bundle(
                sections =
                    listOf(
                        TextSection("broken.txt", "partial", IllegalStateException("boom")),
                        TextSection("fine.txt", "ok"),
                    )
            )

        assertThat(entries.keys).containsExactly("manifest.json", "decoders.json", "fine.txt")
        assertThat(manifestOf(entries).truncated)
            .containsExactly(TruncatedEntry("broken.txt", "section-error", 0, 0))
    }

    @Test
    fun `a full bundle stays within the size bound and drops the oldest logs first`() {
        val names =
            listOf(
                "borealis.log",
                "borealis.log.1",
                "borealis.log.2",
                "borealis.log.3",
                "borealis.log.4",
            )
        for (name in names) {
            File(logDir, name).writeLines(*Array(10) { "$name line $it ".padEnd(99, '.') })
        }
        val maxBytes = 4_000L

        val entries = bundle(maxBytes = maxBytes)

        assertThat(sizeOf(entries)).isAtMost(maxBytes)
        assertThat(entries["logs/borealis.log"]).isEqualTo(File(logDir, "borealis.log").readText())
        assertThat(entries).doesNotContainKey("logs/borealis.log.4")
        val truncated = manifestOf(entries).truncated.associateBy { it.name }
        val oldest = truncated.getValue("logs/borealis.log.4")
        assertThat(oldest.reason).isEqualTo("size-limit")
        assertThat(oldest.keptBytes).isEqualTo(0)
        assertThat(oldest.originalBytes).isEqualTo(File(logDir, "borealis.log.4").length())
        // A file that was cut keeps its newest lines, from a line boundary.
        for (entry in truncated.values.filter { it.keptBytes > 0 }) {
            val file = entry.name.removePrefix("logs/")
            val kept = entries.getValue(entry.name)
            assertThat(File(logDir, file).readText()).endsWith(kept)
            assertThat(kept).startsWith(file)
            assertThat(kept.toByteArray().size.toLong()).isEqualTo(entry.keptBytes)
        }
        // No log file disappears without a record in the manifest.
        for (name in names) {
            assertThat("logs/$name" in entries || "logs/$name" in truncated).isTrue()
        }
    }

    @Test
    fun `crash reports are kept before rotated logs when the bound bites`() {
        File(logDir, "crash-20261014-100000-000.txt").writeLines("old crash")
        File(logDir, "crash-20261014-110000-000.txt").writeLines("new crash")
        File(logDir, "borealis.log").writeLines(*Array(40) { "filler line ".padEnd(99, 'x') })

        val entries = bundle(maxBytes = 3_000)

        assertThat(entries.keys)
            .containsAtLeast(
                "logs/crash-20261014-110000-000.txt",
                "logs/crash-20261014-100000-000.txt",
            )
        assertThat(sizeOf(entries)).isAtMost(3_000)
        assertThat(manifestOf(entries).truncated.map { it.name }).contains("logs/borealis.log")
    }

    @Test
    fun `a section over the size bound is left out and recorded`() {
        val entries =
            bundle(
                sections =
                    listOf(TextSection("big.txt", "x".repeat(5_000)), TextSection("ok.txt", "ok")),
                maxBytes = 4_000,
            )

        assertThat(entries.keys).containsExactly("manifest.json", "decoders.json", "ok.txt")
        val truncated = manifestOf(entries).truncated.single()
        assertThat(truncated.name).isEqualTo("big.txt")
        assertThat(truncated.reason).isEqualTo("size-limit")
    }

    @Test
    fun `pending log lines are flushed once before the log files are read`() {
        var flushes = 0

        val entries =
            bundle(
                flushLogs = {
                    flushes++
                    File(logDir, "borealis.log").writeLines("written by the flush")
                }
            )

        assertThat(flushes).isEqualTo(1)
        assertThat(entries["logs/borealis.log"]).isEqualTo("written by the flush\n")
    }

    @Test
    fun `a flush that fails does not stop the bundle`() {
        File(logDir, "borealis.log").writeLines("kept")

        val entries = bundle(flushLogs = { throw IllegalStateException("queue stuck") })

        assertThat(entries["logs/borealis.log"]).isEqualTo("kept\n")
    }

    @Test
    fun `nothing is truncated when everything fits`() {
        File(logDir, "borealis.log").writeLines("one")

        assertThat(manifestOf(bundle()).truncated).isEmpty()
    }
}
