package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.Closeable
import java.io.IOException
import java.security.Signature
import java.util.logging.Level
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SALT_HEX_LENGTH = 32
private const val CHALLENGE_HEX_LENGTH = 32
private const val TWO_BLOCKS_HEX_LENGTH = 64
private const val SECRET_AND_RSA_SIGNATURE_HEX_LENGTH = (16 + 256) * 2

/**
 * The pairing handshake of the client against a host that runs the protocol itself: what the client
 * sends in each phase, in which order, and how each answer of the host ends the pairing.
 */
@RunWith(RobolectricTestRunner::class)
class PairingFlowTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.forEach { it.close() }

    private fun host(
        sha256: Boolean = true,
        hostPin: String = "1234",
        tlsIdentity: TestIdentity = TestIdentities.hostRsa,
    ) =
        FakePairingHost(sha256 = sha256, tlsIdentity = tlsIdentity, pin = hostPin).also {
            closeables += it
        }

    private fun refusing(host: FakePairingHost, phase: String) {
        host.intercept = { request, default ->
            if (request.phase == phase) {
                default()
                PairingReply.root("<paired>0</paired>")
            } else {
                default()
            }
        }
    }

    @Test
    fun `a host of generation 7 pairs through the five phases in order`() {
        val host = host()
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.PAIRED)

        assertThat(rig.seen).containsExactlyElementsIn(FULL_EXCHANGE).inOrder()
        assertThat(rig.pairedCert).isEqualTo(TestIdentities.hostRsa.certificate)
        assertThat(host.clientSignatureValid).isTrue()
        assertThat(host.clientHashMatched).isTrue()
        assertThat(host.hostFailures).isEmpty()
    }

    @Test
    fun `a host before generation 7 pairs with SHA-1`() {
        val host = host(sha256 = false)
        val rig = PairingRig(host)

        assertThat(rig.pair(SERVER_INFO_GEN6)).isEqualTo(PairState.PAIRED)

        assertThat(rig.seen).containsExactlyElementsIn(FULL_EXCHANGE).inOrder()
        assertThat(host.clientHashMatched).isTrue()
    }

    @Test
    fun `the hash follows the major version of the host from generation 7 on`() {
        val versions =
            listOf("5.0.0.0" to false, "6.99.99.99" to false, "7.0.0.0" to true, "8.1.0.0" to true)

        for ((version, sha256) in versions) {
            val host = host(sha256 = sha256)
            val serverInfo = """<root status_code="200"><appversion>$version</appversion></root>"""

            val state = PairingRig(host).pair(serverInfo)

            assertWithMessage("the pairing with $version").that(state).isEqualTo(PairState.PAIRED)
        }
    }

    @Test
    fun `every request carries the device name, the update flag and the unique id`() {
        val host = host()
        PairingRig(host).pair()

        for (request in host.requests) {
            assertThat(request.query["devicename"]).isEqualTo("roth")
            assertThat(request.query["updateState"]).isEqualTo("1")
            assertThat(request.query["uniqueid"]).isEqualTo("0123456789ABCDEF")
            assertThat(request.query["uuid"]).isNotEmpty()
        }
        assertThat(host.requests.first().query.keys)
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
        assertThat(host.requests.first().query["phrase"]).isEqualTo("getservercert")
    }

    @Test
    fun `the first phase sends a 16 byte salt and the client certificate as uppercase hex`() {
        val host = host()
        PairingRig(host).pair()

        val first = host.requests.first()
        assertThat(first.query.getValue("salt")).matches("[0-9A-F]{$SALT_HEX_LENGTH}")
        assertThat(first.query["clientcert"]).isEqualTo(upperHex(TestIdentities.clientRsa.pem))
        assertThat(host.clientCertificate).isEqualTo(TestIdentities.clientRsa.certificate)
    }

    @Test
    fun `every pairing uses a new salt`() {
        val salts =
            (1..3).map {
                val host = host()
                PairingRig(host).pair()
                host.requests.first().query.getValue("salt")
            }

        assertThat(salts.toSet()).hasSize(salts.size)
    }

    @Test
    fun `the challenge is one AES block and the answers are padded to whole blocks`() {
        val sha256 = host()
        PairingRig(sha256).pair()
        val sha1 = host(sha256 = false)
        PairingRig(sha1).pair(SERVER_INFO_GEN6)

        for (host in listOf(sha256, sha1)) {
            val sent = host.requests
            assertThat(sent[1].query.getValue("clientchallenge"))
                .matches("[0-9A-F]{$CHALLENGE_HEX_LENGTH}")
            // 32 bytes of SHA-256, and 20 bytes of SHA-1 padded with zeros to two blocks.
            assertThat(sent[2].query.getValue("serverchallengeresp"))
                .matches("[0-9A-F]{$TWO_BLOCKS_HEX_LENGTH}")
            // The 16 byte secret and the 2048 bit RSA signature of it.
            assertThat(sent[3].query.getValue("clientpairingsecret"))
                .matches("[0-9A-F]{$SECRET_AND_RSA_SIGNATURE_HEX_LENGTH}")
        }
    }

    @Test
    fun `a host that writes lowercase hex pairs too`() {
        val host = host()
        host.hex = ::lowerHex
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.PAIRED)
    }

    @Test
    fun `the pairing logs the generation of the host at info level`() {
        LimeLogCapture().start().use { log ->
            PairingRig(host()).pair()

            assertThat(log.entries.filter { it.second.startsWith("Pairing with") })
                .containsExactly(Level.INFO to "Pairing with server generation: 7")
        }
    }

    @Test
    fun `the PIN is not logged`() {
        // A PIN with letters that no hex digit has: the logs of the requests are full of hex.
        val pin = "pin-4271-xyz"
        LimeLogCapture().start().use { log ->
            val host = FakePairingHost(pin = pin).also { closeables += it }
            PairingRig(host).pair()

            assertThat(log.messages).isNotEmpty()
            assertThat(log.messages.filter { pin in it }).isEmpty()
        }
    }

    @Test
    fun `a version of the host that is not four numbers ends the pairing before any request`() {
        val host = host()
        val rig = PairingRig(host)

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                rig.pair("""<root status_code="200"><appversion>7.1</appversion></root>""")
            }

        assertThat(error).hasMessageThat().isEqualTo("Malformed server version field: 7.1")
        assertThat(rig.seen).isEmpty()
    }

    @Test
    fun `the manager reads the identity of the provider once, when it is built`() {
        val calls = mutableListOf<String>()
        val client = TestIdentities.clientRsa
        val provider = providerOf(client.certificate, client.keyPair.private, client.pem, calls)

        PairingRig(host(), provider)

        // NvHTTP builds the manager, which asks for the certificate, its PEM and the key, in order.
        assertThat(calls)
            .containsExactly(
                "getClientCertificate",
                "getPemEncodedClientCertificate",
                "getClientPrivateKey",
            )
            .inOrder()
    }

    @Test
    fun `the challenge over TLS presents the client certificate of the provider`() {
        val host = host()
        PairingRig(host).pair()

        val challenge = host.requests.last()
        assertThat(challenge.scheme).isEqualTo("https")
        assertThat(challenge.clientCertificates)
            .containsExactly(TestIdentities.clientRsa.certificate)
    }

    @Test
    fun `the host certificate is pinned for the challenge over TLS`() {
        val host = host(tlsIdentity = TestIdentities.otherHostRsa)
        val rig = PairingRig(host)

        assertThrows(IOException::class.java) { rig.pair() }

        // The four phases over HTTP ran; the TLS request never reached the host and was not
        // unpaired.
        assertThat(rig.seen).containsExactlyElementsIn(FULL_EXCHANGE.dropLast(1)).inOrder()
    }

    @Test
    fun `a wrong PIN ends as PIN_WRONG, cancels the pairing and keeps the host certificate`() {
        val host = host(hostPin = "9999")
        val rig = PairingRig(host)

        assertThat(rig.pair(pin = "1234")).isEqualTo(PairState.PIN_WRONG)

        assertThat(rig.seen)
            .containsExactly(
                "http getservercert",
                "http clientchallenge",
                "http serverchallengeresp",
                "http unpair",
            )
            .inOrder()
        assertThat(rig.pairedCert).isEqualTo(TestIdentities.hostRsa.certificate)
        assertThat(host.clientHashMatched).isNull()
    }

    @Test
    fun `a PIN hash that differs only in its last byte is a wrong PIN`() {
        // The right PIN and the right AES key: only the last byte of the hash in the answer is off,
        // so only a comparison of the whole hash sees it.
        for ((sha256, serverInfo) in listOf(true to SERVER_INFO_GEN7, false to SERVER_INFO_GEN6)) {
            val host = host(sha256 = sha256)
            host.flipLastHashByte = true
            val rig = PairingRig(host)

            assertWithMessage("sha256 is $sha256")
                .that(rig.pair(serverInfo))
                .isEqualTo(PairState.PIN_WRONG)

            assertThat(rig.seen)
                .containsExactly(
                    "http getservercert",
                    "http clientchallenge",
                    "http serverchallengeresp",
                    "http unpair",
                )
                .inOrder()
            assertThat(host.clientHashMatched).isNull()
        }
    }

    @Test
    fun `a host that does not accept the first phase fails the pairing without an unpair`() {
        val host = host()
        refusing(host, "getservercert")
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen).containsExactly("http getservercert")
        assertThat(rig.pairedCert).isNull()
    }

    @Test
    fun `a host that refuses the client challenge fails the pairing and unpairs`() {
        val host = host()
        refusing(host, "clientchallenge")
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen)
            .containsExactly("http getservercert", "http clientchallenge", "http unpair")
            .inOrder()
    }

    @Test
    fun `a host that refuses the server challenge response fails the pairing and unpairs`() {
        val host = host()
        refusing(host, "serverchallengeresp")
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen)
            .containsExactly(
                "http getservercert",
                "http clientchallenge",
                "http serverchallengeresp",
                "http unpair",
            )
            .inOrder()
    }

    @Test
    fun `a host that refuses the client pairing secret fails the pairing and unpairs`() {
        val host = host()
        refusing(host, "clientpairingsecret")
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen)
            .containsExactlyElementsIn(FULL_EXCHANGE.dropLast(1) + "http unpair")
            .inOrder()
    }

    @Test
    fun `a host that refuses the challenge over TLS fails the pairing and unpairs`() {
        val host = host()
        refusing(host, "pairchallenge")
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen).containsExactlyElementsIn(FULL_EXCHANGE + "http unpair").inOrder()
    }

    @Test
    fun `a signature that does not match the host certificate fails the pairing and unpairs`() {
        val host = host()
        host.intercept = { request, default ->
            val honest = default()
            if (request.phase == "serverchallengeresp") {
                val forged =
                    Signature.getInstance("SHA256withRSA")
                        .apply {
                            initSign(TestIdentities.otherHostRsa.keyPair.private)
                            update(host.serverSecret)
                        }
                        .sign()
                PairingReply.root(
                    "<paired>1</paired><pairingsecret>" +
                        "${upperHex(host.serverSecret + forged)}</pairingsecret>"
                )
            } else {
                honest
            }
        }
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.FAILED)

        assertThat(rig.seen)
            .containsExactly(
                "http getservercert",
                "http clientchallenge",
                "http serverchallengeresp",
                "http unpair",
            )
            .inOrder()
    }

    @Test
    fun `a host with no certificate to give because another client pairs is already in progress`() {
        for (withoutCertificate in
            listOf("<paired>1</paired>", "<paired>1</paired><plaincert></plaincert>")) {
            val host = host()
            host.intercept = { request, default ->
                default()
                if (request.phase == "getservercert") PairingReply.root(withoutCertificate)
                else PairingReply.root("")
            }
            val rig = PairingRig(host)

            assertWithMessage(withoutCertificate)
                .that(rig.pair())
                .isEqualTo(PairState.ALREADY_IN_PROGRESS)

            assertThat(rig.seen).containsExactly("http getservercert", "http unpair").inOrder()
            assertThat(rig.pairedCert).isNull()
        }
    }

    @Test
    fun `a host that answers 409 with its HTTP status ends the pairing with the host error`() {
        val host = host()
        host.intercept = { _, _ -> PairingReply(409, "Conflict", "") }
        val rig = PairingRig(host)

        val error = assertThrows(HostHttpResponseException::class.java) { rig.pair() }

        assertThat(error.errorCode).isEqualTo(409)
        assertThat(error.errorMessage).isEqualTo("Conflict")
        assertThat(rig.seen).containsExactly("http getservercert")
    }

    @Test
    fun `a host that answers 409 in the status of the XML ends the pairing with the host error`() {
        val host = host()
        host.intercept = { _, _ ->
            PairingReply(
                200,
                "OK",
                """<root status_code="409" status_message="Pairing exchange already in progress"/>""",
            )
        }
        val rig = PairingRig(host)

        val error = assertThrows(HostHttpResponseException::class.java) { rig.pair() }

        assertThat(error.errorCode).isEqualTo(409)
        assertThat(error.errorMessage).isEqualTo("Pairing exchange already in progress")
        assertThat(rig.seen).containsExactly("http getservercert")
    }
}
