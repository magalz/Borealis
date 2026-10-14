package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotatingFileSinkAsyncTest {
    @get:Rule val temp = TemporaryFolder()

    private val logDir
        get() = File(temp.root, "logs")

    private fun record(message: String) =
        LogRecord(1_700_000_000_000L, LogLevel.INFO, LogTag.APP, message)

    private fun logFiles() =
        logDir.listFiles { f -> f.name.startsWith("borealis.log") }.orEmpty().toList()

    /** Lines of every file, oldest file first. */
    private fun linesOldestFirst(): List<String> =
        logFiles()
            .sortedByDescending { it.name.substringAfterLast('.').toIntOrNull() ?: 0 }
            .flatMap { it.readLines(Charsets.UTF_8) }

    private fun messages() = linesOldestFirst().map { it.substringAfter("APP ") }

    @Test
    fun `write returns while the writer is stalled and the lines land after it resumes`() {
        val gate = CountDownLatch(1)
        val sink = RotatingFileSink(logDir, writeGate = { gate.await() })

        repeat(3) { sink.write(record("pending $it")) }

        assertThat(linesOldestFirst()).isEmpty()
        gate.countDown()
        assertThat(sink.flush()).isTrue()
        assertThat(messages()).containsExactly("pending 0", "pending 1", "pending 2").inOrder()
    }

    private fun droppedIn(lines: List<String>) =
        lines.filter { it.endsWith(" records dropped") }.sumOf { it.substringBefore(' ').toInt() }

    @Test
    fun `a full queue drops new records and accounts for each of them in summaries`() {
        val gate = CountDownLatch(1)
        val sink = RotatingFileSink(logDir, queueCapacity = 4, writeGate = { gate.await() })
        repeat(20) { sink.write(record("message $it")) }

        gate.countDown()
        assertThat(sink.flush()).isTrue()
        sink.write(record("after"))
        assertThat(sink.flush()).isTrue()

        val lines = messages()
        val kept = lines.filter { it.startsWith("message ") }
        assertThat(droppedIn(lines)).isGreaterThan(0)
        assertThat(kept.size + droppedIn(lines)).isEqualTo(20)
        assertThat(kept.map { it.substringAfter(' ').toInt() }).isInStrictOrder()
        assertThat(lines.last()).isEqualTo("after")
    }

    @Test
    fun `flush writes the drop summary even when no record follows`() {
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val sink =
            RotatingFileSink(
                logDir,
                queueCapacity = 2,
                writeGate = {
                    entered.countDown()
                    gate.await()
                },
            )
        sink.write(record("in flight"))
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        repeat(9) { sink.write(record("message $it")) } // two fit, seven are dropped

        gate.countDown()
        assertThat(sink.flush()).isTrue()
        assertThat(sink.flush()).isTrue()

        assertThat(messages().filter { it.endsWith("records dropped") })
            .containsExactly("7 records dropped")
        assertThat(messages().count { it.startsWith("message ") }).isEqualTo(2)
    }

    @Test
    fun `flush gives up after its timeout while the writer is stalled`() {
        val gate = CountDownLatch(1)
        val sink = RotatingFileSink(logDir, writeGate = { gate.await() })
        sink.write(record("stuck"))

        val started = System.nanoTime()
        val flushed = sink.flush(timeoutMillis = 100)
        val tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertThat(flushed).isFalse()
        assertThat(tookMillis).isLessThan(5_000L)
        gate.countDown()
        assertThat(sink.flush()).isTrue()
        assertThat(messages()).containsExactly("stuck")
    }

    @Test
    fun `flush on an idle sink returns at once`() {
        val sink = RotatingFileSink(logDir)

        assertThat(sink.flush(timeoutMillis = 100)).isTrue()
    }

    @Test
    fun `close drains the queued records before returning`() {
        val gate = CountDownLatch(1)
        val sink = RotatingFileSink(logDir, writeGate = { gate.await() })
        repeat(3) { sink.write(record("queued $it")) }

        gate.countDown()
        sink.close()

        assertThat(messages()).containsExactly("queued 0", "queued 1", "queued 2").inOrder()
    }

    @Test
    fun `flushAll drains every open sink`() {
        val gate = CountDownLatch(1)
        val sink = RotatingFileSink(logDir, writeGate = { gate.await() })
        sink.write(record("before the crash"))
        Thread {
            Thread.sleep(100)
            gate.countDown()
        }
            .start()

        RotatingFileSink.flushAll(timeoutMillis = 5_000)

        assertThat(messages()).containsExactly("before the crash")
        sink.close()
    }

    @Test
    fun `records from concurrent producers keep their order across rotated files`() {
        val sink =
            RotatingFileSink(
                logDir,
                maxFiles = 500,
                maxFileBytes = 1_500,
                queueCapacity = 10_000,
            )
        val threads = 6
        val perThread = 300
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { n -> sink.write(record("thread $t seq $n")) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue()
        assertThat(sink.flush(timeoutMillis = 30_000)).isTrue()

        assertThat(logFiles().size).isGreaterThan(1)
        assertThat(logFiles().all { it.length() <= 1_500 }).isTrue()
        val lines = messages().filter { it.startsWith("thread ") }
        assertThat(lines).hasSize(threads * perThread)
        for (t in 0 until threads) {
            val sequence =
                lines
                    .filter { it.startsWith("thread $t ") }
                    .map { it.substringAfterLast(' ').toInt() }
            assertThat(sequence).containsExactlyElementsIn((0 until perThread).toList()).inOrder()
        }
    }
}
