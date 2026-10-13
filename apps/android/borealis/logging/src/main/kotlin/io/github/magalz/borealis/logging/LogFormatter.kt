package io.github.magalz.borealis.logging

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Turns records into single-line text. */
object LogFormatter {
    private val TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** `2023-11-14T22:13:20.123Z INFO APP message key=value`; always exactly one line. */
    fun format(record: LogRecord): String = buildString {
        append(TIMESTAMP.format(Instant.ofEpochMilli(record.timestampMillis)))
        append(' ').append(record.level.name)
        append(' ').append(record.tag.name)
        append(' ').append(formatBody(record))
        record.stackTrace?.let { append(" stack=").append(quote(it)) }
    }

    /** The message and fields only, for sinks that add their own timestamp, level and tag. */
    fun formatBody(record: LogRecord): String = buildString {
        append(escapeLineBreaks(record.message))
        for ((key, value) in record.fields) {
            append(' ')
                .append(key)
                .append('=')
                .append(if (needsQuotes(value)) quote(value) else value)
        }
    }

    private fun escapeLineBreaks(text: String) = text.replace("\n", "\\n").replace("\r", "\\r")

    private fun needsQuotes(value: String) =
        value.isEmpty() || value.any { it.isWhitespace() || it == '"' || it == '=' || it == '\\' }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
