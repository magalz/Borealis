package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.providerOf
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

/**
 * The requests of `NvHTTP` as a host sees them: which path, over which scheme, with which
 * parameters in which order. Every request carries `uniqueid` and `uuid` last.
 */
@RunWith(RobolectricTestRunner::class)
class HostRequestsTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private val pinned = TestIdentities.hostRsa.certificate

    private fun SeenRequest.assertCarriesTheIds() {
        assertThat(value("uniqueid")).isEqualTo(UNIQUE_ID)
        // The uuid is random for every request.
        UUID.fromString(checkNotNull(value("uuid")))
        assertThat(names.takeLast(2)).containsExactly("uniqueid", "uuid").inOrder()
    }

    @Test
    fun `without a pinned certificate serverinfo is one plain HTTP request`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())
        assertThat(http.getServerInfo(false)).isEqualTo(serverInfoBody())

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo", "http /serverinfo")
            .inOrder()
        host.requests.forEach {
            assertThat(it.names).containsExactly("uniqueid", "uuid").inOrder()
            it.assertCarriesTheIds()
        }
        assertThat(host.requests[0].value("uuid")).isNotEqualTo(host.requests[1].value("uuid"))
    }

    @Test
    fun `the unique id of every request is the shared one, not the one the caller passes`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http =
            NvHTTP(
                AddressTuple("127.0.0.1", host.httpPort),
                0,
                "FEDCBA9876543210",
                null,
                providerOf(TestIdentities.clientRsa),
            )

        http.getServerInfo(true)

        assertThat(host.requests.single().value("uniqueid")).isEqualTo("0123456789ABCDEF")
    }

    @Test
    fun `the details of a host come from its serverinfo`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody(paired = true)), "http")
        val http = clientOf(host)

        val details = http.getComputerDetails(true)

        assertThat(details.uuid).isEqualTo("00000000-0000-0000-0000-000000000001")
        assertThat(details.pairState).isEqualTo(PairState.PAIRED)
        assertThat(http.getPairState()).isEqualTo(PairState.PAIRED)
        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo", "http /serverinfo")
            .inOrder()
    }

    @Test
    fun `with a pinned certificate and a known HTTPS port serverinfo is asked over HTTPS only`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody(paired = true)), "https")
        val http = clientOf(host, serverCert = pinned)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody(paired = true))

        val request = host.requests.single()
        assertThat(request.scheme).isEqualTo("https")
        assertThat(request.names).containsExactly("uniqueid", "uuid").inOrder()
        request.assertCarriesTheIds()
        // The client presents its certificate during the handshake.
        assertThat(request.clientCertificates).containsExactly(TestIdentities.clientRsa.certificate)
        assertThat(http.sawCertificateMismatch()).isFalse()
    }

    @Test
    fun `with a pinned certificate and no HTTPS port the port is read over HTTP first and kept`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody(httpsPort = host.httpsPort)),
            "http",
        )
        host.answer("/serverinfo", HostReply.xml(serverInfoBody(paired = true)), "https")
        val http = clientOf(host, serverCert = pinned, httpsKnown = false)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody(paired = true))
        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody(paired = true))

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo", "https /serverinfo", "https /serverinfo")
            .inOrder()
    }

    @Test
    fun `the HTTPS url is built on the host of the client and the port of the host`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody(httpsPort = 47990)),
            "http",
        )
        val http = clientOf(host, httpsKnown = false)

        val first = http.getHttpsUrl(true)
        val second = http.getHttpsUrl(false)

        assertThat(first.toString()).isEqualTo("https://127.0.0.1:47990/")
        assertThat(second.toString()).isEqualTo("https://127.0.0.1:47990/")
        // The port is fetched once.
        assertThat(host.requests).hasSize(1)
        assertThat(clientOf(host).getHttpsUrl(true).port).isEqualTo(host.httpsPort)
    }

    @Test
    fun `a host that gives no HTTPS port gets the default 47984`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody().replace("<HttpsPort>47984</HttpsPort>", "")),
            "http",
        )

        assertThat(clientOf(host, httpsKnown = false).getHttpsUrl(true).port).isEqualTo(47984)
    }

    @Test
    fun `the app list is asked over HTTPS`() {
        val host = host()
        host.answer(
            "/applist",
            HostReply.root(
                "<App><IsHdrSupported>1</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>10</ID></App>" +
                    "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Steam</AppTitle><ID>20</ID></App>"
            ),
            "https",
        )
        val http = clientOf(host, serverCert = pinned)

        val apps = http.getAppList()

        assertThat(apps.map { it.appName }).containsExactly("Desktop", "Steam").inOrder()
        assertThat(apps.map { it.appId }).containsExactly(10, 20).inOrder()
        assertThat(apps.map { it.isHdrSupported }).containsExactly(true, false).inOrder()
        val request = host.requests.single()
        assertThat(request.scheme).isEqualTo("https")
        assertThat(request.path).isEqualTo("/applist")
        assertThat(request.names).containsExactly("uniqueid", "uuid").inOrder()
        request.assertCarriesTheIds()
    }

    @Test
    fun `the raw app list is the body of the answer`() {
        val host = host()
        val body = """<root status_code="200"><App><ID>1</ID></App></root>"""
        host.answer("/applist", HostReply.xml(body), "https")

        assertThat(clientOf(host, serverCert = pinned).getAppListRaw()).isEqualTo(body)
    }

    @Test
    fun `an app is found by its id and by its name without regard to case`() {
        val host = host()
        host.answer(
            "/applist",
            HostReply.root(
                "<App><AppTitle>Desktop</AppTitle><ID>10</ID></App>" +
                    "<App><AppTitle>Same</AppTitle><ID>20</ID></App>" +
                    "<App><AppTitle>same</AppTitle><ID>30</ID></App>"
            ),
            "https",
        )
        val http = clientOf(host, serverCert = pinned)

        assertThat(http.getAppById(20)?.appName).isEqualTo("Same")
        assertThat(http.getAppById(21)).isNull()
        assertThat(http.getAppByName("DESKTOP")?.appId).isEqualTo(10)
        // The first of two apps with the same name.
        assertThat(http.getAppByName("SAME")?.appId).isEqualTo(20)
        assertThat(http.getAppByName("Missing")).isNull()
        // Each lookup asks the host again.
        assertThat(host.requests).hasSize(5)
    }

    @Test
    fun `the box art is asked over HTTPS for the id of the app and handed back as a stream`() {
        val host = host()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 1, 2, 3)
        host.answer("/appasset", HostReply.status(200, "OK", png, "image/png"), "https")
        val http = clientOf(host, serverCert = pinned)

        val bytes = http.getBoxArt(NvApp("Desktop", 1234, false)).use { it.readBytes() }

        assertThat(bytes).isEqualTo(png)
        val request = host.requests.single()
        assertThat(request.scheme).isEqualTo("https")
        assertThat(request.path).isEqualTo("/appasset")
        assertThat(request.names)
            .containsExactly("appid", "AssetType", "AssetIdx", "uniqueid", "uuid")
            .inOrder()
        assertThat(request.value("appid")).isEqualTo("1234")
        assertThat(request.value("AssetType")).isEqualTo("2")
        assertThat(request.value("AssetIdx")).isEqualTo("0")
        request.assertCarriesTheIds()
    }

    @Test
    fun `an unpair is one plain HTTP request that asks to close the connection`() {
        val host = host()
        host.answer("/unpair", HostReply.xml("""<root status_code="200"/>"""), "http")
        val http = clientOf(host)

        assertThat(http.unpair()).isTrue()

        val request = host.requests.single()
        assertThat(request.scheme).isEqualTo("http")
        assertThat(request.path).isEqualTo("/unpair")
        assertThat(request.names).containsExactly("uniqueid", "uuid").inOrder()
        request.assertCarriesTheIds()
        assertThat(request.headers["connection"]).isEqualTo("close")
    }

    @Test
    fun `the unpair that a host does not route is not confirmed, as the hosts frame it`() {
        for ((version, name) in LuminalFixtures.NOT_FOUND_RESPONSES) {
            val host = host()
            host.answer("/unpair", HostReply.raw(LuminalFixtures.bytes(version, name)), "http")

            assertWithMessage("$version $name").that(clientOf(host).unpair()).isFalse()

            assertWithMessage("$version $name").that(host.requests).hasSize(1)
        }
    }

    @Test
    fun `an unpair that cannot reach the host is not an exception`() {
        val host = host()
        val http = clientOf(host)
        host.close()

        assertThat(http.unpair()).isFalse()
    }

    @Test
    fun `the first pairing request carries the phrase, the salt and the certificate in this order`() {
        val host = host()
        host.answer("/pair", HostReply.root("<paired>0</paired>"), "http")
        val http = clientOf(host)

        val result =
            http.pairingManager.pair(
                """<root status_code="200"><appversion>7.1.431.0</appversion></root>""",
                "1234",
            )

        assertThat(result).isEqualTo(PairState.FAILED)
        val request = host.requests.single()
        assertThat(request.scheme).isEqualTo("http")
        assertThat(request.path).isEqualTo("/pair")
        assertThat(request.names)
            .containsExactly(
                "devicename",
                "updateState",
                "phrase",
                "salt",
                "clientcert",
                "uniqueid",
                "uuid",
            )
            .inOrder()
        assertThat(request.value("devicename")).isEqualTo("roth")
        assertThat(request.value("updateState")).isEqualTo("1")
        assertThat(request.value("phrase")).isEqualTo("getservercert")
        request.assertCarriesTheIds()
    }

    @Test
    fun `an IPv4-mapped IPv6 address is written as IPv4`() {
        val http =
            NvHTTP(
                AddressTuple("::ffff:192.0.2.5", HTTP_PORT),
                47984,
                "id",
                null,
                providerOf(null, null, null),
            )

        assertThat(http.getHttpsUrl(true).toString()).isEqualTo("https://192.0.2.5:47984/")
    }

    @Test
    fun `other addresses keep their form`() {
        fun urlOf(address: String) =
            NvHTTP(AddressTuple(address, HTTP_PORT), 47984, "id", null, providerOf(null, null, null))
                .getHttpsUrl(true)
                .toString()

        assertThat(urlOf("192.0.2.5")).isEqualTo("https://192.0.2.5:47984/")
        assertThat(urlOf("2001:db8::1")).isEqualTo("https://[2001:db8::1]:47984/")
        assertThat(urlOf("[2001:db8::1]")).isEqualTo("https://[2001:db8::1]:47984/")
        assertThat(urlOf("Den-PC.local")).isEqualTo("https://den-pc.local:47984/")
        // An address with a dot and a colon that is not IPv4-mapped is IPv6 as given.
        assertThat(urlOf("2001:db8::192.0.2.5")).isEqualTo("https://[2001:db8::c000:205]:47984/")
    }

    @Test
    fun `an address that is not a host is an IOException`() {
        for (address in listOf("bad host", "a/b", "")) {
            val error =
                assertThrows(IOException::class.java) {
                    NvHTTP(
                        AddressTuple(address, HTTP_PORT),
                        0,
                        "id",
                        null,
                        providerOf(null, null, null),
                    )
                }

            assertWithMessage("'$address'")
                .that(error.cause)
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `a host that leaves the connection open gets one connection per request`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host)

        http.getServerInfo(true)
        http.getServerInfo(true)
        http.getServerInfo(false)

        // The connection pool keeps no idle connection.
        assertThat(host.connections).isEqualTo(3)
    }

    @Test
    fun `the mandatory serverinfo of the HTTPS answer is checked and its absence is not hidden`() {
        val host = host()
        host.answer("/serverinfo", HostReply.root("<hostname>no version</hostname>"), "https")

        val error =
            assertThrows(XmlPullParserException::class.java) {
                clientOf(host, serverCert = pinned).getServerInfo(true)
            }

        assertThat(error.message).contains("appversion")
        // The XML error is not a certificate error: no fallback to HTTP.
        assertThat(host.requests.map { it.scheme }).containsExactly("https")
    }
}
