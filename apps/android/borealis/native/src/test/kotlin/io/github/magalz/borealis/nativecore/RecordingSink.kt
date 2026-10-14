package io.github.magalz.borealis.nativecore

import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList

class RecordingSink : LogSink {
    val records = CopyOnWriteArrayList<LogRecord>()

    override fun write(record: LogRecord) {
        records += record
    }
}
