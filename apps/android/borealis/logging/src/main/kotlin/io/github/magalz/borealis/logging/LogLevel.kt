package io.github.magalz.borealis.logging

/** Severity of a log record, lowest first. [androidPriority] matches `android.util.Log`. */
enum class LogLevel(val androidPriority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
}
