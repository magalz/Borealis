package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.logging.RotatingFileSink
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BundleBuilderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logDir
        get() = File(context.noBackupFilesDir, "logs")

    @Before
    fun setUp() {
        logDir.deleteRecursively()
        File(context.cacheDir, "diagnostics").deleteRecursively()
        BorealisLog.initialize(emptyList(), redactor = Redactor(maskAddresses = true))
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    private fun entriesOf(zip: File) = unzip(zip.readBytes())

    @Test
    fun `builds the zip in the cache directory with the current redaction`() {
        logDir.mkdirs()
        File(logDir, "borealis.log").writeLines("pairing pin=4821", "host 192.0.2.5 answered")

        val zip =
            BundleBuilder.build(
                context,
                listOf(TextSection("audit.jsonl", "{\"detail\":\"already redacted\"}\n")),
                Instant.parse("2026-10-14T12:34:56Z"),
            )

        assertThat(zip.parentFile).isEqualTo(File(context.cacheDir, "diagnostics"))
        assertThat(zip.name).isEqualTo("borealis-diagnostics-20261014-123456.zip")
        val entries = entriesOf(zip)
        assertThat(entries.keys)
            .containsExactly("manifest.json", "decoders.json", "logs/borealis.log", "audit.jsonl")
        assertThat(entries["logs/borealis.log"])
            .isEqualTo("pairing pin=[REDACTED]\nhost [IP] answered\n")
        assertThat(entries["audit.jsonl"]).isEqualTo("{\"detail\":\"already redacted\"}\n")
        assertThat(
                Json.decodeFromString<BundleManifest>(entries.getValue("manifest.json")).createdUtc
            )
            .isEqualTo("2026-10-14T12:34:56Z")
        assertThat(entries.getValue("decoders.json")).contains("displayHdrTypes")
    }

    @Test
    fun `by default the open file sinks are drained before the logs are read`() {
        // The writer thread takes 300 ms per record, so the line is only on disk if we wait.
        val sink = RotatingFileSink(logDir, writeGate = { Thread.sleep(300) })
        try {
            sink.write(LogRecord(1_700_000_000_000L, LogLevel.INFO, LogTag.APP, "queued line"))

            val zip =
                BundleBuilder.build(context, emptyList(), Instant.parse("2026-10-14T12:34:56Z"))

            assertThat(entriesOf(zip).getValue("logs/borealis.log")).contains("queued line")
        } finally {
            sink.close()
        }
    }

    @Test
    fun `the log flush runs before the log files are read`() {
        val zip =
            BundleBuilder.build(
                context,
                emptyList(),
                Instant.parse("2026-10-14T12:34:56Z"),
                flushLogs = {
                    logDir.mkdirs()
                    File(logDir, "borealis.log").writeLines("queued line")
                },
            )

        assertThat(entriesOf(zip)["logs/borealis.log"]).isEqualTo("queued line\n")
    }

    @Test
    fun `a new bundle replaces the older ones and leaves no unfinished file`() {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        File(dir, "borealis-diagnostics-20250101-000000.zip").writeText("old")
        File(dir, "borealis-diagnostics-20250101-000001.zip.part").writeText("unfinished")

        val zip = BundleBuilder.build(context, emptyList(), Instant.parse("2026-10-14T12:34:56Z"))

        assertThat(dir.list()!!.toList()).containsExactly(zip.name)
    }
}
