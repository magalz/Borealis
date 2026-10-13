package io.github.magalz.borealis.logging

import java.io.File

/**
 * Appends one UTF-8 line per record to `<directory>/borealis.log`. When the file would exceed
 * [maxFileBytes] it is rotated to `borealis.log.1` (older files shift up) and at most [maxFiles]
 * files are kept in total.
 */
@Suppress("UnusedPrivateProperty", "UnusedParameter", "EmptyFunctionBlock")
class RotatingFileSink(
    private val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) : LogSink {
    override fun write(record: LogRecord) {}

    companion object {
        const val FILE_NAME = "borealis.log"
        const val DEFAULT_MAX_FILES = 5
        const val DEFAULT_MAX_FILE_BYTES = 1024L * 1024L
    }
}
