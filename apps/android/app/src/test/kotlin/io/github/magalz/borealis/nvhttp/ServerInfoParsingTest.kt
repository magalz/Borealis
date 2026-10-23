package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.PairingManager.PairState
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

/**
 * What `NvHTTP` reads out of a `/serverinfo` answer: the captures of the two LuminalShine releases
 * and answers written after the host contract (`docs/host-contract/luminalshine.md`).
 */
@RunWith(RobolectricTestRunner::class)
class ServerInfoParsingTest {
    private val http = parsingClient(port = PORT)

    private fun details(serverInfo: String): ComputerDetails = http.getComputerDetails(serverInfo)

    /** The message of the parser exception that [block] ends with. */
    private fun failure(block: () -> Any?): String =
        assertThrows(XmlPullParserException::class.java) { block() }.message.orEmpty()

    @Test
    fun `a captured serverinfo of each LuminalShine release gives the details of the host`() {
        for (version in LuminalFixtures.VERSIONS) {
            val details = details(LuminalFixtures.serverInfo(version))

            assertWithMessage(version).that(details.name).isEqualTo("LUMINALSHINE-HOST")
            assertWithMessage(version)
                .that(details.uuid)
                .isEqualTo("00000000-0000-0000-0000-000000000001")
            assertWithMessage(version).that(details.httpsPort).isEqualTo(47984)
            assertWithMessage(version).that(details.macAddress).isEqualTo("00:00:00:00:00:00")
            // The local address takes the port that the client uses, not the one of the host.
            assertWithMessage(version)
                .that(details.localAddress)
                .isEqualTo(AddressTuple("192.0.2.10", PORT))
            assertWithMessage(version).that(details.externalPort).isEqualTo(47989)
            assertWithMessage(version).that(details.remoteAddress).isNull()
            assertWithMessage(version).that(details.pairState).isEqualTo(PairState.NOT_PAIRED)
            assertWithMessage(version).that(details.runningGameId).isEqualTo(0)
            assertWithMessage(version).that(details.nvidiaServer).isFalse()
            assertWithMessage(version).that(details.state).isEqualTo(ComputerDetails.State.ONLINE)
            assertWithMessage(version).that(details.manualAddress).isNull()
            assertWithMessage(version).that(details.ipv6Address).isNull()
            assertWithMessage(version).that(details.activeAddress).isNull()
            assertWithMessage(version).that(details.serverCert).isNull()
            assertWithMessage(version).that(details.rawAppList).isNull()
        }
    }

    @Test
    fun `the values of a captured serverinfo are read one by one`() {
        for (version in LuminalFixtures.VERSIONS) {
            val serverInfo = LuminalFixtures.serverInfo(version)

            assertWithMessage(version).that(http.getServerVersion(serverInfo)).isEqualTo("7.1.431.-1")
            assertWithMessage(version)
                .that(http.getServerAppVersionQuad(serverInfo))
                .asList()
                .containsExactly(7, 1, 431, -1)
                .inOrder()
            assertWithMessage(version).that(http.getServerMajorVersion(serverInfo)).isEqualTo(7)
            assertWithMessage(version).that(http.getGfeVersion(serverInfo)).isEqualTo("3.23.0.74")
            assertWithMessage(version).that(http.getGpuType(serverInfo)).isNull()
            assertWithMessage(version).that(http.supports4K(serverInfo)).isTrue()
            assertWithMessage(version)
                .that(http.getMaxLumaPixelsHEVC(serverInfo))
                .isEqualTo(1869449984L)
            assertWithMessage(version).that(http.getMaxLumaPixelsH264(serverInfo)).isEqualTo(0L)
            assertWithMessage(version)
                .that(http.getServerCodecModeSupport(serverInfo))
                .isEqualTo(1835777L)
            assertWithMessage(version).that(http.getCurrentGame(serverInfo)).isEqualTo(0)
            assertWithMessage(version).that(http.getHttpsPort(serverInfo)).isEqualTo(47984)
            assertWithMessage(version).that(http.getExternalPort(serverInfo)).isEqualTo(47989)
            assertWithMessage(version)
                .that(http.getPairState(serverInfo))
                .isEqualTo(PairState.NOT_PAIRED)
        }
    }

