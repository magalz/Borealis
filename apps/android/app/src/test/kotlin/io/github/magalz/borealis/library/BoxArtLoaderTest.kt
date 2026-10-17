package io.github.magalz.borealis.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The box art of the apps of a host: fetched from it once, cached on disk, never fatal. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoxArtLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val binder = FakeCmsBinder()
    private val clients = FakeHostClients(binder)
    private val io = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }
    private val dispatcher = io.asCoroutineDispatcher()
    private val disk = DiskAssetLoader(context)
    private val png = png()

    private fun loader(): BoxArtLoader =
        BoxArtLoader(
            context,
            ComputerManagerConnection(FakeCmsConnector(binder), dispatcher),
            clients,
            dispatcher,
        )

    private fun cached(appId: Int, hostId: String = "host-1") =
        disk.checkCacheExists(
            DiskAssetLoader.LoaderTuple(
                ComputerDetails().also { it.uuid = hostId },
                NvApp("", appId, false),
            )
        )

    private fun png(): ByteArray =
        ByteArrayOutputStream().use { out ->
            val art = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            art.eraseColor(Color.RED)
            art.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

    @Before
    fun hostIsPaired() {
        disk.deleteAssetsForComputer("host-1")
        binder.computers["host-1"] = pairedHost()
        clients.client.boxArt = { ByteArrayInputStream(png) }
    }

    @After
    fun stopIo() {
        io.shutdownNow()
    }

    private fun pairedHost(): ComputerDetails =
        details(uuid = "host-1").also { it.serverCert = testCertificate() }

    @Test
    fun `the art of an app is fetched from its host and stored in the disk cache`() = runTest {
        val art = loader().bitmap("host-1", 7)

        assertThat(art?.width).isEqualTo(300)
        assertThat(art?.height).isEqualTo(400)
        assertThat(clients.client.boxArtRequests).containsExactly(7)
        assertThat(cached(7)).isTrue()
        assertThat(disk.getFile("host-1", 7).readBytes()).isEqualTo(png)
    }

    @Test
    fun `art that is cached makes no request, also for a loader that did not fetch it`() =
        runTest {
            loader().bitmap("host-1", 7)

            val art = loader().bitmap("host-1", 7)

            assertThat(art).isNotNull()
            assertThat(clients.client.boxArtRequests).containsExactly(7)
        }

    @Test
    fun `art that is cached needs no connection to the host`() = runTest {
        loader().bitmap("host-1", 7)
        binder.computers.clear()

        assertThat(loader().bitmap("host-1", 7)).isNotNull()
    }

    @Test
    fun `the art of two apps is cached apart`() = runTest {
        loader().bitmap("host-1", 7)
        loader().bitmap("host-1", 8)

        assertThat(clients.client.boxArtRequests).containsExactly(7, 8).inOrder()
        assertThat(cached(7)).isTrue()
        assertThat(cached(8)).isTrue()
    }

    @Test
    fun `a prefetch fills the cache, and a later load makes no second request`() = runTest {
        val loader = loader()

        loader.prefetch("host-1", 7).join()
        val art = loader.bitmap("host-1", 7)

        assertThat(cached(7)).isTrue()
        assertThat(art).isNotNull()
        assertThat(clients.client.boxArtRequests).containsExactly(7)
    }

    @Test
    fun `a host that has no art for the app gives none and nothing is cached`() = runTest {
        clients.client.boxArt = { throw FileNotFoundException("404") }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `a network failure gives no art instead of throwing`() = runTest {
        clients.client.boxArt = { throw IOException("connection reset") }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `a stream that breaks half way leaves no partial file`() = runTest {
        clients.client.boxArt = {
            object : InputStream() {
                private var sent = 0

                override fun read(): Int {
                    if (sent++ > 100) throw IOException("connection reset")
                    return 0
                }
            }
        }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `a host that cannot be reached gives no art instead of throwing`() = runTest {
        clients.failure = IOException("no address")

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `art that cannot be decoded gives none, and is fetched again next time`() = runTest {
        clients.client.boxArt = { ByteArrayInputStream("this is not an image".toByteArray()) }
        val loader = loader()

        assertThat(loader.bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()

        clients.client.boxArt = { ByteArrayInputStream(png) }
        assertThat(loader.bitmap("host-1", 7)).isNotNull()
        assertThat(clients.client.boxArtRequests).hasSize(2)
    }

    @Test
    fun `art larger than 5 MB is rejected and not stored`() = runTest {
        clients.client.boxArt = { ByteArrayInputStream(ByteArray(6 * 1024 * 1024)) }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `the request is made off the calling thread`() = runTest {
        var thread: String? = null
        clients.client.boxArt = {
            thread = Thread.currentThread().name
            ByteArrayInputStream(png)
        }

        loader().bitmap("host-1", 7)

        assertThat(thread).isEqualTo(IO_THREAD)
    }

    @Test
    fun `art is not requested from a host that is not paired`() = runTest {
        binder.computers["host-1"] = pairedHost().also { it.pairState = PairState.NOT_PAIRED }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(clients.created).isEmpty()
    }

    @Test
    fun `art is not requested from a host with no pinned certificate`() = runTest {
        binder.computers["host-1"] = details(uuid = "host-1")

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(clients.created).isEmpty()
    }

    @Test
    fun `art is not requested from a host that is not known`() = runTest {
        binder.computers.clear()

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(clients.created).isEmpty()
    }

    @Test
    fun `two loads of the same art at once make one request`() = runTest {
        val loader = loader()
        val first = loader.prefetch("host-1", 7)

        val art = loader.bitmap("host-1", 7)
        first.join()

        assertThat(art).isNotNull()
        assertThat(clients.client.boxArtRequests).containsExactly(7)
    }

    private companion object {
        const val IO_THREAD = "box-art-io"
    }
}
