package io.github.magalz.borealis.crypto

import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import java.security.cert.X509Certificate

/** `serverinfo` of a host of generation 7, which pairs with SHA-256. */
internal const val SERVER_INFO_GEN7 =
    """<root status_code="200"><appversion>7.1.431.0</appversion></root>"""

/** `serverinfo` of a host before generation 7, which pairs with SHA-1. */
internal const val SERVER_INFO_GEN6 =
    """<root status_code="200"><appversion>6.1.431.0</appversion></root>"""

/** A client, an [NvHTTP] of it and the [com.limelight.nvstream.http.PairingManager] it builds. */
internal class PairingRig(
    val host: FakePairingHost,
    val provider: LimelightCryptoProvider = providerOf(TestIdentities.clientRsa),
) {
    val http =
        NvHTTP(AddressTuple("127.0.0.1", host.httpPort), host.httpsPort, "id", null, provider)

    val pairedCert: X509Certificate?
        get() = http.pairingManager.pairedCert

    fun pair(serverInfo: String = SERVER_INFO_GEN7, pin: String = host.pin): PairState =
        http.pairingManager.pair(serverInfo, pin)

    /** What the host saw, as `scheme phase`. */
    val seen: List<String>
        get() = host.requests.map { it.toString() }
}

internal val FULL_EXCHANGE =
    listOf(
        "http getservercert",
        "http clientchallenge",
        "http serverchallengeresp",
        "http clientpairingsecret",
        "https pairchallenge",
    )
