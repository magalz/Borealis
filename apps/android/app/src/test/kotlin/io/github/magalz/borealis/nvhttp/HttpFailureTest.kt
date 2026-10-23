package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.crypto.LimeLogCapture
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.providerOf
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.ServerSocket
import java.nio.charset.Charset
import java.util.logging.Level
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How `NvHTTP` reports a response that is not a success, a host that cannot be reached and what a
 * debug build logs about its requests (`verbose`, which is `BuildConfig.DEBUG`).
 */
@RunWith(RobolectricTestRunner::class)
class HttpFailureTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private val pinned = TestIdentities.hostRsa.certificate

    private fun verboseField() =
        NvHTTP::class.java.getDeclaredField("verbose").apply { isAccessible = true }

    private fun <T> withVerbose(value: Boolean, block: () -> T): T {
        val field = verboseField()
        val before = field.getBoolean(null)
        field.setBoolean(null, value)
        try {
            return block()
        } finally {
            field.setBoolean(null, before)
        }
    }

    @Test
    fun `a 404 of the HTTP layer is a FileNotFoundException that names the url`() {
        val host = host()
        host.answer("/serverinfo", HostReply.status(404, "Not Found", ByteArray(0)), "http")

        val error =
            assertThrows(FileNotFoundException::class.java) { clientOf(host).getServerInfo(true) }

        assertThat(error.message)
            .startsWith(
                "http://127.0.0.1:${host.httpPort}/serverinfo?uniqueid=$UNIQUE_ID&uuid="
            )
    }

    @Test
    fun `another HTTP status is a host error with its code and reason`() {
        for ((code, reason) in
            listOf(
                401 to "Unauthorized",
                403 to "Forbidden",
                500 to "Internal Server Error",
                502 to "Bad Gateway",
                503 to "Service Unavailable",
            )) {
            val host = host()
            host.answer("/serverinfo", HostReply.status(code, reason, "body".toByteArray()), "http")

            val error =
                assertThrows(HostHttpResponseException::class.java) {
                    clientOf(host).getServerInfo(true)
                }

            assertWithMessage("$code").that(error.errorCode).isEqualTo(code)
            assertWithMessage("$code").that(error.errorMessage).isEqualTo(reason)
            assertWithMessage("$code")
                .that(error.message)
                .isEqualTo("Host PC returned error: $reason (Error code: $code)")
        }
    }

    @Test
    fun `a 3xx answer that OkHttp does not follow is a host error`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.raw(
                "HTTP/1.1 304 Not Modified\r\nContent-Length: 0\r\n\r\n".toByteArray()
            ),
            "http",
        )

        val error =
            assertThrows(HostHttpResponseException::class.java) { clientOf(host).getServerInfo(true) }

        assertThat(error.errorCode).isEqualTo(304)
    }

    @Test
    fun `a 200 with the not found XML of a host without a route is returned as it is`() {
        for ((version, name) in LuminalFixtures.NOT_FOUND_RESPONSES) {
            val host = host()
            host.answer("/serverinfo", HostReply.raw(LuminalFixtures.bytes(version, name)), "http")
            val http = clientOf(host)

            val body = http.getServerInfo(true)

            assertWithMessage("$version $name")
                .that(body)
                .isEqualTo("""<?xml version="1.0" encoding="utf-8"?>""" + "\n" + """<root status_code="404"/>""")
            // The XML says what the HTTP layer did not.
            val error =
                assertThrows(HostHttpResponseException::class.java) { http.getComputerDetails(body) }
            assertWithMessage("$version $name").that(error.errorCode).isEqualTo(404)
        }
    }

    @Test
    fun `a host that cannot be reached is an IOException`() {
        val port = ServerSocket(0).use { it.localPort }
        val http =
            NvHTTP(
                AddressTuple("127.0.0.1", port),
                0,
                "id",
                null,
                providerOf(null, null, null),
            )

        assertThat(assertThrows(IOException::class.java) { http.getServerInfo(true) })
            .isInstanceOf(ConnectException::class.java)
        assertThat(assertThrows(IOException::class.java) { http.getServerInfo(false) })
            .isInstanceOf(ConnectException::class.java)
    }

    @Test
    fun `a debug build logs the url and the answer of every request but serverinfo`() {
        LimeLogCapture().use { log ->
            val host = host()
            host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")
            host.answer("/applist", HostReply.root("<App><ID>1</ID></App>"), "https")
            val http = clientOf(host, serverCert = pinned)

            withVerbose(true) {
                http.getServerInfo(true)
                assertThat(log.records).isEmpty()

                http.getAppListRaw()
            }

            val record = log.records.single()
            assertThat(record.first).isEqualTo(Level.INFO)
            assertThat(record.second)
                .startsWith("https://127.0.0.1:${host.httpsPort}/applist?uniqueid=$UNIQUE_ID&uuid=")
            assertThat(record.second)
                .endsWith(""" -> <root status_code="200"><App><ID>1</ID></App></root>""")
        }
    }

    @Test
    fun `a debug build logs the url and the error of a request that failed`() {
        LimeLogCapture().use { log ->
            val host = host()
            host.answer("/applist", HostReply.status(500, "Internal Server Error", ByteArray(0)), "https")
            val http = clientOf(host, serverCert = pinned)

            withVerbose(true) {
                assertThrows(HostHttpResponseException::class.java) { http.getAppListRaw() }
            }

            val record = log.records.single()
            assertThat(record.first).isEqualTo(Level.WARNING)
            assertThat(record.second)
                .startsWith("https://127.0.0.1:${host.httpsPort}/applist?uniqueid=$UNIQUE_ID&uuid=")
            assertThat(record.second)
                .endsWith(" -> Host PC returned error: Internal Server Error (Error code: 500)")
        }
    }

    @Test
    fun `a release build logs nothing about its requests`() {
        LimeLogCapture().use { log ->
            val host = host()
            host.answer("/applist", HostReply.root("<App><ID>1</ID></App>"), "https")
            host.answer("/appasset", HostReply.status(500, "Oops", ByteArray(0)), "https")
            val http = clientOf(host, serverCert = pinned)

            withVerbose(false) {
                http.getAppListRaw()
                assertThrows(HostHttpResponseException::class.java) {
                    http.getBoxArt(NvApp("a", 1, false))
                }
            }

            assertThat(log.records).isEmpty()
        }
    }

    @Test
    fun `a release build reads the app list from the stream, with the default charset`() {
        val host = host()
        host.answer(
            "/applist",
            HostReply.root("<App><AppTitle>Café Über</AppTitle><ID>5</ID></App>"),
            "https",
        )
        val http = clientOf(host, serverCert = pinned)

        val release = withVerbose(false) { http.getAppList() }
        val debug = withVerbose(true) { http.getAppList() }

        // A debug build reads the text of OkHttp, which is UTF-8; a release build reads the bytes
        // with the default charset of the platform.
        assertThat(debug.map { it.appName }).containsExactly("Café Über")
        assertThat(release.map { it.appName })
            .containsExactly(
                String("Café Über".toByteArray(Charsets.UTF_8), Charset.defaultCharset())
            )
        assertThat(release.map { it.appId }).containsExactly(5)
    }

    @Test
    fun `the app list that a host refuses raises its error in both builds`() {
        val host = host()
        host.answer("/applist", HostReply.failure("401", "denied"), "https")
        val http = clientOf(host, serverCert = pinned)

        for (verbose in listOf(true, false)) {
            val error =
                assertThrows(HostHttpResponseException::class.java) {
                    withVerbose(verbose) { http.getAppList() }
                }

            assertWithMessage("verbose $verbose").that(error.errorCode).isEqualTo(401)
        }
    }
}
