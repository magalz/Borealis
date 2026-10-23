package com.limelight.computers

import android.content.Context
import com.limelight.LimeLog
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12
        // backlog). The stack trace goes to the standard error stream, as in the inherited Java.
        // Logging it instead is a behaviour change.
        @Suppress("ReturnCount", "PrintStackTrace")
        fun loadUniqueId(c: Context): String? {
            // 2 Hex digits per byte
            val uid = CharArray(UID_SIZE_IN_BYTES * 2)
            LimeLog.info("Reading UID from disk")
            try {
                InputStreamReader(c.openFileInput(UNIQUE_ID_FILE_NAME)).use { reader ->
                    if (reader.read(uid) != UID_SIZE_IN_BYTES * 2) {
                        LimeLog.severe("UID file data is truncated")
                        return null
                    }
                    return String(uid)
                }
            } catch (ignored: FileNotFoundException) {
                LimeLog.info("No UID file found")
                return null
            } catch (e: IOException) {
                LimeLog.severe("Error while reading UID file")
                e.printStackTrace()
                return null
            }
        }

        // The stack trace goes to the standard error stream, as in the inherited Java. Logging it
        // instead is a behaviour change (Sprint 12 backlog).
        @Suppress("PrintStackTrace")
        fun generateNewUniqueId(c: Context): String {
            // Generate a new UID hex string
            LimeLog.info("Generating new UID")
            val uidStr = String.format(null as Locale?, "%016x", Random().nextLong())

            try {
                OutputStreamWriter(c.openFileOutput(UNIQUE_ID_FILE_NAME, 0)).use { writer ->
                    writer.write(uidStr)
                    LimeLog.info("UID written to disk")
                }
            } catch (e: IOException) {
                LimeLog.severe("Error while writing UID file")
                e.printStackTrace()
            }

            // We can return a UID even if I/O fails
            return uidStr
        }
    }
}
