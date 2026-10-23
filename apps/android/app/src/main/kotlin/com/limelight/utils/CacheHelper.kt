package com.limelight.utils

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Objects

object CacheHelper {
    private const val BUFFER_SIZE = 4096

    // A null component is refused with a NullPointerException, as File did for the Java callers.
    @JvmStatic
    fun openPath(createPath: Boolean, root: File, vararg path: String?): File {
        var f = root
        for (i in path.indices) {
            val component = path[i]

            if (i == path.size - 1) {
                // This is the file component so now we create parent directories
                if (createPath) {
                    f.mkdirs()
                }
            }

            f = File(f, Objects.requireNonNull(component))
        }
        return f
    }

    @JvmStatic
    fun cacheFileExists(root: File, vararg path: String?): Boolean =
        openPath(false, root, *path).exists()

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun openCacheFileForOutput(root: File, vararg path: String?): OutputStream =
        BufferedOutputStream(FileOutputStream(openPath(true, root, *path)))

    @JvmStatic
    @Throws(IOException::class)
    fun writeInputStreamToOutputStream(input: InputStream, out: OutputStream, maxLength: Long) {
        val buf = ByteArray(BUFFER_SIZE)
        var remaining = maxLength

        while (true) {
            val bytesRead = input.read(buf)
            if (bytesRead == -1) {
                break
            }
            remaining -= bytesRead
            if (remaining <= 0) {
                throw IOException("Stream exceeded max size")
            }
            out.write(buf, 0, bytesRead)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeStringToOutputStream(out: OutputStream, str: String) {
        out.write(str.toByteArray(Charsets.UTF_8))
    }
}
