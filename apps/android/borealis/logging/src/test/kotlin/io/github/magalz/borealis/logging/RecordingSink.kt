package io.github.magalz.borealis.logging

import java.util.concurrent.CopyOnWriteArrayList

class RecordingSink : LogSink {
    val records = CopyOnWriteArrayList<LogRecord>()
    var closed = false
        private set

    override fun write(record: LogRecord) {
        records += record
    }

    override fun close() {
        closed = true
    }
}
