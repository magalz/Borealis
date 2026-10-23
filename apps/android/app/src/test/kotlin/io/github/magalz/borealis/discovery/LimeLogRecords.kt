package io.github.magalz.borealis.discovery

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.rules.ExternalResource

/** What `LimeLog` writes while a test runs, with the level of each line. */
class LimeLogRecords : ExternalResource() {
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

    override fun before() {
        logger.addHandler(handler)
    }

    override fun after() {
        logger.removeHandler(handler)
    }

    /** The lines logged so far as `LEVEL: message`. */
    fun lines(): List<String> = records.map { "${it.level}: ${it.message}" }

    /** The lines logged so far at [level]. */
    fun messages(level: Level): List<String> = records.filter { it.level == level }.map { it.message }
}
