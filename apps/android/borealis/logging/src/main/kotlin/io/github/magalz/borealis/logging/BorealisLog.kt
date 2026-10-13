package io.github.magalz.borealis.logging

/**
 * Process-wide logging facade. Records are redacted and then fanned out to the configured sinks.
 * Until [initialize] is called, records are dropped.
 */
@Suppress("UnusedParameter", "EmptyFunctionBlock")
object BorealisLog {
    val isInitialized: Boolean
        get() = false

    fun initialize(
        sinks: List<LogSink>,
        minLevel: LogLevel = LogLevel.DEBUG,
        redactor: Redactor = Redactor(),
        clock: () -> Long = System::currentTimeMillis,
    ) {}

    /** Closes the sinks and goes back to dropping records. */
    fun shutdown() {}

    fun log(
        level: LogLevel,
        tag: LogTag,
        message: String,
        fields: List<Pair<String, Any?>> = emptyList(),
        throwable: Throwable? = null,
    ) {}

    fun v(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) {}

    fun d(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) {}

    fun i(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) {}

    fun w(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) {}

    fun e(
        tag: LogTag,
        message: String,
        throwable: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) {}
}
