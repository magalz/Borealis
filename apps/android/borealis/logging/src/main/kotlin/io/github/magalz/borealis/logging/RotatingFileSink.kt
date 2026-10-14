package io.github.magalz.borealis.logging

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Appends one UTF-8 line per record to `<directory>/borealis.log`. When a record would push the
 * file past [maxFileBytes] it is rotated to `borealis.log.1` (older files shift up) and at most
 * [maxFiles] files are kept in total, the oldest being deleted. Every record is flushed to the file
 * as soon as it is written so nothing is lost if the process dies.
 */
class RotatingFileSink(
    private val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val writeGate: () -> Unit = {},
) : LogSink {
    private val lock = Any()
    private var stream: FileOutputStream? = null
    private var size = 0L
    private var closed = false

    init {
        require(maxFiles >= 1) { "maxFiles must be at least 1" }
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
    }

    override fun write(record: LogRecord) {
        val bytes = (LogFormatter.format(record) + "\n").toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            if (closed) return
            try {
                var out = openCurrent()
                if (size > 0 && size + bytes.size > maxFileBytes) {
                    rotate()
                    out = openCurrent()
                }
                out.write(bytes)
                size += bytes.size
            } catch (e: java.io.IOException) {
                // Reopen on the next record instead of keeping a broken stream around.
                closeStream()
                throw e
            }
        }
    }

    /** Placeholder until the asynchronous writer exists. */
    fun flush(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS): Boolean = true

    override fun close() {
        synchronized(lock) {
            closed = true
            closeStream()
        }
    }

    private fun openCurrent(): FileOutputStream {
        stream?.let {
            return it
        }
        directory.mkdirs()
        val file = File(directory, FILE_NAME)
        size = file.length()
        return FileOutputStream(file, true).also { stream = it }
    }

    private fun closeStream() {
        stream?.close()
        stream = null
    }

    private fun rotate() {
        closeStream()
        val current = File(directory, FILE_NAME)
        if (maxFiles == 1) {
            current.delete()
            return
        }
        numbered(maxFiles - 1).delete()
        for (index in maxFiles - 2 downTo 1) {
            val file = numbered(index)
            if (file.exists()) move(file, numbered(index + 1))
        }
        move(current, numbered(1))
    }

    private fun numbered(index: Int) = File(directory, "$FILE_NAME.$index")

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val FILE_NAME = "borealis.log"
        const val DEFAULT_MAX_FILES = 5
        const val DEFAULT_MAX_FILE_BYTES = 1024L * 1024L
        const val DEFAULT_QUEUE_CAPACITY = 1024
        const val DEFAULT_FLUSH_TIMEOUT_MILLIS = 2_000L

        /** Placeholder until the asynchronous writer exists. */
        fun flushAll(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS) {}
    }
}
