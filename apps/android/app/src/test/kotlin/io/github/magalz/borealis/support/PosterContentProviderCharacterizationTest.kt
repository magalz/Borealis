package io.github.magalz.borealis.support

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.BuildConfig
import com.limelight.PosterContentProvider
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Modifier
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The provider that serves the box art to the TV launcher (`PosterContentProviderTest` has the
 * prefetch and the host and app id checks): its authority, its URIs, its read-only contract.
 */
@RunWith(RobolectricTestRunner::class)
class PosterContentProviderCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver

    @Before
    fun providerIsRegistered() {
        Robolectric.setupContentProvider(
            PosterContentProvider::class.java,
            PosterContentProvider.AUTHORITY,
        )
    }

    private fun art(hostId: String, appId: Int, text: String) {
        File(context.cacheDir, "boxart/$hostId/$appId.png")
            .also { it.parentFile?.mkdirs() }
            .writeText(text)
    }

    private fun uri(path: String): Uri =
        Uri.parse("content://${PosterContentProvider.AUTHORITY}/$path")

    @Test
    fun `the authority is poster dot the application id`() {
        assertThat(PosterContentProvider.AUTHORITY)
            .isEqualTo("poster." + BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `the constants of the provider keep their values`() {
        assertThat(PosterContentProvider.PNG_MIME_TYPE).isEqualTo("image/png")
        assertThat(PosterContentProvider.COMPUTER_UUID_PATH_INDEX).isEqualTo(1)
        assertThat(PosterContentProvider.APP_ID_PATH_INDEX).isEqualTo(2)
    }

    @Test
    fun `the URI of a poster is boxart, the host id and the app id under the authority`() {
        val uri = PosterContentProvider.createBoxArtUri("0a1b-22", "7")

        assertThat(uri.toString())
            .isEqualTo("content://poster.${BuildConfig.APPLICATION_ID}/boxart/0a1b-22/7")
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo(PosterContentProvider.AUTHORITY)
        assertThat(uri.pathSegments).containsExactly("boxart", "0a1b-22", "7").inOrder()
    }

    @Test
    fun `the segments of a poster URI are encoded`() {
        val uri = PosterContentProvider.createBoxArtUri("a b/c", "1?2")

        assertThat(uri.toString())
            .isEqualTo("content://poster.${BuildConfig.APPLICATION_ID}/boxart/a%20b%2Fc/1%3F2")
        assertThat(uri.pathSegments).containsExactly("boxart", "a b/c", "1?2").inOrder()
    }

    @Test
    fun `the type of every URI is PNG`() {
        assertThat(resolver.getType(PosterContentProvider.createBoxArtUri("0a1b", "7")))
            .isEqualTo("image/png")
        assertThat(resolver.getType(uri("anything/at/all/here"))).isEqualTo("image/png")
    }

    @Test
    fun `a poster is read through the URI of its host and app`() {
        art("0a1b-22", 7, "the poster")

        val poster =
            resolver.openInputStream(PosterContentProvider.createBoxArtUri("0a1b-22", "7"))!!.use {
                it.readText()
            }

        assertThat(poster).isEqualTo("the poster")
    }

    @Test
    fun `a path that is not boxart is served as boxart when it has the segments of it`() {
        art("0a1b-22", 7, "the poster")

        val poster = resolver.openInputStream(uri("anything/0a1b-22/7"))!!.use { it.readText() }

        assertThat(poster).isEqualTo("the poster")
    }

    @Test
    fun `a URI without exactly three segments has no poster`() {
        art("0a1b-22", 7, "the poster")

        for (path in
            listOf("", "boxart", "boxart/0a1b-22", "boxart/0a1b-22/7/extra", "0a1b-22/7")) {
            assertThrows("path: $path", FileNotFoundException::class.java) {
                resolver.openInputStream(uri(path))
            }
        }
    }

    @Test
    fun `the provider only opens for read`() {
        art("0a1b-22", 7, "the poster")
        val uri = PosterContentProvider.createBoxArtUri("0a1b-22", "7")

        for (mode in listOf("w", "rw", "wa", "rwt", "")) {
            val error =
                assertThrows("mode: $mode", UnsupportedOperationException::class.java) {
                    resolver.openFileDescriptor(uri, mode)
                }
            assertThat(error).hasMessageThat().isEqualTo("This provider is only for read mode")
        }
    }

    @Test
    fun `the provider is read only`() {
        val uri = PosterContentProvider.createBoxArtUri("0a1b-22", "7")

        assertThat(
                assertThrows(UnsupportedOperationException::class.java) {
                    resolver.query(uri, null, null, null, null)
                }
            )
            .hasMessageThat()
            .isEqualTo("This provider doesn't support query")
        assertThat(
                assertThrows(UnsupportedOperationException::class.java) {
                    resolver.insert(uri, ContentValues())
                }
            )
            .hasMessageThat()
            .isEqualTo("This provider is only for read mode")
        assertThat(
                assertThrows(UnsupportedOperationException::class.java) {
                    resolver.update(uri, ContentValues(), null, null)
                }
            )
            .hasMessageThat()
            .isEqualTo("This provider is support read only")
        assertThat(
                assertThrows(UnsupportedOperationException::class.java) {
                    resolver.delete(uri, null, null)
                }
            )
            .hasMessageThat()
            .isEqualTo("This provider is only for read mode")
    }

    @Test
    fun `the provider is the one that the manifest declares, exported, for the authority`() {
        val info =
            checkNotNull(
                context.packageManager.resolveContentProvider(PosterContentProvider.AUTHORITY, 0)
            )

        assertThat(info.name).isEqualTo("com.limelight.PosterContentProvider")
        assertThat(info.exported).isTrue()
        assertThat(info.enabled).isTrue()
    }

    @Test
    fun `the provider has a public constructor without arguments and a public file opener`() {
        val constructor = PosterContentProvider::class.java.getConstructor()
        val opener =
            PosterContentProvider::class
                .java
                .getMethod(
                    "openBoxArtFile",
                    Uri::class.java,
                    String::class.java,
                )

        assertThat(Modifier.isPublic(constructor.modifiers)).isTrue()
        assertThat(Modifier.isPublic(opener.modifiers)).isTrue()
        assertThat(opener.exceptionTypes.toList())
            .containsExactly(FileNotFoundException::class.java)
        assertThat(opener.returnType).isEqualTo(android.os.ParcelFileDescriptor::class.java)
    }

    private fun java.io.InputStream.readText() = readBytes().toString(Charsets.UTF_8)
}
