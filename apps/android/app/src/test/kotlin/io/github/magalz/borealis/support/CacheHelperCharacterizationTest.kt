package io.github.magalz.borealis.support

import com.google.common.truth.Truth.assertThat
import com.limelight.utils.CacheHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The file helpers of the cache directory that the computer manager and the box art share. */
class CacheHelperCharacterizationTest {
    @get:Rule val folder = TemporaryFolder()

    private val root: File
        get() = folder.root

    @Test
    fun `a path is the components under the root, and nothing is created`() {
        val file = CacheHelper.openPath(false, root, "boxart", "host", "7.png")

        assertThat(file).isEqualTo(File(File(File(root, "boxart"), "host"), "7.png"))
        assertThat(File(root, "boxart").exists()).isFalse()
    }

    @Test
    fun `a path with no components is the root itself`() {
        assertThat(CacheHelper.openPath(false, root)).isEqualTo(root)
        assertThat(CacheHelper.openPath(true, root)).isEqualTo(root)
    }

    @Test
    fun `creating a path makes its parent directories and not the file`() {
        val file = CacheHelper.openPath(true, root, "boxart", "host", "7.png")

        assertThat(file.parentFile?.isDirectory).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `creating a path with one component makes the root`() {
        val missing = File(root, "cache")

        val file = CacheHelper.openPath(true, missing, "applist")

        assertThat(missing.isDirectory).isTrue()
        assertThat(file).isEqualTo(File(missing, "applist"))
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `a path that is not created leaves an existing tree as it is`() {
        File(root, "boxart/host").mkdirs()

        CacheHelper.openPath(false, root, "boxart", "other", "1.png")

        assertThat(File(root, "boxart").list()?.toList()).containsExactly("host")
    }

    @Test
    fun `a cache file exists when its file does`() {
        File(root, "applist").mkdirs()
        File(root, "applist/host").writeText("x")

        assertThat(CacheHelper.cacheFileExists(root, "applist", "host")).isTrue()
        assertThat(CacheHelper.cacheFileExists(root, "applist", "other")).isFalse()
        assertThat(CacheHelper.cacheFileExists(root, "missing", "host")).isFalse()
    }

    @Test
    fun `asking whether a cache file exists creates nothing`() {
        CacheHelper.cacheFileExists(root, "applist", "host")

        assertThat(root.list()?.toList()).isEmpty()
    }

    @Test
    fun `a cache file is opened for output with its parent directories made`() {
        CacheHelper.openCacheFileForOutput(root, "applist", "host").use {
            it.write("abc".toByteArray())
        }

        assertThat(File(root, "applist/host").readText()).isEqualTo("abc")
    }

    @Test
    fun `a cache file that exists is overwritten, not appended to`() {
        CacheHelper.openCacheFileForOutput(root, "f").use {
            it.write("long old text".toByteArray())
        }

        CacheHelper.openCacheFileForOutput(root, "f").use { it.write("new".toByteArray()) }

        assertThat(File(root, "f").readText()).isEqualTo("new")
    }

    @Test
    fun `the output stream of a cache file is buffered`() {
        val out = CacheHelper.openCacheFileForOutput(root, "f")
        out.write("abc".toByteArray())

        // Nothing reached the file before the stream was flushed or closed.
        assertThat(File(root, "f").length()).isEqualTo(0)
        out.close()
        assertThat(File(root, "f").length()).isEqualTo(3)
    }

    @Test
    fun `a cache file that is a directory cannot be opened`() {
        File(root, "dir").mkdirs()

        assertThrows(FileNotFoundException::class.java) {
            CacheHelper.openCacheFileForOutput(root, "dir")
        }
    }

    @Test
    fun `a stream that is shorter than the limit is copied whole`() {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()

        CacheHelper.writeInputStreamToOutputStream(ByteArrayInputStream(data), out, 10_001)

        assertThat(out.toByteArray()).isEqualTo(data)
    }

    @Test
    fun `a stream that is as long as the limit is refused, and nothing of its last chunk is written`() {
        val out = ByteArrayOutputStream()

        val error =
            assertThrows(IOException::class.java) {
                CacheHelper.writeInputStreamToOutputStream(
                    ByteArrayInputStream(ByteArray(10)),
                    out,
                    10,
                )
            }

        assertThat(error).hasMessageThat().isEqualTo("Stream exceeded max size")
        assertThat(out.size()).isEqualTo(0)
    }

    @Test
    fun `a stream over the limit has the chunks before the one that crossed it written`() {
        val out = ByteArrayOutputStream()

        assertThrows(IOException::class.java) {
            // The first read is 4096 bytes: 1 byte of the limit is left, so the second read
            // crosses.
            CacheHelper.writeInputStreamToOutputStream(
                ByteArrayInputStream(ByteArray(5_000)),
                out,
                4_097,
            )
        }

        assertThat(out.size()).isEqualTo(4_096)
    }

    @Test
    fun `an empty stream is copied even with a limit of zero`() {
        val out = ByteArrayOutputStream()

        CacheHelper.writeInputStreamToOutputStream(ByteArrayInputStream(ByteArray(0)), out, 0)

        assertThat(out.size()).isEqualTo(0)
    }

    @Test
    fun `a limit above the range of an int is counted as a long`() {
        val out = ByteArrayOutputStream()

        CacheHelper.writeInputStreamToOutputStream(
            ByteArrayInputStream(ByteArray(100)),
            out,
            Int.MAX_VALUE.toLong() + 1,
        )

        assertThat(out.size()).isEqualTo(100)
    }

    @Test
    fun `a string is written as UTF-8`() {
        val out = ByteArrayOutputStream()

        CacheHelper.writeStringToOutputStream(out, "caf\u00e9 \u2603")

        assertThat(out.toByteArray()).isEqualTo("caf\u00e9 \u2603".toByteArray(Charsets.UTF_8))
        assertThat(out.size()).isEqualTo(9)
    }
}
