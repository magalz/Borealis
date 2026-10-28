package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.PairingManager.PairState
import java.io.Closeable
import java.security.NoSuchAlgorithmException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val UP_TO_THE_SIGNATURE =
    listOf("http getservercert", "http clientchallenge", "http serverchallengeresp")

/**
 * Which signatures the pairing makes and checks: by the algorithm name of the key, which is
 * case-sensitive and knows RSA and EC; and what it does when the provider gives it nothing.
 */
@RunWith(RobolectricTestRunner::class)
class PairingKeyAlgorithmTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.forEach { it.close() }

    private fun host(identity: TestIdentity = TestIdentities.hostRsa) =
        FakePairingHost(hostIdentity = identity).also { closeables += it }

    @Test
    fun `a host with an EC key is verified with SHA256withECDSA`() {
        val host = host(TestIdentities.hostEc)
        val rig = PairingRig(host)

        assertThat(rig.pair()).isEqualTo(PairState.PAIRED)

        assertThat(rig.pairedCert).isEqualTo(TestIdentities.hostEc.certificate)
    }

    @Test
    fun `a host with a key of another algorithm ends the pairing before it can be verified`() {
        val host = host(TestIdentities.hostDsa)
        val rig = PairingRig(host)

        val error = assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(error).hasCauseThat().isInstanceOf(NoSuchAlgorithmException::class.java)
        assertThat(error).hasCauseThat().hasMessageThat().isEqualTo("Unhandled key algorithm: DSA")
        assertThat(rig.seen).containsExactlyElementsIn(UP_TO_THE_SIGNATURE).inOrder()
    }

    @Test
    fun `a client with an EC key signs with SHA256withECDSA`() {
        val host = host()
        val rig = PairingRig(host, providerOf(TestIdentities.clientEc))

        assertThat(rig.pair()).isEqualTo(PairState.PAIRED)

        assertThat(host.clientSignatureValid).isTrue()
        assertThat(host.clientCertificate).isEqualTo(TestIdentities.clientEc.certificate)
    }

    @Test
    fun `a client with a key of another algorithm ends the pairing when it has to sign`() {
        val host = host()
        val rig = PairingRig(host, providerOf(TestIdentities.clientDsa))

        val error = assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(error).hasCauseThat().isInstanceOf(NoSuchAlgorithmException::class.java)
        assertThat(error).hasCauseThat().hasMessageThat().isEqualTo("Unhandled key algorithm: DSA")
        // The PIN was right, so the host was not unpaired and never got the client secret.
        assertThat(rig.seen).containsExactlyElementsIn(UP_TO_THE_SIGNATURE).inOrder()
        assertThat(host.clientSignatureValid).isNull()
    }

    @Test
    fun `the algorithm name of a key is matched in uppercase only`() {
        val client = TestIdentities.clientRsa
        val host = host()
        val rig =
            PairingRig(
                host,
                providerOf(
                    client.certificate,
                    RenamedKey(client.keyPair.private, "rsa"),
                    client.pem,
                ),
            )

        val error = assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(error).hasCauseThat().hasMessageThat().isEqualTo("Unhandled key algorithm: rsa")
        assertThat(rig.seen).containsExactlyElementsIn(UP_TO_THE_SIGNATURE).inOrder()
    }

    @Test
    fun `a provider with no PEM certificate ends the pairing before any request`() {
        val client = TestIdentities.clientRsa
        val host = host()
        val rig = PairingRig(host, providerOf(client.certificate, client.keyPair.private, null))

        assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(rig.seen).isEmpty()
    }

    @Test
    fun `a provider with no certificate ends the pairing after the client challenge`() {
        val client = TestIdentities.clientRsa
        val host = host()
        val rig = PairingRig(host, providerOf(null, client.keyPair.private, client.pem))

        assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(rig.seen).containsExactly("http getservercert", "http clientchallenge").inOrder()
    }

    @Test
    fun `a provider with no private key ends the pairing when it has to sign`() {
        val client = TestIdentities.clientRsa
        val host = host()
        val rig = PairingRig(host, providerOf(client.certificate, null, client.pem))

        assertThrows(RuntimeException::class.java) { rig.pair() }

        assertThat(rig.seen).containsExactlyElementsIn(UP_TO_THE_SIGNATURE).inOrder()
        assertThat(host.clientSignatureValid).isNull()
    }
}
