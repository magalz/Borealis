package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotatingFileSinkTest {
    @get:Rule val temp = TemporaryFolder()

    private val logDir
        get() = File(temp.root, "logs")

    private fun record(message: String, millis: Long = 1_700_000_000_000L) =
        LogRecord(millis, LogLevel.INFO, LogTag.APP, message)

    private fun logFiles() =
        logDir.listFiles { f -> f.name.startsWith("borealis.log") }.orEmpty().sortedBy { it.name }

    private fun allLines() = logFiles().flatMap { it.readLines(Charsets.UTF_8) }

    @Test
    fun `writes one utf-8 line per record and creates the directory`() {
        val sink = RotatingFileSink(logDir)

        sink.write(record("héllo ✓ wörld"))
        sink.write(record("second"))

        val file = File(logDir, "borealis.log")
        assertThat(file.readText(Charsets.UTF_8))
            .isEqualTo(
                LogFormatter.format(record("héllo ✓ wörld")) +
                    "\n" +
                    LogFormatter.format(record("second")) +
                    "\n"
            )
    }

    @Test
    fun `lines are readable without closing the sink`() {
        val sink = RotatingFileSink(logDir)

        sink.write(record("flushed"))

        assertThat(allLines()).hasSize(1)
    }

    @Test
    fun `rolls over when the size limit would be exceeded`() {
        val sink = RotatingFileSink(logDir, maxFiles = 5, maxFileBytes = 300)

        repeat(20) { sink.write(record("message number $it")) }

        val names = logFiles().map { it.name }
        assertThat(names).contains("borealis.log.1")
        assertThat(logFiles().all { it.length() <= 300 }).isTrue()
        assertThat(File(logDir, "borealis.log").readLines().last()).endsWith("message number 19")
    }

    @Test
    fun `keeps at most max files and deletes the oldest`() {
        val sink = RotatingFileSink(logDir, maxFiles = 3, maxFileBytes = 200)

        repeat(100) { sink.write(record("message number $it")) }

        assertThat(logFiles().map { it.name })
            .containsExactly("borealis.log", "borealis.log.1", "borealis.log.2")
        val text = allLines().joinToString("\n")
        assertThat(text).doesNotContain("message number 0 ")
        assertThat(text).doesNotContain("message number 0\n")
        assertThat(text).contains("message number 99")
        // Newest records live in the current file, older ones in higher-numbered files.
        val numbers =
            listOf("borealis.log", "borealis.log.1", "borealis.log.2").map { name ->
                File(logDir, name).readLines().map { it.substringAfterLast(' ').toInt() }
            }
        assertThat(numbers[0].min()).isGreaterThan(numbers[1].max())
        assertThat(numbers[1].min()).isGreaterThan(numbers[2].max())
    }

    @Test
    fun `defaults are five files of one megabyte`() {
        assertThat(RotatingFileSink.DEFAULT_MAX_FILES).isEqualTo(5)
        assertThat(RotatingFileSink.DEFAULT_MAX_FILE_BYTES).isEqualTo(1024L * 1024L)
    }

    @Test
    fun `a record larger than the limit is still written whole`() {
        val sink = RotatingFileSink(logDir, maxFiles = 2, maxFileBytes = 100)

        sink.write(record("x".repeat(500)))
        sink.write(record("after"))

        assertThat(allLines().any { it.endsWith("x".repeat(500)) }).isTrue()
        assertThat(allLines().any { it.endsWith("after") }).isTrue()
    }

    @Test
    fun `appends to an existing log across instances`() {
        RotatingFileSink(logDir).apply {
            write(record("first run"))
            close()
        }

        RotatingFileSink(logDir).write(record("second run"))

        assertThat(allLines().map { it.substringAfter("APP ") })
            .containsExactly("first run", "second run")
            .inOrder()
    }

    @Test
    fun `rotation does not touch other files such as crash reports`() {
        logDir.mkdirs()
        val crash = File(logDir, "crash-20260101-000000-000.txt").apply { writeText("report") }
        val sink = RotatingFileSink(logDir, maxFiles = 2, maxFileBytes = 100)

        repeat(30) { sink.write(record("message number $it")) }

        assertThat(crash.readText()).isEqualTo("report")
    }

    @Test
    fun `concurrent writers lose no lines and never interleave`() {
        val sink = RotatingFileSink(logDir, maxFiles = 10_000, maxFileBytes = 2_000)
        val threads = 8
        val perThread = 250
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { n -> sink.write(record("thread $t record $n end")) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

        val lines = allLines()
        assertThat(lines).hasSize(threads * perThread)
        assertThat(lines.all { Regex("""^\S+ INFO APP thread \d record \d+ end$""").matches(it) })
            .isTrue()
        assertThat(lines.toSet()).hasSize(threads * perThread)
    }

    @Test
    fun `close is idempotent`() {
        val sink = RotatingFileSink(logDir)
        sink.write(record("x"))

        sink.close()
        sink.close()

        assertThat(allLines()).hasSize(1)
    }

    @Test
    fun `writes after close are ignored and do not reopen the file`() {
        val sink = RotatingFileSink(logDir)
        sink.write(record("before"))
        sink.close()

        sink.write(record("after"))

        assertThat(allLines().map { it.substringAfter("APP ") }).containsExactly("before")
    }
}