    @Test
    fun `the codec mode support is a long, so a mask above the int range is read in full`() {
        for ((text, expected) in
            listOf(
                "4294967295" to 4294967295L,
                "2147483648" to 2147483648L,
                "2147483647" to 2147483647L,
                "0" to 0L,
                "-1" to -1L,
            )) {
            val serverInfo = serverInfoWith("ServerCodecModeSupport", text)

            assertWithMessage(text)
                .that(http.getServerCodecModeSupport(serverInfo))
                .isEqualTo(expected)
        }
    }

    @Test
    fun `an absent codec mode support is 0 and an unreadable one is a NumberFormatException`() {
        assertThat(http.getServerCodecModeSupport(serverInfoWithout("ServerCodecModeSupport")))
            .isEqualTo(0L)
        for (text in listOf("abc", " 5", "0x10", "1.5", "18446744073709551616")) {
            assertWithMessage(text).that(
                assertThrows(NumberFormatException::class.java) {
                    http.getServerCodecModeSupport(serverInfoWith("ServerCodecModeSupport", text))
                }
            ).isNotNull()
        }
    }

    @Test
    fun `a host with a codec mask above the int range or an unreadable one still gives its details`() {
        for (text in listOf("4294967295", "abc")) {
            val details = details(serverInfoWith("ServerCodecModeSupport", text))

            assertWithMessage(text).that(details.uuid).isNotNull()
            assertWithMessage(text).that(details.state).isEqualTo(ComputerDetails.State.ONLINE)
        }
    }

    @Test
    fun `the luma limits are longs that default to 0`() {
        val serverInfo =
            serverInfoWith("MaxLumaPixelsHEVC", "8589934592").replace(
                "<currentgame>",
                "<MaxLumaPixelsH264>2073600</MaxLumaPixelsH264><currentgame>",
            )

        assertThat(http.getMaxLumaPixelsHEVC(serverInfo)).isEqualTo(8589934592L)
        assertThat(http.getMaxLumaPixelsH264(serverInfo)).isEqualTo(2073600L)
        assertThat(http.getMaxLumaPixelsHEVC(serverInfoWithout("MaxLumaPixelsHEVC"))).isEqualTo(0L)
        assertThat(http.getMaxLumaPixelsH264(serverInfoWithout("MaxLumaPixelsH264"))).isEqualTo(0L)
        assertThrows(NumberFormatException::class.java) {
            http.getMaxLumaPixelsHEVC(serverInfoWith("MaxLumaPixelsHEVC", "many"))
        }
        assertThrows(NumberFormatException::class.java) {
            http.getMaxLumaPixelsH264(
                serverInfoWith("MaxLumaPixelsHEVC", "1")
                    .replace("<currentgame>", "<MaxLumaPixelsH264>many</MaxLumaPixelsH264><currentgame>")
            )
        }
    }

    @Test
    fun `the running game is read only while the host is busy`() {
        assertThat(http.getCurrentGame(serverInfoBody("SUNSHINE_SERVER_BUSY", currentGame = 123)))
            .isEqualTo(123)
        assertThat(http.getCurrentGame(serverInfoBody("MJOLNIR_SERVER_BUSY", currentGame = 7)))
            .isEqualTo(7)
        // GFE 2.8 keeps the last game in currentgame after the stream ended.
        assertThat(http.getCurrentGame(serverInfoBody("SUNSHINE_SERVER_FREE", currentGame = 99)))
            .isEqualTo(0)
        assertThat(http.getCurrentGame(serverInfoBody("SERVER_BUSY_NOT", currentGame = 99)))
            .isEqualTo(0)
    }

