package io.github.magalz.borealis.stream.testing

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.rules.ExternalResource

/** Routes [BorealisLog] into [records] for one test and shuts the facade down afterwards. */
class RecordingLogSink : ExternalResource(), LogSink {
    val records = CopyOnWriteArrayList<LogRecord>()

    override fun before() {
        BorealisLog.initialize(listOf(this), LogLevel.VERBOSE)
    }

    override fun after() {
        BorealisLog.shutdown()
    }

    override fun write(record: LogRecord) {
        records += record
    }
}
