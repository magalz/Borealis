package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.crypto.TestIdentities
import java.io.Closeable
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The three OkHttp clients of `NvHTTP`: their timeouts, the pool and dispatcher they share, and
 * which request uses which. A request that waits for a person (the PIN, a launch) has no read
 * timeout; one for a host that may be gone has the short connect timeout. The tests read the
 * private fields by name (`httpClientLongConnectTimeout`, `httpClientShortConnectTimeout` and
 * `httpClientLongConnectNoReadTimeout`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowNvMoonBridge::class])
class ClientConfigurationTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private val pinned = TestIdentities.hostRsa.certificate

    private fun NvHTTP.client(field: String): OkHttpClient =
        NvHTTP::class.java.getDeclaredField(field).apply { isAccessible = true }.get(this)
            as OkHttpClient

    /** Replaces each client by one that notes its name in [seen] for every request it sends. */
    private fun NvHTTP.watch(seen: MutableList<String>) {
        for ((field, label) in
            listOf(
                "httpClientLongConnectTimeout" to "long",
                "httpClientShortConnectTimeout" to "short",
                "httpClientLongConnectNoReadTimeout" to "noRead",
            )) {
            val reflected = NvHTTP::class.java.getDeclaredField(field).apply { isAccessible = true }
            val watched =
                client(field)
                    .newBuilder()
                    .addInterceptor { chain ->
                        seen += "$label ${chain.request().url.encodedPath}"
                        chain.proceed(chain.request())
                    }
                    .build()
            reflected.set(this, watched)
        }
    }

    private fun clientsOf(http: NvHTTP) =
        listOf(
            "httpClientLongConnectTimeout",
            "httpClientShortConnectTimeout",
            "httpClientLongConnectNoReadTimeout",
        ).map { http.client(it) }

    @Test
    fun `the long client waits 5 s to connect and 7 s to read`() {
        val long = clientOf(host()).client("httpClientLongConnectTimeout")

        assertThat(long.connectTimeoutMillis).isEqualTo(5000)
        assertThat(long.readTimeoutMillis).isEqualTo(7000)
    }

    @Test
    fun `the short client waits 3 s to connect and 7 s to read`() {
        val short = clientOf(host()).client("httpClientShortConnectTimeout")

        assertThat(short.connectTimeoutMillis).isEqualTo(3000)
        assertThat(short.readTimeoutMillis).isEqualTo(7000)
    }

    @Test
    fun `the client of the requests that wait for a person has no read timeout`() {
        val noRead = clientOf(host()).client("httpClientLongConnectNoReadTimeout")

        assertThat(noRead.connectTimeoutMillis).isEqualTo(5000)
        assertThat(noRead.readTimeoutMillis).isEqualTo(0)
    }

    @Test
    fun `no client uses a proxy or the happy eyeballs fallback, and all verify the host the same way`() {
        val http = clientOf(host())
        val clients = clientsOf(http)

        for (client in clients) {
            assertThat(client.proxy).isEqualTo(Proxy.NO_PROXY)
            assertThat(client.fastFallback).isFalse()
        }
        assertThat(clients.map { it.hostnameVerifier }.distinct()).hasSize(1)
        assertThat(clients.map { it.connectionPool }.distinct()).hasSize(1)
        assertThat(clients.map { it.dispatcher }.distinct()).hasSize(1)
    }

    @Test
    fun `the pool keeps no idle connection`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host)

        http.getServerInfo(true)

        assertThat(http.client("httpClientLongConnectTimeout").connectionPool.connectionCount())
            .isEqualTo(0)
    }

    @Test
    fun `each request uses the client of its kind`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody(httpsPort = host.httpsPort)),
            "http",
        )
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")
        host.answer("/applist", HostReply.root("<App><ID>1</ID></App>"), "https")
        host.answer("/appasset", HostReply.xml("png"), "https")
        host.answer("/launch", HostReply.root("<gamesession>1</gamesession>"), "https")
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer("/unpair", HostReply.xml("""<root status_code="200"/>"""), "http")
        host.answer("/pair", HostReply.root("<paired>0</paired>"), "http")
        val seen = CopyOnWriteArrayList<String>()
        val pinnedHttp = clientOf(host, serverCert = pinned)
        val plainHttp = clientOf(host)
        val unknownPort = clientOf(host, serverCert = pinned, httpsKnown = false)
        pinnedHttp.watch(seen)
        plainHttp.watch(seen)
        unknownPort.watch(seen)

        plainHttp.getServerInfo(true)
        plainHttp.getServerInfo(false)
        pinnedHttp.getServerInfo(true)
        pinnedHttp.getServerInfo(false)
        // The port is read with the client that the caller asks for, before the HTTPS request.
        unknownPort.getServerInfo(false)
        pinnedHttp.getAppListRaw()
        pinnedHttp.getAppList()
        pinnedHttp.getBoxArt(NvApp("a", 1, false)).close()
        plainHttp.unpair()
        plainHttp.pairingManager.pair(
            """<root status_code="200"><appversion>7.1.431.0</appversion></root>""",
            "1234",
        )

        assertThat(seen)
            .containsExactly(
                "long /serverinfo",
                "short /serverinfo",
                "long /serverinfo",
                "short /serverinfo",
                "short /serverinfo",
                "short /serverinfo",
                "long /applist",
                "long /applist",
                "long /appasset",
                "long /unpair",
                "noRead /pair",
            )
            .inOrder()
    }

    @Test
    fun `a launch and a cancel use the client without a read timeout`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")
        host.answer("/launch", HostReply.root("<gamesession>1</gamesession>"), "https")
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        val seen = CopyOnWriteArrayList<String>()
        val http = clientOf(host, serverCert = pinned)
        http.watch(seen)
        val context =
            ConnectionContext().also {
                it.negotiatedWidth = 1920
                it.negotiatedHeight = 1080
                it.riKey = SecretKeySpec(ByteArray(16), "AES")
                it.streamConfig = StreamConfiguration.Builder().build()
            }

        http.launchApp(context, "launch", 1, false)
        http.quitApp()

        // The serverinfo after the cancel is a plain query: it times out.
        assertThat(seen)
            .containsExactly("noRead /launch", "noRead /cancel", "long /serverinfo")
            .inOrder()
    }

    @Test
    fun `cancelling ends a pairing that waits for the PIN on the host`() {
        val host = host()
        host.answer("/pair", HostReply.HOLD, "http")
        val http = clientOf(host)
        val failure = AtomicReference<Throwable?>()
        val finished = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                http.pairingManager.pair(
                    """<root status_code="200"><appversion>7.1.431.0</appversion></root>""",
                    "1234",
                )
            } catch (e: IOException) {
                failure.set(e)
            } finally {
                finished.countDown()
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (host.requests.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertThat(host.requests.map { it.path }).containsExactly("/pair")
        assertThat(finished.await(300, TimeUnit.MILLISECONDS)).isFalse()

        http.cancelPendingRequests()

        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(failure.get()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `cancelling with nothing pending leaves the client usable`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host)

        http.cancelPendingRequests()

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())
    }
}
