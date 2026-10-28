package io.github.magalz.borealis.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.PosterContentProvider
import com.limelight.grid.assets.DiskAssetLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The TV channel poster of an app is the box art that [BoxArtLoader] cached. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterContentProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val binder = FakeCmsBinder()
    private val clients = FakeHostClients(binder)

    @Before
    fun providerIsRegistered() {
        DiskAssetLoader(context).deleteAssetsForComputer(HOST)
        Robolectric.setupContentProvider(
            PosterContentProvider::class.java,
            PosterContentProvider.AUTHORITY,
        )
        binder.computers[HOST] = details(uuid = HOST).also { it.serverCert = testCertificate() }
    }

    @Test
    fun `a prefetched app has its box art as the poster`() = runTest {
        val png =
            ByteArrayOutputStream().use {
                Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(Color.RED) }
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
        clients.client.boxArtAnswer = { ByteArrayInputStream(png) }
        val io = UnconfinedTestDispatcher(testScheduler)
        BoxArtLoader(context, ComputerManagerConnection(FakeCmsConnector(binder), io), clients, io)
            .prefetch(HOST, 7)
            .join()

        val poster =
            context.contentResolver
                .openInputStream(PosterContentProvider.createBoxArtUri(HOST, "7"))!!
                .use { it.readBytes() }

        assertThat(poster).isEqualTo(png)
    }

    private fun open(hostId: String, appId: String) =
        context.contentResolver.openInputStream(
            PosterContentProvider.createBoxArtUri(hostId, appId)
        )

    @Test
    fun `a host id that climbs out of the cache directory has no poster`() {
        // cache/boxart/../7.png is cache/7.png, which is not box art.
        File(context.cacheDir, "7.png").writeText("not a poster")

        assertThrows(FileNotFoundException::class.java) { open("..", "7") }
    }

    @Test
    fun `a host id that is not a plain id has no poster`() {
        for (hostId in listOf("a/b", "a\\b", "host 1", "a".repeat(65), "..%2F..")) {
            assertThrows(FileNotFoundException::class.java) { open(hostId, "7") }
        }
    }

    @Test
    fun `an app id that is not a number has no poster instead of an error`() {
        for (appId in listOf("abc", "-1", "", "99999999999", "1.5")) {
            assertThrows(FileNotFoundException::class.java) { open(HOST, appId) }
        }
    }

    @Test
    fun `an app without cached art has no poster`() {
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(
                PosterContentProvider.createBoxArtUri(HOST, "7")
            )
        }
    }

    private companion object {
        // A host id as the host sends it: hexadecimal digits and hyphens.
        const val HOST = "0a1b2c3d-1111-2222-3333-444455556666"
    }
}
