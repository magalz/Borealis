package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException

class ServerInfoRetryTest {
    @get:Rule val log = RecordingLogSink()

    /** Fake time: each call to the reader takes [callMs], as a read timeout would. */
    private var now = 0L
    private var cancelled = false
    private var calls = 0

    private fun read(callMs: Long = READ_TIMEOUT_MS, answer: (Int) -> String): String =
        ServerInfoRetry.read(
            ServerInfoReader {
                calls++
                now += callMs
                answer(calls)
            },
            { cancelled },
            { now },
        )

    private fun timeout(call: Int) = SocketTimeoutException("read timed out $call")

    @Test
    fun `a timeout followed by an answer returns the answer after two calls`() {
        val result = read { if (it == 1) throw timeout(it) else "<root/>" }

        assertThat(result).isEqualTo("<root/>")
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `a timeout on every attempt makes exactly three calls and rethrows the last timeout`() {
        val failure =
            assertThrows(SocketTimeoutException::class.java) { read { throw timeout(it) } }

        assertThat(calls).isEqualTo(3)
        assertThat(failure).hasMessageThat().isEqualTo("read timed out 3")
    }

    @Test
    fun `an answer on the first call is returned without a retry`() {
        assertThat(read { "<root/>" }).isEqualTo("<root/>")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `an IOException that is not a timeout is rethrown at once`() {
        val failure = IOException("connection refused")

        val thrown = assertThrows(IOException::class.java) { read { throw failure } }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `a parser error is rethrown at once`() {
        val failure = XmlPullParserException("bad xml")

        val thrown = assertThrows(XmlPullParserException::class.java) { read { throw failure } }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `a cancel after the first timeout makes no second call`() {
        val failure =
            assertThrows(SocketTimeoutException::class.java) {
                read {
                    cancelled = true
                    throw timeout(it)
                }
            }

        assertThat(calls).isEqualTo(1)
        assertThat(failure).hasMessageThat().isEqualTo("read timed out 1")
    }

    @Test
    fun `no attempt starts once the elapsed time is over the budget`() {
        val failure =
            assertThrows(SocketTimeoutException::class.java) {
                read(callMs = ServerInfoRetry.BUDGET_MS + 1) { throw timeout(it) }
            }

        assertThat(calls).isEqualTo(1)
        assertThat(failure).hasMessageThat().isEqualTo("read timed out 1")
    }

    @Test
    fun `a retry is logged once with its attempt number`() {
        read { if (it == 1) throw timeout(it) else "<root/>" }

        val records = log.records.filter { it.tag == LogTag.LAUNCH }
        assertThat(records).hasSize(1)
        assertThat(records.single().level).isEqualTo(LogLevel.INFO)
        assertThat(records.single().fields).contains("attempt" to 2)
        assertThat(records.single().fields).contains("max" to ServerInfoRetry.MAX_ATTEMPTS)
    }

    private companion object {
        const val READ_TIMEOUT_MS = 7_000L
    }
}
