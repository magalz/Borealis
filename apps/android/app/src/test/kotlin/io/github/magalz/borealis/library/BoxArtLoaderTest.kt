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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
    private val io = Executors.newFixedThreadPool(4) { Thread(it, IO_THREAD) }
    private val dispatcher = io.asCoroutineDispatcher()
    private val disk = DiskAssetLoader(context)
    private val png = png()

    private fun loader(fetchTimeoutMs: Long = 20_000L): BoxArtLoader =
        BoxArtLoader(
            context,
            ComputerManagerConnection(FakeCmsConnector(binder), dispatcher),
            clients,
            dispatcher,
            fetchTimeoutMs,
        )

    /** The files of the cache directory of the host, temporary ones included. */
    private fun cacheFiles(): List<String> =
        disk.getFile("host-1", 7).parentFile?.list()?.toList().orEmpty()

    /** A PNG of [width]x[height] that is a single colour: small, whatever its size. */
    private fun pngOf(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

    /** A host that sends the first half of [body], then waits for [release], then the rest. */
    private fun slowArt(body: ByteArray, halfSent: CountDownLatch, release: CountDownLatch) =
        object : InputStream() {
            private val stream = ByteArrayInputStream(body)
            private var sent = 0

            override fun read(): Int = throw UnsupportedOperationException()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (sent >= body.size / 2) {
                    halfSent.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                val count =
                    stream.read(
                        buffer,
                        offset,
                        minOf(length, body.size / 2 - sent).coerceAtLeast(1),
                    )
                if (count > 0) sent += count
                return count
            }
        }

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
        clients.client.boxArtAnswer = { ByteArrayInputStream(png) }
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
    fun `art that is cached makes no request, also for a loader that did not fetch it`() = runTest {
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
        clients.client.boxArtAnswer = { throw FileNotFoundException("404") }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `a network failure gives no art instead of throwing`() = runTest {
        clients.client.boxArtAnswer = { throw IOException("connection reset") }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `a stream that breaks half way leaves no partial file`() = runTest {
        clients.client.boxArtAnswer = {
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
        clients.client.boxArtAnswer = { ByteArrayInputStream("this is not an image".toByteArray()) }
        val loader = loader()

        assertThat(loader.bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()

        clients.client.boxArtAnswer = { ByteArrayInputStream(png) }
        assertThat(loader.bitmap("host-1", 7)).isNotNull()
        assertThat(clients.client.boxArtRequests).hasSize(2)
    }

    @Test
    fun `art larger than 5 MB is rejected and not stored`() = runTest {
        clients.client.boxArtAnswer = { ByteArrayInputStream(ByteArray(6 * 1024 * 1024)) }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `the request is made off the calling thread`() = runTest {
        var thread: String? = null
        clients.client.boxArtAnswer = {
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

    @Test
    fun `art that is still downloading is not in the cache, and a leftover is not kept`() =
        runTest {
            val halfSent = CountDownLatch(1)
            val release = CountDownLatch(1)
            clients.client.boxArtAnswer = { slowArt(png, halfSent, release) }
            val loading = async(dispatcher) { loader().bitmap("host-1", 7) }
            assertThat(halfSent.await(5, TimeUnit.SECONDS)).isTrue()

            try {
                // The poster provider and the next caller look for this file.
                assertThat(cached(7)).isFalse()
                assertThat(disk.getFile("host-1", 7).exists()).isFalse()
            } finally {
                // Whatever happened above, the download must end before the test does.
                release.countDown()
            }
            assertThat(loading.await()).isNotNull()
            assertThat(disk.getFile("host-1", 7).readBytes()).isEqualTo(png)
            assertThat(cacheFiles()).containsExactly("7.png")
        }

    @Test
    fun `a second caller waits for the download and decodes the whole file`() = runTest {
        val halfSent = CountDownLatch(1)
        val release = CountDownLatch(1)
        clients.client.boxArtAnswer = { slowArt(png, halfSent, release) }
        val loader = loader()
        val first = async(dispatcher) { loader.bitmap("host-1", 7) }
        assertThat(halfSent.await(5, TimeUnit.SECONDS)).isTrue()

        val second = async(dispatcher) { loader.bitmap("host-1", 7) }
        try {
            // A bounded wait for the second caller to act: it must not finish before the download.
            repeat(15) { if (!second.isCompleted) withContext(dispatcher) { delay(10) } }
            assertThat(second.isCompleted).isFalse()
        } finally {
            release.countDown()
        }

        assertThat(first.await()).isNotNull()
        assertThat(second.await()).isNotNull()
        assertThat(clients.client.boxArtRequests).containsExactly(7)
        assertThat(disk.getFile("host-1", 7).readBytes()).isEqualTo(png)
    }

    @Test
    fun `a download that breaks leaves neither the art nor a temporary file`() = runTest {
        clients.client.boxArtAnswer = {
            object : InputStream() {
                private var sent = 0

                override fun read(): Int {
                    if (sent++ > 100) throw IOException("connection reset")
                    return 0
                }
            }
        }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cacheFiles()).isEmpty()
    }

    @Test
    fun `art over the size limit leaves no temporary file`() = runTest {
        clients.client.boxArtAnswer = { ByteArrayInputStream(ByteArray(6 * 1024 * 1024)) }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cacheFiles()).isEmpty()
    }

    @Test
    fun `a host too slow to send the art is given up on, and the next fetch is not held up`() =
        runTest {
            var interrupted = false
            clients.client.boxArtAnswer = {
                object : InputStream() {
                    override fun read(): Int = throw UnsupportedOperationException()

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        try {
                            // Far longer than the deadline of this test; it ends in an error
                            // anyway.
                            CountDownLatch(1).await(3, TimeUnit.SECONDS)
                        } catch (e: InterruptedException) {
                            interrupted = true
                            throw e
                        }
                        throw IOException("too late")
                    }
                }
            }
            val loader = loader(fetchTimeoutMs = 200)

            assertThat(loader.bitmap("host-1", 7)).isNull()
            assertThat(interrupted).isTrue()

            clients.client.boxArtAnswer = { ByteArrayInputStream(png) }
            assertThat(loader.bitmap("host-1", 7)).isNotNull()
        }

    @Test
    fun `art wider or taller than 8192 px is rejected without decoding`() = runTest {
        clients.client.boxArtAnswer = { ByteArrayInputStream(pngOf(8193, 1)) }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    @Test
    fun `art of more than 16 megapixels is rejected without decoding`() = runTest {
        clients.client.boxArtAnswer = { ByteArrayInputStream(pngOf(4100, 4100)) }

        assertThat(loader().bitmap("host-1", 7)).isNull()
        assertThat(cached(7)).isFalse()
    }

    private companion object {
        const val IO_THREAD = "box-art-io"
    }
}
