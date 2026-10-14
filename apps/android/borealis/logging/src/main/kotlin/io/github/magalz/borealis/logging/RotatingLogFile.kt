package io.github.magalz.borealis.logging

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The `borealis.log` file ring of [RotatingFileSink]: appends bytes, rotating to `borealis.log.1`
 * (older files shift up) before a write that would pass [maxFileBytes], and keeps at most
 * [maxFiles] files. Not thread-safe; the sink's writer thread is its only user.
 */
internal class RotatingLogFile(
    private val directory: File,
    private val maxFiles: Int,
    private val maxFileBytes: Long,
) {
    private var stream: FileOutputStream? = null
    private var size = 0L

    fun append(bytes: ByteArray) {
        try {
            var out = openCurrent()
            if (size > 0 && size + bytes.size > maxFileBytes) {
                rotate()
                out = openCurrent()
            }
            out.write(bytes)
            size += bytes.size
        } catch (e: IOException) {
            // Reopen on the next record instead of keeping a broken stream around.
            close()
            throw e
        }
    }

    fun close() {
        runCatching { stream?.close() }
        stream = null
    }

    private fun openCurrent(): FileOutputStream {
        stream?.let {
            return it
        }
        directory.mkdirs()
        val file = File(directory, RotatingFileSink.FILE_NAME)
        size = file.length()
        return FileOutputStream(file, true).also { stream = it }
    }

    private fun rotate() {
        close()
        val current = File(directory, RotatingFileSink.FILE_NAME)
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

    private fun numbered(index: Int) = File(directory, "${RotatingFileSink.FILE_NAME}.$index")

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
