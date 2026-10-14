package io.github.magalz.borealis.logging

/** Destination for log records. Implementations must be thread-safe. */
interface LogSink {
    fun write(record: LogRecord)

    /** Releases resources; called when the log is shut down or re-initialised. */
    fun close() {}
}