    @Test
    fun `a busy host without a readable current game fails`() {
        val busy = serverInfoBody("SUNSHINE_SERVER_BUSY", currentGame = 5)

        assertThat(
                assertThrows(XmlPullParserException::class.java) {
                        http.getCurrentGame(busy.replace("<currentgame>5</currentgame>", ""))
                    }
                    .message
            )
            .isEqualTo("Missing mandatory field in host response: currentgame")
        // currentgame is an int: an id above Integer.MAX_VALUE does not fit.
        assertThrows(NumberFormatException::class.java) {
            http.getCurrentGame(busy.replace("<currentgame>5<", "<currentgame>4294967295<"))
        }
        assertThrows(NumberFormatException::class.java) {
            http.getCurrentGame(busy.replace("<currentgame>5<", "<currentgame>x<"))
        }
        assertThat(
                assertThrows(XmlPullParserException::class.java) {
                        http.getCurrentGame(serverInfoWithout("state"))
                    }
                    .message
            )
            .isEqualTo("Missing mandatory field in host response: state")
    }

    @Test
    fun `a host that is busy with a running game gives the id of the game`() {
        val details = details(serverInfoBody("SUNSHINE_SERVER_BUSY", currentGame = 42))

        assertThat(details.runningGameId).isEqualTo(42)
        assertThat(details.nvidiaServer).isFalse()
    }

    @Test
    fun `a state that contains MJOLNIR marks the host as an Nvidia server`() {
        assertThat(details(serverInfoBody("MJOLNIR_STATE_FREE")).nvidiaServer).isTrue()
        assertThat(details(serverInfoBody("SUNSHINE_SERVER_FREE")).nvidiaServer).isFalse()
        assertThat(details(serverInfoBody("mjolnir_free")).nvidiaServer).isFalse()
    }

    @Test
    fun `the pair status is paired only for 1`() {
        assertThat(http.getPairState(serverInfoBody(paired = true))).isEqualTo(PairState.PAIRED)
        assertThat(http.getPairState(serverInfoBody(paired = false)))
            .isEqualTo(PairState.NOT_PAIRED)
        assertThat(
                http.getPairState(serverInfoWith("PairStatus", "2"))
            )
            .isEqualTo(PairState.NOT_PAIRED)
        assertThat(http.getPairState(serverInfoWith("PairStatus", "01")))
            .isEqualTo(PairState.NOT_PAIRED)
        assertThat(details(serverInfoBody(paired = true)).pairState).isEqualTo(PairState.PAIRED)
        assertThat(
                assertThrows(XmlPullParserException::class.java) {
                        http.getPairState(serverInfoWithout("PairStatus"))
                    }
                    .message
            )
            .isEqualTo("Missing mandatory field in host response: PairStatus")
    }

    @Test
    fun `the HTTPS port is 47984 when the host does not give a usable one`() {
        assertThat(http.getHttpsPort(serverInfoBody(httpsPort = 47985))).isEqualTo(47985)
        assertThat(http.getHttpsPort(serverInfoWithout("HttpsPort"))).isEqualTo(47984)
        assertThat(http.getHttpsPort("""<root status_code="401" status_message="no"/>"""))
            .isEqualTo(47984)
        assertThat(http.getHttpsPort("<root status_code=")).isEqualTo(47984)
        assertThat(http.getHttpsPort("")).isEqualTo(47984)
        assertThrows(NumberFormatException::class.java) {
            http.getHttpsPort(serverInfoWith("HttpsPort", "https"))
        }
    }

