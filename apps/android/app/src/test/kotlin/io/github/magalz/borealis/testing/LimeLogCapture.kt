package io.github.magalz.borealis.testing

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.rules.ExternalResource

/**
 * What `LimeLog` writes while the capture is open, with the level of each line.
 *
 * As a rule it is open for the whole test. A test that wants the lines of one part of its body
 * opens it with [start] and closes it, which `use` does: `LimeLogCapture().start().use { log -> ...
 * }`.
 */
class LimeLogCapture : ExternalResource(), Closeable {
    private val logger = Logger.getLogger("com.limelight.LimeLog")
    private val lines = CopyOnWriteArrayList<Pair<Level, String>>()
    private var open = false
    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                lines += record.level to record.message
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    /** The level and the text of every line written so far. */
    val entries: List<Pair<Level, String>>
        get() = lines.toList()

    /** The text of every line written so far. */
    val messages: List<String>
        get() = entries.map { it.second }

    /** The text of the lines written so far at [level]. */
    fun messages(level: Level): List<String> =
        entries.filter { it.first == level }.map { it.second }

    /** The lines written so far as `LEVEL: message`. */
    fun lines(): List<String> = entries.map { "${it.first}: ${it.second}" }

    fun warnings(): List<String> = messages(Level.WARNING)

    fun severes(): List<String> = messages(Level.SEVERE)

    /** Opens the capture now; [before] does it when the capture is a rule. */
    fun start(): LimeLogCapture = also { before() }

    override fun before() {
        if (!open) logger.addHandler(handler)
        open = true
    }

    override fun after() {
        if (open) logger.removeHandler(handler)
        open = false
    }

    override fun close() = after()
}
