package io.github.magalz.borealis.nvhttp

import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.providerOf
import java.io.File
import java.security.cert.X509Certificate

/** The id that every request carries, whatever id the caller passes to the constructor. */
internal const val UNIQUE_ID = "0123456789ABCDEF"

/** The port of the plain HTTP server of a host. */
internal const val HTTP_PORT = 47989

/** The captures of real hosts in `core/tests/fixtures/luminalshine`. */
internal object LuminalFixtures {
    val VERSIONS = listOf("26.09.1", "26.10.0-beta.1")

    private val root: File by lazy {
        File(
            checkNotNull(System.getProperty("borealis.fixtures.dir")) {
                "System property borealis.fixtures.dir is not set (borealis.android.application plugin)."
            },
            "luminalshine",
        )
    }

    /** The answers of a host to a path that it does not route: `/unpair` and an unknown path. */
    val NOT_FOUND_RESPONSES =
        listOf(
            "26.09.1" to "unpair-http.response",
            "26.10.0-beta.1" to "unpair-http.response",
            "26.10.0-beta.1" to "unknown-path-http.response",
        )

    fun bytes(version: String, name: String): ByteArray = File(root, "$version/$name").readBytes()

    fun text(version: String, name: String): String = String(bytes(version, name), Charsets.UTF_8)

    /** The `/serverinfo` answer over plain HTTP. */
    fun serverInfo(version: String): String = text(version, "serverinfo-http.xml")
}

/** A client of a host at 192.0.2.1 that is never contacted: for the code that parses answers. */
internal fun parsingClient(port: Int = HTTP_PORT, httpsPort: Int = 0): NvHTTP =
    NvHTTP(
        AddressTuple("192.0.2.1", port),
        httpsPort,
        "ignored",
        null,
        providerOf(null, null, null),
    )

/** A client of [host]; its identity for TLS is the one of [provider]. */
internal fun clientOf(
    host: NvFakeHost,
    serverCert: X509Certificate? = null,
    httpsKnown: Boolean = true,
    provider: LimelightCryptoProvider = providerOf(TestIdentities.clientRsa),
): NvHTTP =
    NvHTTP(
        AddressTuple("127.0.0.1", host.httpPort),
        if (httpsKnown) host.httpsPort else 0,
        "ignored",
        serverCert,
        provider,
    )

/** The `/serverinfo` body of a host with the given `state` and `currentgame`. */
internal fun serverInfoBody(
    state: String = "SUNSHINE_SERVER_FREE",
    currentGame: Int = 0,
    httpsPort: Int = 47984,
    paired: Boolean = false,
): String =
    """<?xml version="1.0" encoding="utf-8"?>""" +
        """<root status_code="200"><hostname>HOST</hostname><appversion>7.1.431.-1</appversion>""" +
        "<uniqueid>00000000-0000-0000-0000-000000000001</uniqueid><HttpsPort>$httpsPort</HttpsPort>" +
        "<PairStatus>${if (paired) 1 else 0}</PairStatus><currentgame>$currentGame</currentgame>" +
        "<state>$state</state></root>"
