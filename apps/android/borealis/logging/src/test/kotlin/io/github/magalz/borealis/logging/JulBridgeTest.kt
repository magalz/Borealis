package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.util.logging.Level
import java.util.logging.LogRecord as JulRecord
import java.util.logging.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

class JulBridgeTest {
    private val sink = RecordingSink()
    private val loggerName = "test.borealis.JulBridgeTest"

    @Before
    fun setUp() {
        BorealisLog.initialize(listOf(sink), minLevel = LogLevel.VERBOSE)
    }

    @After
    fun tearDown() {
        JulBridge.uninstall(loggerName)
        BorealisLog.shutdown()
    }

    @Test
    fun `jul levels map to borealis levels with the upstream tag`() {
        val handler = JulLogHandler()
        val expected =
            mapOf(
                Level.SEVERE to LogLevel.ERROR,
                Level.WARNING to LogLevel.WARN,
                Level.INFO to LogLevel.INFO,
                Level.CONFIG to LogLevel.INFO,
                Level.FINE to LogLevel.DEBUG,
                Level.FINER to LogLevel.VERBOSE,
                Level.FINEST to LogLevel.VERBOSE,
            )

        for (level in expected.keys) handler.publish(JulRecord(level, "m"))

        assertThat(sink.records.map { it.level })
            .containsExactlyElementsIn(expected.values)
            .inOrder()
        assertThat(sink.records.all { it.tag == LogTag.UPSTREAM }).isTrue()
    }

    @Test
    fun `message parameters are formatted and the logger name is a field`() {
        val record =
            JulRecord(Level.INFO, "connected to {0} on {1}").apply {
                parameters = arrayOf("host-a", "47989")
                loggerName = "com.limelight.LimeLog"
            }

        JulLogHandler().publish(record)

        val logged = sink.records.single()
        assertThat(logged.message).isEqualTo("connected to host-a on 47989")
        assertThat(logged.fields).containsExactly("logger" to "com.limelight.LimeLog")
    }

    @Test
    fun `thrown becomes a stack trace`() {
        val record =
            JulRecord(Level.SEVERE, "failed").apply { thrown = IllegalStateException("boom") }

        JulLogHandler().publish(record)

        assertThat(sink.records.single().stackTrace)
            .startsWith("java.lang.IllegalStateException: boom")
    }

    @Test
    fun `bridged messages are redacted`() {
        JulLogHandler().publish(JulRecord(Level.INFO, "pair with pin=1234"))

        assertThat(sink.records.single().message).isEqualTo("pair with pin=[REDACTED]")
    }

    @Test
    fun `publishing is safe when the log is not initialised`() {
        BorealisLog.shutdown()

        JulLogHandler().publish(JulRecord(Level.INFO, "early"))
        JulLogHandler().publish(null)
    }

    @Test
    fun `installed handler receives records logged on the logger`() {
        JulBridge.install(loggerName)

        Logger.getLogger(loggerName).info("from limelog")
        Logger.getLogger(loggerName).fine("detail")

        assertThat(sink.records.map { it.message })
            .containsExactly("from limelog", "detail")
            .inOrder()
    }

    @Test
    fun `installing twice does not duplicate records`() {
        JulBridge.install(loggerName)
        JulBridge.install(loggerName)

        Logger.getLogger(loggerName).warning("once")

        assertThat(sink.records).hasSize(1)
        assertThat(sink.records.single().level).isEqualTo(LogLevel.WARN)
    }

    @Test
    fun `parent handlers are bypassed so the record is not printed twice`() {
        JulBridge.install(loggerName)

        assertThat(Logger.getLogger(loggerName).useParentHandlers).isFalse()
    }

    @Test
    fun `uninstall removes the handler and restores parent handlers`() {
        JulBridge.install(loggerName)

        JulBridge.uninstall(loggerName)
        Logger.getLogger(loggerName).info("after")

        assertThat(sink.records).isEmpty()
        assertThat(Logger.getLogger(loggerName).useParentHandlers).isTrue()
    }

    @Test
    fun `the default logger is the one used by upstream LimeLog`() {
        assertThat(JulBridge.DEFAULT_LOGGER_NAME).isEqualTo("com.limelight.LimeLog")
    }
}
