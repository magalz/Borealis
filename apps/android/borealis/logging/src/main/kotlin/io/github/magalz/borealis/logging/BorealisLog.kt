package io.github.magalz.borealis.logging

/**
 * Process-wide logging facade. Records are redacted and then fanned out to the configured sinks.
 * Until [initialize] is called, records are dropped. Logging never throws.
 */
object BorealisLog {
    private data class State(
        val sinks: List<LogSink>,
        val minLevel: LogLevel,
        val redactor: Redactor,
        val clock: () -> Long,
    )

    private val INVALID_KEY_CHARS = Regex("[^A-Za-z0-9_.-]")

    @Volatile private var state: State? = null

    val isInitialized: Boolean
        get() = state != null

    /** Replaces any previous configuration; the previous sinks are closed. */
    @Synchronized
    fun initialize(
        sinks: List<LogSink>,
        minLevel: LogLevel = LogLevel.DEBUG,
        redactor: Redactor = Redactor(),
        clock: () -> Long = System::currentTimeMillis,
    ) {
        val previous = state
        state = State(sinks.toList(), minLevel, redactor, clock)
        previous?.sinks?.forEach { runCatching { it.close() } }
    }

    /** Closes the sinks and goes back to dropping records. */
    @Synchronized
    fun shutdown() {
        val previous = state
        state = null
        previous?.sinks?.forEach { runCatching { it.close() } }
    }

    /** Changes the minimum level of the running configuration; ignored before [initialize]. */
    @Synchronized
    fun setMinLevel(level: LogLevel) {
        state = state?.copy(minLevel = level)
    }

    /** Switches address masking of the running configuration; ignored before [initialize]. */
    fun setMaskAddresses(enabled: Boolean) {
        state?.redactor?.maskAddresses = enabled
    }

    /**
     * Redacts [text] with the running configuration's redactor (and so its address-masking switch);
     * a default redactor (no address masking) before [initialize]. Text over the redactor's length
     * cap is cut, so pass a line, not a file.
     */
    fun redact(text: String): String = TODO()

    fun log(
        level: LogLevel,
        tag: LogTag,
        message: String,
        fields: List<Pair<String, Any?>> = emptyList(),
        throwable: Throwable? = null,
    ) {
        val current = state ?: return
        if (level < current.minLevel) return
        runCatching {
            val redactor = current.redactor
            val record =
                LogRecord(
                    timestampMillis = current.clock(),
                    level = level,
                    tag = tag,
                    message = redactor.redact(message),
                    fields =
                        fields.map { (key, value) ->
                            val safeKey = key.replace(INVALID_KEY_CHARS, "_")
                            safeKey to redactor.redactField(safeKey, value.toString())
                        },
                    stackTrace = throwable?.let { redactor.redact(it.stackTraceToString()) },
                )
            for (sink in current.sinks) runCatching { sink.write(record) }
        }
    }

    fun v(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.VERBOSE, tag, message, fields.asList())

    fun d(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.DEBUG, tag, message, fields.asList())

    fun i(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.INFO, tag, message, fields.asList())

    fun w(tag: LogTag, message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.WARN, tag, message, fields.asList())

    fun e(
        tag: LogTag,
        message: String,
        throwable: Throwable? = null,
        vararg fields: Pair<String, Any?>,
    ) = log(LogLevel.ERROR, tag, message, fields.asList(), throwable)
}
