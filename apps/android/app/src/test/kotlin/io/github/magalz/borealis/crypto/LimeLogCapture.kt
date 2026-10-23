package io.github.magalz.borealis.crypto

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/** The records that `LimeLog` writes while this is open: its level and its text. */
internal class LimeLogCapture : Closeable {
    private val logger = Logger.getLogger("com.limelight.LimeLog")
    val records = CopyOnWriteArrayList<Pair<Level, String>>()

    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                records += record.level to record.message
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    init {
        logger.addHandler(handler)
    }

    val messages: List<String>
        get() = records.map { it.second }

    override fun close() = logger.removeHandler(handler)
}
