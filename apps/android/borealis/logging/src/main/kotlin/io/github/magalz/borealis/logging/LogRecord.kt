package io.github.magalz.borealis.logging

/**
 * A fully redacted log record as handed to the sinks. [fields] keep insertion order; [stackTrace]
 * is the redacted stack trace of the attached throwable, if any.
 */
data class LogRecord(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
    val fields: List<Pair<String, String>> = emptyList(),
    val stackTrace: String? = null,
)
