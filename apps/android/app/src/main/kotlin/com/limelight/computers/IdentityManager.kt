package com.limelight.computers

import android.content.Context
import com.limelight.LimeLog
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.util.Locale
import java.util.Random

class IdentityManager(c: Context) {
    val uniqueId: String

    init {
        uniqueId = loadUniqueId(c) ?: generateNewUniqueId(c)

        LimeLog.info("UID is now: $uniqueId")
    }

    private companion object {
        private const val UNIQUE_ID_FILE_NAME = "uniqueid"
        private const val UID_SIZE_IN_BYTES = 8

        // Converted from the inherited Java; the stack trace goes to the standard error stream as
        // it did, and logging it instead is a behaviour change (Sprint 18c backlog). The saved id
        // is kept when it is not hex (Sprint 16): generating a new one would unpair the client
        // from every host. The warning must not hold the id, which is the identity of the client
        // toward every host.
        @Suppress("PrintStackTrace")
        fun loadUniqueId(c: Context): String? {
            LimeLog.info("Reading UID from disk")
            return try {
                InputStreamReader(c.openFileInput(UNIQUE_ID_FILE_NAME)).use { reader ->
                    readUniqueId(reader)
                }
            } catch (ignored: FileNotFoundException) {
                LimeLog.info("No UID file found")
                null
            } catch (e: IOException) {
                LimeLog.severe("Error while reading UID file")
                e.printStackTrace()
                null
            }
        }

        /** The id in [reader], or null when it holds fewer characters than an id. */
        private fun readUniqueId(reader: Reader): String? {
            // 2 Hex digits per byte
            val uid = CharArray(UID_SIZE_IN_BYTES * 2)
            if (reader.read(uid) != UID_SIZE_IN_BYTES * 2) {
                LimeLog.severe("UID file data is truncated")
                return null
            }
            if (!uid.all { isHexDigit(it) }) {
                LimeLog.warning("UID file data is not hex digits, the saved id is kept")
            }
            return String(uid)
        }

        private fun isHexDigit(ch: Char) = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

        // The stack trace goes to the standard error stream, as in the inherited Java. Logging it
        // instead is a behaviour change (Sprint 18c backlog).
        @Suppress("PrintStackTrace")
        fun generateNewUniqueId(c: Context): String {
            // Generate a new UID hex string
            LimeLog.info("Generating new UID")
            val uidStr = String.format(null as Locale?, "%016x", Random().nextLong())

            try {
                OutputStreamWriter(c.openFileOutput(UNIQUE_ID_FILE_NAME, 0)).use { writer ->
                    writer.write(uidStr)
                }
                // Only the close flushes the buffered text, so the log comes after it.
                LimeLog.info("UID written to disk")
            } catch (e: IOException) {
                LimeLog.severe("Error while writing UID file")
                e.printStackTrace()
            }

            // We can return a UID even if I/O fails
            return uidStr
        }
    }
}
