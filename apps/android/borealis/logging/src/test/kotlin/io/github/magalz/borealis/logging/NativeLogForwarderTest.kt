package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Test

class NativeLogForwarderTest {
    private val emitted = CopyOnWriteArrayList<String>()
    private var now = 0L
    private var ready = true

    private fun forwarder(burst: Int = 5, perSecond: Int = 10) =
        NativeLogForwarder(
            ready = { ready },
            emit = { emitted += it },
            nanoClock = { now },
            burst = burst,
            linesPerSecond = perSecond,
        )

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    @Test
    fun `forwards the decoded line and reports success`() {
        val ok = forwarder().accept(bytes("Received first video packet after 123 ms"))

        assertThat(ok).isTrue()
        assertThat(emitted).containsExactly("Received first video packet after 123 ms")
    }

    @Test
    fun `strips trailing line breaks but keeps the text`() {
        forwarder().accept(bytes("RTSP handshake complete\r\n\n"))

        assertThat(emitted).containsExactly("RTSP handshake complete")
    }

    @Test
    fun `decodes utf-8 and replaces malformed sequences`() {
        val line =
            bytes("host café ") + byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte()) + bytes("!")

        forwarder().accept(line)

        assertThat(emitted).containsExactly("host café �(�!")
    }

    @Test
    fun `an empty or newline-only line is consumed without a record`() {
        val forwarder = forwarder()

        assertThat(forwarder.accept(ByteArray(0))).isTrue()
        assertThat(forwarder.accept(bytes("\n"))).isTrue()

        assertThat(emitted).isEmpty()
    }

    @Test
    fun `reports failure and logs nothing while the log is not initialised`() {
        ready = false

        val ok = forwarder().accept(bytes("too early"))

        assertThat(ok).isFalse()
        assertThat(emitted).isEmpty()
    }

    @Test
    fun `lines over the burst are counted instead of logged and still count as forwarded`() {
        val forwarder = forwarder(burst = 5)

        val results = (1..8).map { forwarder.accept(bytes("line $it")) }

        assertThat(results.all { it }).isTrue()
        assertThat(emitted)
            .containsExactly("line 1", "line 2", "line 3", "line 4", "line 5")
            .inOrder()
    }

    @Test
    fun `one summary precedes the first line after the limit lifts`() {
        val forwarder = forwarder(burst = 5, perSecond = 10)
        repeat(5) { forwarder.accept(bytes("line $it")) }
        repeat(50) { forwarder.accept(bytes("lost $it")) }
        emitted.clear()

        now += 1_000_000_000L // ten tokens
        forwarder.accept(bytes("back"))
        forwarder.accept(bytes("again"))

        assertThat(emitted).containsExactly("50 lines suppressed", "back", "again").inOrder()
    }

    @Test
    fun `no summary is logged when nothing was suppressed`() {
        val forwarder = forwarder(burst = 5)

        forwarder.accept(bytes("a"))
        now += 10_000_000_000L
        forwarder.accept(bytes("b"))

        assertThat(emitted).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `the bucket refills at the configured rate and never beyond the burst`() {
        val forwarder = forwarder(burst = 5, perSecond = 10)
        repeat(5) { forwarder.accept(bytes("drain $it")) }
        emitted.clear()

        now += 300_000_000L // three tokens
        repeat(6) { forwarder.accept(bytes("line $it")) }
        assertThat(emitted.filter { it.startsWith("line") }).hasSize(3)

        emitted.clear()
        now += 3_600_000_000_000L // an hour: capped at the burst of five
        repeat(9) { forwarder.accept(bytes("again $it")) }
        assertThat(emitted.filter { it.startsWith("again") }).hasSize(5)
    }

    @Test
    fun `concurrent callers never log more than the burst`() {
        val forwarder = forwarder(burst = 100)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) { t ->
            pool.execute {
                start.await()
                repeat(200) { n -> forwarder.accept(bytes("thread $t line $n")) }
            }
        }

        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue()

        assertThat(emitted).hasSize(100)
    }

    @Test
    fun `forwarded lines pass the redactor and reach logcat once under the COMMONC tag`() {
        val prints = CopyOnWriteArrayList<Pair<String, String>>()
        BorealisLog.initialize(listOf(LogcatSink { _, tag, message -> prints += tag to message }))

        val ok = NativeLogForwarder.forward(bytes("pairing phrase pin=7391 accepted\n"))

        assertThat(ok).isTrue()
        assertThat(prints).hasSize(1)
        assertThat(prints.single().first).isEqualTo("Borealis.COMMONC")
        assertThat(prints.single().second).contains("pin=[REDACTED]")
        assertThat(prints.single().second).doesNotContain("7391")
    }

    @Test
    fun `the static entry point reports failure before the log is initialised`() {
        assertThat(NativeLogForwarder.forward(bytes("too early"))).isFalse()
    }
}
