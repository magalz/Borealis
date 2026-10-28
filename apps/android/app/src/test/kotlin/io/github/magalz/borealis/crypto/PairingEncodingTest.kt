package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.PairingManager.PairState
import java.io.Closeable
import java.security.cert.CertificateException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

/**
 * The bytes of the pairing: how it writes and reads hex, how it pads, which exceptions it throws
 * for the answers of a host that does not follow the protocol.
 */
@RunWith(RobolectricTestRunner::class)
class PairingEncodingTest {
    private val closeables = mutableListOf<Closeable>()

    @After
    fun tearDown() = closeables.forEach { it.close() }

    private fun host(sha256: Boolean = true) =
        FakePairingHost(sha256 = sha256).also { closeables += it }

    private fun answering(host: FakePairingHost, phase: String, answer: String) {
        host.intercept = { request, default ->
            val honest = default()
            if (request.phase == phase) PairingReply.root(answer) else honest
        }
    }

    @Test
    fun `the client certificate goes out as uppercase hex with every byte kept`() {
        val host = host()
        val bytes =
            byteArrayOf(0x00, 0x0F, 0x10, 0x7F, 0x80.toByte(), 0xAB.toByte(), 0xFF.toByte())
        // The honest answer cannot read these bytes as a certificate: only the request counts here.
        host.intercept = { _, _ -> PairingReply.root("<paired>0</paired>") }
        val provider =
            providerOf(
                TestIdentities.clientRsa.certificate,
                TestIdentities.clientRsa.keyPair.private,
                bytes,
            )

        val state = PairingRig(host, provider).pair()

        assertThat(state).isEqualTo(PairState.FAILED)
        assertThat(host.requests.single().query["clientcert"]).isEqualTo("000F107F80ABFF")
    }

    @Test
    fun `characters that are not hex digits count as minus one in their half byte`() {
        val host = host()
        // The first bytes of the secret that the host signs are F0, FF, EF and A3. The host writes
        // them as a bad character with 0, 0 with a bad character, two bad characters, and a
        // fullwidth A with an Arabic-Indic three, which Character.digit reads as 10 and 3.
        host.serverSecret =
            byteArrayOf(0xF0.toByte(), 0xFF.toByte(), 0xEF.toByte(), 0xA3.toByte()) +
                host.serverSecret.copyOfRange(4, 16)
        host.intercept = { request, default ->
            val honest = default()
            if (request.phase == "serverchallengeresp") {
                PairingReply.root(
                    honest.body
                        .substringAfter("""<root status_code="200">""")
                        .substringBeforeLast("</root>")
                        .replace("<pairingsecret>F0FFEFA3", "<pairingsecret>Z00ZZZＡ٣")
                )
            } else {
                honest
            }
        }
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.PAIRED)
    }

    @Test
    fun `an odd length of hex in an answer is refused with its length`() {
        val host = host()
        answering(host, "clientchallenge", "<paired>1</paired><challengeresponse>ABC</challengeresponse>")
        val rig = PairingRig(host)

        val error = assertThrows(IllegalArgumentException::class.java) { rig.pair() }

        assertThat(error).hasMessageThat().isEqualTo("Illegal string length: 3")
    }

    @Test
    fun `a challenge response that stops after the hash is read with the challenge padded with zeros`() {
        for ((sha256, serverInfo, challengeBytes) in
            listOf(
                Triple(false, SERVER_INFO_GEN6, 12),
                Triple(true, SERVER_INFO_GEN7, 0),
            )) {
            val host = host(sha256)
            host.serverChallengeBytesSent = challengeBytes
            val rig = PairingRig(host)

            val state = rig.pair(serverInfo)

            assertWithMessage("$challengeBytes bytes of challenge").that(state).isEqualTo(PairState.PAIRED)
            assertThat(host.clientHashMatched).isTrue()
        }
    }

    @Test
    fun `a pairing secret shorter than its 16 byte secret is refused`() {
        val host = host()
        answering(
            host,
            "serverchallengeresp",
            "<paired>1</paired><pairingsecret>0102030405060708</pairingsecret>",
        )
        val rig = PairingRig(host)

        assertThrows(IllegalArgumentException::class.java) { rig.pair() }

        assertThat(rig.seen)
            .containsExactly("http getservercert", "http clientchallenge", "http serverchallengeresp")
            .inOrder()
    }

    @Test
    fun `a PIN with a character outside ASCII is salted with as many bytes as it has characters`() {
        val host = host()
        // The two characters of the PIN are three bytes in UTF-8; only the first two are salted.
        host.pinBytes = "é1".toByteArray(Charsets.UTF_8).copyOf(2)
        val rig = PairingRig(host)

        assertThat(rig.pair(pin = "é1")).isEqualTo(PairState.PAIRED)
    }

    @Test
    fun `a certificate in the answer that is not a certificate ends the pairing with its cause`() {
        val host = host()
        answering(host, "getservercert", "<paired>1</paired><plaincert>0102030405</plaincert>")
        val rig = PairingRig(host)

        val error = assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(error).hasCauseThat().isInstanceOf(CertificateException::class.java)
        assertThat(rig.seen).containsExactly("http getservercert")
    }

    @Test
    fun `a missing mandatory field ends the pairing without an unpair`() {
        val host = host()
        answering(host, "clientchallenge", "<paired>1</paired>")
        val rig = PairingRig(host)

        val error = assertThrows(XmlPullParserException::class.java) { rig.pair() }

        assertThat(error)
            .hasMessageThat()
            .contains("Missing mandatory field in host response: challengeresponse")
        assertThat(rig.seen).containsExactly("http getservercert", "http clientchallenge").inOrder()
    }

    @Test
    fun `only the text 1 in the paired field means paired`() {
        for (notOne in listOf("true", "01", "0", "")) {
            val host = host()
            answering(host, "getservercert", "<paired>$notOne</paired>")
            val rig = PairingRig(host)

            if (notOne.isEmpty()) {
                // An empty element has no text, so the mandatory field is missing.
                assertThrows(XmlPullParserException::class.java) { rig.pair() }
            } else {
                assertWithMessage("paired is $notOne").that(rig.pair()).isEqualTo(PairState.FAILED)
            }
        }
    }
}
