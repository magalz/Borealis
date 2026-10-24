package io.github.magalz.borealis.computerstore

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.IdentityManager
import io.github.magalz.borealis.discovery.LimeLogRecords
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The client id of the inherited `IdentityManager`: the `uniqueid` file that the Java build wrote
 * (`computerstore/uniqueid`), the id it generates when there is none, and what it logs and does
 * when the file is short, long, odd or cannot be read or written.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class IdentityManagerCharacterizationTest {
    @get:Rule val log = LimeLogRecords()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val idFile = File(context.filesDir, UNIQUE_ID_FILE)

    private fun writeIdFile(text: String) {
        idFile.parentFile?.mkdirs()
        idFile.writeText(text)
    }

    /** A generated id: 16 lower-case hex digits, the 64 bits of a long. */
    private val generated = Regex("[0-9a-f]{16}")

    /** The lines that `LimeLog` wrote, as the level and the text. */
    private fun lines(): List<String> = log.lines()

    @Test
    fun `the client id that the Java build wrote is read back`() {
        installFixture(UNIQUE_ID_FILE, idFile)

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).isEqualTo(FIXTURE_UNIQUE_ID)
        assertThat(idFile.readText()).isEqualTo(FIXTURE_UNIQUE_ID)
        assertThat(lines())
            .containsExactly("INFO: Reading UID from disk", "INFO: UID is now: $FIXTURE_UNIQUE_ID")
            .inOrder()
    }

    @Test
    fun `the fixture file is the 16 hex digits and nothing else`() {
        val bytes = fixtureBytes(UNIQUE_ID_FILE)

        assertThat(bytes).hasLength(16)
        assertThat(String(bytes, Charsets.US_ASCII)).matches(generated.pattern)
    }

    @Test
    fun `the first run generates an id, writes it to the file and logs each step`() {
        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(idFile.readBytes().toList()).isEqualTo(identity.uniqueId.toByteArray(Charsets.US_ASCII).toList())
        assertThat(lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "INFO: No UID file found",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    @Test
    fun `the second run reads the id that the first one wrote`() {
        val first = IdentityManager(context).uniqueId

        val second = IdentityManager(context).uniqueId

        assertThat(second).isEqualTo(first)
        assertThat(idFile.readText()).isEqualTo(first)
        assertThat(lines().takeLast(2))
            .containsExactly("INFO: Reading UID from disk", "INFO: UID is now: $first")
            .inOrder()
    }

    @Test
    fun `the id is kept for the life of the object`() {
        val identity = IdentityManager(context)
        val id = identity.uniqueId

        idFile.writeText("0123456789abcdef")

        assertThat(identity.uniqueId).isEqualTo(id)
    }

    @Test
    fun `generated ids are always 16 lower-case hex digits, padded with zeros`() {
        val ids = List(GENERATED_IDS) {
            idFile.delete()
            IdentityManager(context).uniqueId
        }

        assertThat(ids.filterNot { generated.matches(it) }).isEmpty()
        // In 500 ids a zero comes first about 31 times, a letter shows in nearly every id, a long
        // below zero shows as a first digit from 8 to f, and the first eight digits are not fixed.
        assertThat(ids.any { id -> id.startsWith("0") }).isTrue()
        assertThat(ids.any { id -> id.any { it in 'a'..'f' } }).isTrue()
        assertThat(ids.any { id -> id.first() in '8'..'f' }).isTrue()
        assertThat(ids.any { id -> id.first() in '0'..'7' }).isTrue()
        assertThat(ids.map { it.take(HIGH_DIGITS) }.toSet().size).isGreaterThan(GENERATED_IDS - 2)
        assertThat(ids.toSet().size).isGreaterThan(GENERATED_IDS - 2)
    }

    @Test
    fun `a file shorter than 16 characters is truncated, and a new id replaces it`() {
        writeIdFile("0123456789abcde")

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(idFile.readText()).isEqualTo(identity.uniqueId)
        assertThat(lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "SEVERE: UID file data is truncated",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    @Test
    fun `an empty file is truncated too`() {
        writeIdFile("")

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(idFile.readText()).isEqualTo(identity.uniqueId)
        assertThat(lines()).contains("SEVERE: UID file data is truncated")
    }

    @Test
    fun `a file longer than 16 characters gives its first 16 and is left alone`() {
        writeIdFile("0123456789abcdefEXTRA")

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).isEqualTo("0123456789abcdef")
        assertThat(idFile.readText()).isEqualTo("0123456789abcdefEXTRA")
        assertThat(lines())
            .containsExactly("INFO: Reading UID from disk", "INFO: UID is now: 0123456789abcdef")
            .inOrder()
    }

    @Test
    fun `an id of 16 characters is taken as it is, whatever the characters are`() {
        writeIdFile("ABCDEF 0123 xyz!")

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).isEqualTo("ABCDEF 0123 xyz!")
        assertThat(idFile.readText()).isEqualTo("ABCDEF 0123 xyz!")
    }

    @Test
    fun `a line break at the end of a short id counts as a character`() {
        writeIdFile("0123456789abcde\n")

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).isEqualTo("0123456789abcde\n")
    }

    /** The id file as the code under test opens it, with the faults that a test asks for. */
    private class TrackedInput(file: File, private val fault: IOException?) : FileInputStream(file) {
        var closed = false

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            if (fault != null) throw fault else super.read(b, off, len)

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class TrackedOutput(file: File, private val fault: IOException?) : FileOutputStream(file) {
        var closed = false

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (fault != null) throw fault
            super.write(b, off, len)
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    /** Opens the id file as a context does, and notes the calls, the streams and the faults. */
    private class IdFileContext(
        base: Context,
        private val readFault: IOException? = null,
        private val writeFault: IOException? = null,
    ) : ContextWrapper(base) {
        val calls = mutableListOf<String>()
        val inputs = mutableListOf<TrackedInput>()
        val outputs = mutableListOf<TrackedOutput>()

        override fun openFileInput(name: String): FileInputStream {
            calls += "in $name"
            return TrackedInput(File(filesDir, name), readFault).also { inputs += it }
        }

        override fun openFileOutput(name: String, mode: Int): FileOutputStream {
            calls += "out $name $mode"
            return TrackedOutput(File(filesDir, name), writeFault).also { outputs += it }
        }
    }

    @Test
    fun `the id is read from the file named uniqueid and written to it for the app alone`() {
        val files = IdFileContext(context)

        IdentityManager(files)
        IdentityManager(files)

        assertThat(files.calls).containsExactly("in uniqueid", "out uniqueid 0", "in uniqueid").inOrder()
    }

    @Test
    fun `the file is closed after it is written, and after it is read`() {
        val files = IdFileContext(context)

        IdentityManager(files)
        assertThat(files.outputs.single().closed).isTrue()

        IdentityManager(files)
        assertThat(files.inputs.single().closed).isTrue()
    }

    @Test
    fun `a file that cannot be read gives an error log, and a new id is generated and written`() {
        writeIdFile("0123456789abcdef")
        val files = IdFileContext(context, readFault = IOException("read failed"))

        val identity = IdentityManager(files)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(identity.uniqueId).isNotEqualTo("0123456789abcdef")
        assertThat(idFile.readText()).isEqualTo(identity.uniqueId)
        assertThat(files.inputs.single().closed).isTrue()
        assertThat(lines())
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
    fun `a path that is a directory is no file, so an id is generated and cannot be written`() {
        assertThat(idFile.mkdirs()).isTrue()

        val identity = IdentityManager(context)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(idFile.isDirectory).isTrue()
        assertThat(lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "INFO: No UID file found",
                "INFO: Generating new UID",
                "SEVERE: Error while writing UID file",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    @Test
    fun `a file that cannot be written still gives a generated id, and the log tells it`() {
        val files = IdFileContext(context, writeFault = IOException("disk full"))

        val identity = IdentityManager(files)

        assertThat(identity.uniqueId).matches(generated.pattern)
        assertThat(files.outputs.single().closed).isTrue()
        // The text is only buffered when the log says "written": the failure comes when it is flushed.
        assertThat(lines())
            .containsExactly(
                "INFO: Reading UID from disk",
                "INFO: No UID file found",
                "INFO: Generating new UID",
                "INFO: UID written to disk",
                "SEVERE: Error while writing UID file",
                "INFO: UID is now: ${identity.uniqueId}",
            )
            .inOrder()
    }

    private companion object {
        const val GENERATED_IDS = 500
        const val HIGH_DIGITS = 8
    }
}
