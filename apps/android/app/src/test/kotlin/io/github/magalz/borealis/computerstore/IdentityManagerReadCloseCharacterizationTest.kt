package io.github.magalz.borealis.computerstore

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.IdentityManager
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What `IdentityManager` does when the id file is read in full but its `close` then fails. The
 * [IdentityManagerCharacterizationTest] faults the read; this one faults the close, which comes
 * after the id was read and checked.
 *
 * Characterization of the code as it is: the close failure is an I/O error of the whole read, so
 * the id that was read is dropped and a new one replaces it.
 */
@RunWith(RobolectricTestRunner::class)
class IdentityManagerReadCloseCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val idFile = File(context.filesDir, "uniqueid")

    /** Opens the id file for reading as a context does, with a close that fails. */
    private class FailingCloseContext(base: Context) : ContextWrapper(base) {
        override fun openFileInput(name: String): FileInputStream =
            object : FileInputStream(File(filesDir, name)) {
                override fun close() {
                    super.close()
                    throw IOException("close failed")
                }
            }
    }

    private fun writeIdFile(text: String) {
        idFile.parentFile?.mkdirs()
        idFile.writeText(text)
    }

    @Test
    fun `an id that was read in full is dropped when the close fails, and a new one replaces it`() {
        writeIdFile("0123456789abcdef")

        val identity = IdentityManager(FailingCloseContext(context))

        assertThat(identity.uniqueId).matches("[0-9a-f]{16}")
        assertThat(identity.uniqueId).isNotEqualTo("0123456789abcdef")
        assertThat(idFile.readText()).isEqualTo(identity.uniqueId)
        assertThat(log.lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "SEVERE: Error while reading UID file",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    @Test
    fun `a truncated file whose close fails logs the truncation first, then the read error`() {
        writeIdFile("0123456789abcde")

        val identity = IdentityManager(FailingCloseContext(context))

        assertThat(identity.uniqueId).matches("[0-9a-f]{16}")
        assertThat(log.lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "SEVERE: UID file data is truncated",
                "SEVERE: Error while reading UID file",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    @Test
    fun `a saved id that is not hex and whose close fails is dropped after the warning`() {
        writeIdFile("ABCDEF 0123 xyz!")

        val identity = IdentityManager(FailingCloseContext(context))

        assertThat(identity.uniqueId).matches("[0-9a-f]{16}")
        assertThat(log.lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "WARNING: UID file data is not hex digits, the saved id is kept",
                "SEVERE: Error while reading UID file",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }
}
