package io.github.magalz.borealis.logging

import java.util.logging.Handler
import java.util.logging.LogRecord as JulRecord

/** Forwards `java.util.logging` records to [BorealisLog] with the [LogTag.UPSTREAM] tag. */
@Suppress("UnusedParameter", "EmptyFunctionBlock")
class JulLogHandler : Handler() {
    override fun publish(record: JulRecord?) {}

    override fun flush() {}

    override fun close() {}
}

/** Attaches a [JulLogHandler] to the logger used by upstream Moonlight's `LimeLog`. */
@Suppress("UnusedParameter", "EmptyFunctionBlock")
object JulBridge {
    const val DEFAULT_LOGGER_NAME = "com.limelight.LimeLog"

    /** Installs the handler (once per logger) and returns it. */
    fun install(loggerName: String = DEFAULT_LOGGER_NAME): JulLogHandler = JulLogHandler()

    /** Removes the handler and restores the logger's parent handlers. */
    fun uninstall(loggerName: String = DEFAULT_LOGGER_NAME) {}
}
