package io.github.magalz.borealis.support

import com.google.common.truth.Truth.assertThat
import com.limelight.LimeLog
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `LimeLog`, which about 25 files call as `LimeLog.info`, `warning` and `severe`: the lines go to
 * the `java.util.logging` logger `com.limelight.LimeLog`, which `JulBridge` listens to.
 */
class LimeLogCharacterizationTest {
    private val logger = Logger.getLogger("com.limelight.LimeLog")
    private val records = CopyOnWriteArrayList<LogRecord>()
    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    @Before
    fun listen() {
        logger.addHandler(handler)
    }

    @After
    fun stopListening() {
        logger.removeHandler(handler)
    }

    @Test
    fun `info is logged at INFO`() {
        LimeLog.info("an info line")

        assertThat(records.map { it.level to it.message })
            .containsExactly(Level.INFO to "an info line")
    }

    @Test
    fun `warning is logged at WARNING`() {
        LimeLog.warning("a warning line")

        assertThat(records.map { it.level to it.message })
            .containsExactly(Level.WARNING to "a warning line")
    }

    @Test
    fun `severe is logged at SEVERE`() {
        LimeLog.severe("a severe line")

        assertThat(records.map { it.level to it.message })
            .containsExactly(Level.SEVERE to "a severe line")
    }

    @Test
    fun `the records carry the logger name that JulBridge listens to`() {
        LimeLog.info("one")
        LimeLog.warning("two")
        LimeLog.severe("three")

        assertThat(records.map { it.loggerName }.distinct())
            .containsExactly("com.limelight.LimeLog")
    }

    @Test
    fun `a line is logged once and in order`() {
        LimeLog.info("first")
        LimeLog.severe("second")
        LimeLog.info("third")

        assertThat(records.map { it.message }).containsExactly("first", "second", "third").inOrder()
    }

    @Test
    fun `a null message is logged as null instead of failing`() {
        LimeLog.info(null)
        LimeLog.warning(null)
        LimeLog.severe(null)

        assertThat(records.map { it.level to it.message })
            .containsExactly(Level.INFO to null, Level.WARNING to null, Level.SEVERE to null)
            .inOrder()
    }

    @Test
    fun `an empty message and a message with format characters are logged as they are`() {
        LimeLog.info("")
        LimeLog.info("100% {0} %s")

        assertThat(records.map { it.message }).containsExactly("", "100% {0} %s").inOrder()
    }
}
