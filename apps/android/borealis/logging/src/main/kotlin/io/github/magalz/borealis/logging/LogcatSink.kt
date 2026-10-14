package io.github.magalz.borealis.logging

import android.util.Log

/**
 * Writes records at or above [minLevel] to logcat under the tag `Borealis.<TAG>`. [print] is the
 * output primitive (`Log.println` by default).
 */
class LogcatSink(
    private val minLevel: LogLevel = LogLevel.VERBOSE,
    private val print: (priority: Int, tag: String, message: String) -> Unit =
        { priority, tag, message ->
            Log.println(priority, tag, message)
        },
) : LogSink {
    override fun write(record: LogRecord) {
        if (record.level < minLevel) return
        val tag = "Borealis.${record.tag.name}"
        val priority = record.level.androidPriority
        print(priority, tag, LogFormatter.formatBody(record))
        record.stackTrace?.let { print(priority, tag, it) }
    }
}