    @Test
    fun `the external port is the one of the client when the host does not give one`() {
        assertThat(http.getExternalPort(serverInfoWith("ExternalPort", "50000"))).isEqualTo(50000)
        assertThat(http.getExternalPort(serverInfoWithout("ExternalPort"))).isEqualTo(PORT)
        assertThat(http.getExternalPort("""<root status_code="401"/>""")).isEqualTo(PORT)
        assertThat(http.getExternalPort("")).isEqualTo(PORT)
        assertThrows(NumberFormatException::class.java) {
            http.getExternalPort(serverInfoWith("ExternalPort", "none"))
        }
    }

    @Test
    fun `the remote address of a host is its ExternalIP at its external port`() {
        val withBoth =
            details(
                serverInfoWith("ExternalPort", "50000")
                    .replace("<currentgame>", "<ExternalIP>203.0.113.5</ExternalIP><currentgame>")
            )
        val withoutPort =
            details(
                serverInfoWithout("ExternalPort")
                    .replace("<currentgame>", "<ExternalIP>203.0.113.5</ExternalIP><currentgame>")
            )

        assertThat(withBoth.externalPort).isEqualTo(50000)
        assertThat(withBoth.remoteAddress).isEqualTo(AddressTuple("203.0.113.5", 50000))
        assertThat(withoutPort.externalPort).isEqualTo(PORT)
        assertThat(withoutPort.remoteAddress).isEqualTo(AddressTuple("203.0.113.5", PORT))
        // An address tuple does not take port 0.
        assertThrows(IllegalArgumentException::class.java) {
            details(
                serverInfoWith("ExternalPort", "0")
                    .replace("<currentgame>", "<ExternalIP>203.0.113.5</ExternalIP><currentgame>")
            )
        }
    }

    @Test
    fun `a host without a name, an address or a mac still gives its details`() {
        val bare =
            """<root status_code="200"><appversion>7.1.431.-1</appversion>""" +
                "<uniqueid>u-1</uniqueid><PairStatus>0</PairStatus><state>SUNSHINE_SERVER_FREE</state></root>"

        val details = details(bare)

        assertThat(details.name).isEqualTo("UNKNOWN")
        assertThat(details.macAddress).isNull()
        assertThat(details.localAddress).isNull()
        assertThat(details.remoteAddress).isNull()
        assertThat(details.httpsPort).isEqualTo(47984)
        assertThat(details.externalPort).isEqualTo(PORT)
    }

    @Test
    fun `an empty host name is UNKNOWN too`() {
        assertThat(details(serverInfoWith("hostname", "")).name).isEqualTo("UNKNOWN")
        assertThat(details(serverInfoWith("hostname", "Den PC")).name).isEqualTo("Den PC")
        assertThat(details(serverInfoWith("hostname", "R&amp;D &lt;1&gt;")).name)
            .isEqualTo("R&D <1>")
        assertThat(details(serverInfoWith("hostname", "<![CDATA[a<b]]>")).name).isEqualTo("a<b")
    }

    @Test
    fun `the mandatory fields are checked in the order uniqueid, PairStatus, state`() {
        val none = """<root status_code="200"><appversion>7.1.431.-1</appversion></root>"""

        assertThat(failure { details(none) })
            .isEqualTo("Missing mandatory field in host response: uniqueid")
        val withId = none.replace("</root>", "<uniqueid>u</uniqueid></root>")
        assertThat(failure { details(withId) })
            .isEqualTo("Missing mandatory field in host response: PairStatus")
        val withPair = withId.replace("</root>", "<PairStatus>0</PairStatus></root>")
        assertThat(failure { details(withPair) })
            .isEqualTo("Missing mandatory field in host response: state")
    }

