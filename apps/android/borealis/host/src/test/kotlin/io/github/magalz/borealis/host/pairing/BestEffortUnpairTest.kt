package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.Fixtures
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class BestEffortUnpairTest {
    // A client with the default pool, which would reuse a connection that is not marked as closed.
    // Retries are off: with them OkHttp silently repeats a request that failed on a poisoned
    // pooled connection on a fresh one, which would hide the missing `Connection: close`.
    private val client =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun ReplayServer.url(path: String): HttpUrl =
        "http://127.0.0.1:$port/$path?uniqueid=0123456789ABCDEF".toHttpUrl()

    private fun xmlResponse(status: Int, httpLine: String = "HTTP/1.1 200 OK"): ByteArray {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><root status_code=\"$status\"/>"
        return "$httpLine\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray()
    }

    private fun fixture(): ByteArray =
        Fixtures.file("luminalshine/26.09.1/unpair-http.response").readBytes()

    @Test
    fun `the 404 status in the LuminalShine fixture is a failure`() {
        ReplayServer(ReplayServer.Reply(fixture(), closeDelayMillis = 80)).use { server ->
            assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isFalse()
        }
    }

    @Test
    fun `stray bytes after the fixture never poison the next request on the same client`() {
        ReplayServer(
                ReplayServer.Reply(fixture(), closeDelayMillis = 80),
                ReplayServer.Reply(xmlResponse(200)),
            )
            .use { server ->
                BestEffortUnpair.unpair(client, server.url("unpair"))

                val next = Request.Builder().url(server.url("serverinfo")).build()
                val body = client.newCall(next).execute().use { it.body.string() }

                assertThat(body).contains("status_code=\"200\"")
                // The second request needed its own connection: the first one was not reused.
                assertThat(server.heads).hasSize(2)
            }
    }

    @Test
    fun `the request asks the host to close the connection`() {
        ReplayServer(ReplayServer.Reply(xmlResponse(200))).use { server ->
            BestEffortUnpair.unpair(client, server.url("unpair"))

            assertThat(server.heads.single().lowercase()).contains("connection: close")
        }
    }

    @Test
    fun `a status_code of 200 is a success`() {
        ReplayServer(ReplayServer.Reply(xmlResponse(200))).use { server ->
            assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isTrue()
        }
    }

    @Test
    fun `status_code 200 is read whatever the XML spelling`() {
        val bodies =
            listOf(
                "<root status_code='200'/>",
                "<root status_message=\"OK\" status_code=\"200\"/>",
                "<root status_code=\"200\" status_message=\"OK\"/>",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root status_code=\"200\"/>",
                "<root status_code = \"200\"></root>",
                "  \n<root\n  status_code\t=\t'200'\n>",
            )
        for (body in bodies) {
            val reply = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body"
            ReplayServer(ReplayServer.Reply(reply.toByteArray())).use { server ->
                assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isTrue()
            }
        }
    }

    @Test
    fun `any other status_code is a failure`() {
        for (status in listOf(0, 400, 401, 404, 500, 2000)) {
            ReplayServer(ReplayServer.Reply(xmlResponse(status))).use { server ->
                assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isFalse()
            }
        }
    }

    @Test
    fun `a non-2xx HTTP status is a failure even when the body says 200`() {
        val reply = xmlResponse(200, httpLine = "HTTP/1.1 404 NOT FOUND")
        ReplayServer(ReplayServer.Reply(reply)).use { server ->
            assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isFalse()
        }
    }

    @Test
    fun `a body without a readable status_code is a failure`() {
        val bodies =
            listOf(
                "",
                "not xml at all",
                "<root/>",
                "<root status_code=\"abc\"/>",
                "<root status_code=\"\"/>",
                "<root><status_code>200</status_code></root>",
                "<other status_code=\"200\"/>",
                "<root status_code=\"200\"",
            )
        for (body in bodies) {
            val reply = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body"
            ReplayServer(ReplayServer.Reply(reply.toByteArray())).use { server ->
                assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isFalse()
            }
        }
    }

    @Test
    fun `a connection reset is a failure and does not throw`() {
        ReplayServer(ReplayServer.Reply(ByteArray(0), reset = true)).use { server ->
            assertThat(BestEffortUnpair.unpair(client, server.url("unpair"))).isFalse()
        }
    }

    @Test
    fun `a refused connection is a failure and does not throw`() {
        val port = ServerSocket(0).use { it.localPort }
        val url = "http://127.0.0.1:$port/unpair".toHttpUrl()

        assertThat(BestEffortUnpair.unpair(client, url)).isFalse()
    }
}
