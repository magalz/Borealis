package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class BorealisLogTest {
    private val sink = RecordingSink()

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    @Test
    fun `logging before initialisation is safe and the message is dropped`() {
        BorealisLog.i(LogTag.APP, "too early", "k" to "v")
        BorealisLog.e(LogTag.APP, "too early", RuntimeException("x"))

        BorealisLog.initialize(listOf(sink))

        assertThat(sink.records).isEmpty()
    }

    @Test
    fun `initialise and shutdown toggle the state`() {
        assertThat(BorealisLog.isInitialized).isFalse()

        BorealisLog.initialize(listOf(sink))
        assertThat(BorealisLog.isInitialized).isTrue()

        BorealisLog.shutdown()
        assertThat(BorealisLog.isInitialized).isFalse()
        assertThat(sink.closed).isTrue()
    }

    @Test
    fun `redact uses the running redactor and follows its address masking`() {
        BorealisLog.initialize(listOf(sink), redactor = Redactor(maskAddresses = false))

        assertThat(BorealisLog.redact("pin=4821 at 192.0.2.5")).isEqualTo("pin=[REDACTED] at 192.0.2.5")

        BorealisLog.setMaskAddresses(true)

        assertThat(BorealisLog.redact("pin=4821 at 192.0.2.5")).isEqualTo("pin=[REDACTED] at [IP]")
    }

    @Test
    fun `redact still removes secrets before initialisation`() {
        assertThat(BorealisLog.isInitialized).isFalse()

        assertThat(BorealisLog.redact("password=hunter2")).isEqualTo("password=[REDACTED]")
    }

    @Test
    fun `records carry level, tag, message, ordered fields and the clock time`() {
        BorealisLog.initialize(listOf(sink), clock = { 42L })

        BorealisLog.w(LogTag.HOST, "slow", "host" to "pc", "rtt" to 120, "gone" to null)

        assertThat(sink.records)
            .containsExactly(
                LogRecord(
                    timestampMillis = 42L,
                    level = LogLevel.WARN,
                    tag = LogTag.HOST,
                    message = "slow",
                    fields = listOf("host" to "pc", "rtt" to "120", "gone" to "null"),
                )
            )
    }

    @Test
    fun `every level helper logs at its level`() {
        BorealisLog.initialize(listOf(sink), minLevel = LogLevel.VERBOSE)

        BorealisLog.v(LogTag.CORE, "m")
        BorealisLog.d(LogTag.CORE, "m")
        BorealisLog.i(LogTag.CORE, "m")
        BorealisLog.w(LogTag.CORE, "m")
        BorealisLog.e(LogTag.CORE, "m")

        assertThat(sink.records.map { it.level })
            .containsExactly(
                LogLevel.VERBOSE,
                LogLevel.DEBUG,
                LogLevel.INFO,
                LogLevel.WARN,
                LogLevel.ERROR,
            )
            .inOrder()
    }

    @Test
    fun `records below the minimum level are dropped`() {
        BorealisLog.initialize(listOf(sink), minLevel = LogLevel.INFO)

        BorealisLog.d(LogTag.APP, "debug")
        BorealisLog.i(LogTag.APP, "info")

        assertThat(sink.records.map { it.message }).containsExactly("info")
    }

    @Test
    fun `message and fields are redacted before any sink sees them`() {
        val other = RecordingSink()
        BorealisLog.initialize(listOf(sink, other))

        BorealisLog.i(
            LogTag.PAIR,
            "pairing with pin=1234",
            "apiToken" to "abc def",
            "note" to "Bearer abc.def",
            "host" to "pc",
        )

        for (s in listOf(sink, other)) {
            val record = s.records.single()
            assertThat(record.message).isEqualTo("pairing with pin=[REDACTED]")
            assertThat(record.fields)
                .containsExactly(
                    "apiToken" to "[REDACTED]",
                    "note" to "Bearer [REDACTED]",
                    "host" to "pc",
                )
                .inOrder()
        }
    }

    @Test
    fun `ip masking follows the redactor passed at initialisation`() {
        BorealisLog.initialize(listOf(sink), redactor = Redactor(maskAddresses = true))

        BorealisLog.i(LogTag.HOST, "connect 10.0.0.5", "addr" to "10.0.0.5")

        val record = sink.records.single()
        assertThat(record.message).isEqualTo("connect [IP]")
        assertThat(record.fields).containsExactly("addr" to "[IP]")
    }

    @Test
    fun `address masking can be switched while logging`() {
        BorealisLog.initialize(listOf(sink))

        BorealisLog.i(LogTag.HOST, "before 10.0.0.5")
        BorealisLog.setMaskAddresses(true)
        BorealisLog.i(LogTag.HOST, "masked 10.0.0.5", "host" to "Den-PC")
        BorealisLog.setMaskAddresses(false)
        BorealisLog.i(LogTag.HOST, "after 10.0.0.5")

        assertThat(sink.records.map { it.message })
            .containsExactly("before 10.0.0.5", "masked [IP]", "after 10.0.0.5")
            .inOrder()
        assertThat(sink.records[1].fields).containsExactly("host" to "[HOST]")
    }

    @Test
    fun `the minimum level can be changed while logging`() {
        BorealisLog.initialize(listOf(sink), minLevel = LogLevel.INFO)

        BorealisLog.d(LogTag.APP, "dropped")
        BorealisLog.setMinLevel(LogLevel.DEBUG)
        BorealisLog.d(LogTag.APP, "kept")
        BorealisLog.setMinLevel(LogLevel.WARN)
        BorealisLog.i(LogTag.APP, "dropped again")

        assertThat(sink.records.map { it.message }).containsExactly("kept")
    }

    @Test
    fun `changing the settings before initialisation is safe`() {
        BorealisLog.setMaskAddresses(true)
        BorealisLog.setMinLevel(LogLevel.ERROR)

        BorealisLog.initialize(listOf(sink))
        BorealisLog.i(LogTag.APP, "to 10.0.0.5")

        assertThat(sink.records.map { it.message }).containsExactly("to 10.0.0.5")
    }

    @Test
    fun `throwable becomes a redacted stack trace`() {
        BorealisLog.initialize(listOf(sink))

        BorealisLog.e(LogTag.REST, "request failed", IllegalStateException("bad token=hunter2"))

        val stack = sink.records.single().stackTrace
        assertThat(stack).startsWith("java.lang.IllegalStateException: bad token=[REDACTED]")
        assertThat(stack).contains("BorealisLogTest")
        assertThat(stack).doesNotContain("hunter2")
    }

    @Test
    fun `field names are sanitised`() {
        BorealisLog.initialize(listOf(sink))

        BorealisLog.i(LogTag.APP, "m", "bad key=1" to "v", "ok.key-1_x" to "v")

        assertThat(sink.records.single().fields.map { it.first })
            .containsExactly("bad_key_1", "ok.key-1_x")
            .inOrder()
    }

    @Test
    fun `a failing sink does not affect the caller or the other sinks`() {
        val broken =
            object : LogSink {
                override fun write(record: LogRecord) = throw IllegalStateException("disk full")
            }
        BorealisLog.initialize(listOf(broken, sink))

        BorealisLog.i(LogTag.APP, "still delivered")

        assertThat(sink.records.map { it.message }).containsExactly("still delivered")
    }

    @Test
    fun `initialising again closes the previous sinks and replaces them`() {
        val first = RecordingSink()
        BorealisLog.initialize(listOf(first))

        BorealisLog.initialize(listOf(sink))
        BorealisLog.i(LogTag.APP, "after")

        assertThat(first.closed).isTrue()
        assertThat(first.records).isEmpty()
        assertThat(sink.records).hasSize(1)
    }

    @Test
    fun `records after shutdown are dropped`() {
        BorealisLog.initialize(listOf(sink))
        BorealisLog.shutdown()

        BorealisLog.i(LogTag.APP, "late")

        assertThat(sink.records).isEmpty()
    }
}