    @Test
    fun `the app version is mandatory and has four numbers`() {
        assertThat(failure { http.getServerVersion(serverInfoWithout("appversion")) })
            .isEqualTo("Missing mandatory field in host response: appversion")
        // An empty tag has no text, so it is missing too.
        assertThat(failure { http.getServerAppVersionQuad(serverInfoWith("appversion", "")) })
            .isEqualTo("Missing mandatory field in host response: appversion")
        assertThat(
                http.getServerAppVersionQuad(serverInfoWith("appversion", "6.0.0.1")).asList()
            )
            .containsExactly(6, 0, 0, 1)
            .inOrder()
        for (text in listOf("7.1.431", "7.1.431.", "7.1.431.0.1", "7")) {
            assertWithMessage(text)
                .that(
                    assertThrows(IllegalArgumentException::class.java) {
                            http.getServerAppVersionQuad(serverInfoWith("appversion", text))
                        }
                        .message
                )
                .isEqualTo("Malformed server version field: $text")
        }
        assertThrows(NumberFormatException::class.java) {
            http.getServerAppVersionQuad(serverInfoWith("appversion", "7.1.x.0"))
        }
        assertThat(http.getServerMajorVersion(serverInfoWith("appversion", "6.1.431.0")))
            .isEqualTo(6)
    }

    @Test
    fun `4K needs a GfeVersion that is not of generation 2`() {
        assertThat(http.supports4K(serverInfoWith("GfeVersion", "3.20.3.63"))).isTrue()
        assertThat(http.supports4K(serverInfoWith("GfeVersion", "12.0.0.1"))).isTrue()
        assertThat(http.supports4K(serverInfoWith("GfeVersion", "2.8.1.0"))).isFalse()
        assertThat(http.supports4K(serverInfoWithout("GfeVersion"))).isFalse()
        assertThat(http.getGfeVersion(serverInfoWithout("GfeVersion"))).isNull()
    }

    @Test
    fun `the GPU type is optional`() {
        assertThat(http.getGpuType(serverInfoWith("gputype", "NVIDIA GeForce RTX 3080")))
            .isEqualTo("NVIDIA GeForce RTX 3080")
        assertThat(http.getGpuType(serverInfoWithout("gputype"))).isNull()
    }

    @Test
    fun `the first text of a tag wins and text of other tags is skipped`() {
        val serverInfo =
            """<root status_code="200"><a><hostname>inner</hostname></a><hostname>first</hostname>""" +
                "<hostname>second</hostname></root>"

        assertThat(http.getGpuType(serverInfo.replace("hostname", "gputype"))).isEqualTo("inner")
    }

    @Test
    fun `answers that are not well-formed XML raise a parser exception`() {
        for (text in
            listOf(
                "not xml",
                "<a></b>",
                """text<root status_code="200"/>""",
                """<root status_code="200"/>trailing""",
                """<root status_code="200"><x>&bogus;</x></root>""",
            )) {
            assertWithMessage(text)
                .that(assertThrows(XmlPullParserException::class.java) { http.getGpuType(text) })
                .isNotNull()
        }
    }

    @Test
    fun `an answer that is cut off, or empty, has the fields it did not get to missing`() {
        // The parser does not complain about the end of the input.
        for (text in listOf("", """<root status_code="200"><hostname>x""", "<?xml version=\"1.0\"?>")) {
            assertWithMessage(text).that(http.getGpuType(text)).isNull()
            assertWithMessage(text)
                .that(failure { http.getServerVersion(text) })
                .isEqualTo("Missing mandatory field in host response: appversion")
        }
    }

    private fun serverInfoWith(tag: String, text: String): String {
        val base = LuminalFixtures.serverInfo(LuminalFixtures.VERSIONS.first())
        val pattern = Regex("<$tag>[^<]*</$tag>")
        val element = "<$tag>$text</$tag>"
        return if (pattern.containsMatchIn(base)) {
            base.replace(pattern, Regex.escapeReplacement(element))
        } else {
            base.replace("<currentgame>", "$element<currentgame>")
        }
    }

    private fun serverInfoWithout(tag: String): String =
        LuminalFixtures.serverInfo(LuminalFixtures.VERSIONS.first())
            .replace(Regex("<$tag>[^<]*</$tag>"), "")

    private companion object {
        const val PORT = 48000
    }
}
