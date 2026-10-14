package io.github.magalz.borealis.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord as JulRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/** Forwards `java.util.logging` records to [BorealisLog] with the [LogTag.UPSTREAM] tag. */
class JulLogHandler : Handler() {
    private val formatter = SimpleFormatter()

    init {
        level = Level.ALL
    }

    override fun publish(record: JulRecord?) {
        if (record == null) return
        runCatching {
            BorealisLog.log(
                level = toLogLevel(record.level),
                tag = LogTag.UPSTREAM,
                message = formatter.formatMessage(record),
                fields = listOfNotNull(record.loggerName?.let { "logger" to it }),
                throwable = record.thrown,
            )
        }
    }

    override fun flush() = Unit

    override fun close() = Unit

    private fun toLogLevel(level: Level): LogLevel {
        val value = level.intValue()
        return when {
            value >= Level.SEVERE.intValue() -> LogLevel.ERROR
            value >= Level.WARNING.intValue() -> LogLevel.WARN
            value >= Level.CONFIG.intValue() -> LogLevel.INFO
            value >= Level.FINE.intValue() -> LogLevel.DEBUG
            else -> LogLevel.VERBOSE
        }
    }
}

/** Attaches a [JulLogHandler] to the logger used by upstream Moonlight's `LimeLog`. */
object JulBridge {
    const val DEFAULT_LOGGER_NAME = "com.limelight.LimeLog"

    // The LogManager only holds loggers weakly; keep ours alive so the handler is never lost
    // when the bridge is installed before the upstream class has created its own reference.
    private val retained = ConcurrentHashMap<String, Logger>()

    /**
     * Installs the handler (once per logger) and returns it. Parent handlers are bypassed so
     * Android's default JUL-to-logcat handler does not print every record a second time.
     */
    @Synchronized
    fun install(loggerName: String = DEFAULT_LOGGER_NAME): JulLogHandler {
        val logger = Logger.getLogger(loggerName)
        retained[loggerName] = logger
        removeHandlers(logger)
        val handler = JulLogHandler()
        logger.addHandler(handler)
        logger.useParentHandlers = false
        logger.level = Level.ALL
        return handler
    }

    /** Removes the handler and restores the logger's parent handlers. */
    @Synchronized
    fun uninstall(loggerName: String = DEFAULT_LOGGER_NAME) {
        val logger = retained.remove(loggerName) ?: return
        removeHandlers(logger)
        logger.useParentHandlers = true
        logger.level = null
    }

    private fun removeHandlers(logger: Logger) {
        logger.handlers.filterIsInstance<JulLogHandler>().forEach(logger::removeHandler)
    }
}
