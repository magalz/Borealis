package io.github.magalz.borealis.logging

/** Turns records into single-line text. */
object LogFormatter {
    /** `2023-11-14T22:13:20.123Z INFO APP message key=value`; always exactly one line. */
    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    fun format(record: LogRecord): String = ""

    /** The message and fields only, for sinks that add their own timestamp, level and tag. */
    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    fun formatBody(record: LogRecord): String = ""
}
