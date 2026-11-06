package io.github.magalz.borealis.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.crypto.AndroidCryptoProvider
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val KEY_FIRST_THREADS = 6
private const val KEY_FIRST_WAIT_SECONDS = 120L

/**
 * The paths of the `AndroidCryptoProvider` that [AndroidCryptoProviderTest] reaches through the
 * certificate only: the identity asked for through the private key first, and the files that cannot
 * be written. The provider shares one load-or-generate rule between the two, behind one lock.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidCryptoProviderKeyFirstCharacterizationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val certFile = File(context.filesDir, "client.crt")
    private val keyFile = File(context.filesDir, "client.key")

    private fun parse(pem: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.inputStream())
            as X509Certificate

    private fun privateKeyOf(pkcs8: ByteArray): RSAPrivateCrtKey =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            as RSAPrivateCrtKey

    private fun writeIdentity(identity: TestIdentity) {
        certFile.writeBytes(identity.pem)
        keyFile.writeBytes(identity.keyPair.private.encoded)
    }

    private fun assertSamePair(certificate: X509Certificate, key: PrivateKey) {
        val signed =
            Signature.getInstance("SHA256withRSA")
                .apply {
                    initSign(key)
                    update(byteArrayOf(1, 2, 3))
                }
                .sign()
        val verified =
            Signature.getInstance("SHA256withRSA")
                .apply {
                    initVerify(certificate.publicKey)
                    update(byteArrayOf(1, 2, 3))
                }
                .verify(signed)
        assertThat(verified).isTrue()
    }

    @Test
    fun `the first use of the key generates and saves an identity and logs each step`() {
        LimeLogCapture().start().use { log ->
            val provider = AndroidCryptoProvider(context)

            val key = checkNotNull(provider.clientPrivateKey)

            assertThat(log.entries)
                .containsExactly(
                    Level.INFO to "Missing cert or key; need to generate a new one",
                    Level.INFO to "Generated a new key pair",
                    Level.INFO to "Saved generated key pair to disk",
                )
                .inOrder()
            assertThat(key.encoded).isEqualTo(keyFile.readBytes())
            assertThat(certFile.readText(Charsets.US_ASCII)).doesNotContain("\r")
            assertThat(provider.pemEncodedClientCertificate).isEqualTo(certFile.readBytes())
            assertSamePair(checkNotNull(provider.clientCertificate), key)
            assertThat(parse(certFile.readBytes()).encoded)
                .isEqualTo(provider.clientCertificate?.encoded)
        }
    }

    @Test
    fun `a key that another writer saved loads unchanged, with its certificate, and logs nothing`() {
        val identity = TestIdentities.clientRsa
        writeIdentity(identity)

        LimeLogCapture().start().use { log ->
            val provider = AndroidCryptoProvider(context)

            val key = checkNotNull(provider.clientPrivateKey)

            assertThat(log.entries).isEmpty()
            assertThat((key as RSAPrivateCrtKey).privateExponent)
                .isEqualTo((identity.keyPair.private as RSAPrivateCrtKey).privateExponent)
            assertThat(provider.clientCertificate?.encoded).isEqualTo(identity.certificate.encoded)
            assertThat(provider.pemEncodedClientCertificate).isEqualTo(identity.pem)
            assertThat(certFile.readBytes()).isEqualTo(identity.pem)
            assertThat(keyFile.readBytes()).isEqualTo(identity.keyPair.private.encoded)
        }
    }

    @Test
    fun `a provider keeps the key it loaded even when the files go away`() {
        val provider = AndroidCryptoProvider(context)
        val key = provider.clientPrivateKey

        certFile.delete()
        keyFile.delete()

        assertThat(provider.clientPrivateKey).isSameInstanceAs(key)
        assertThat(certFile.exists()).isFalse()
        assertThat(keyFile.exists()).isFalse()
    }

    @Test
    fun `a missing key file makes a whole new identity when the key is asked for first`() {
        writeIdentity(TestIdentities.clientRsa)
        keyFile.delete()

        val provider = AndroidCryptoProvider(context)
        val key = checkNotNull(provider.clientPrivateKey)

        assertThat(key.encoded).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
        assertThat(key.encoded).isEqualTo(keyFile.readBytes())
        assertThat(provider.pemEncodedClientCertificate).isEqualTo(certFile.readBytes())
        assertThat(provider.pemEncodedClientCertificate).isNotEqualTo(TestIdentities.clientRsa.pem)
        assertSamePair(checkNotNull(provider.clientCertificate), key)
    }

    @Test
    fun `a corrupt certificate file makes a whole new identity when the key is asked for first`() {
        writeIdentity(TestIdentities.clientRsa)
        certFile.writeText("this is not a certificate")

        val log = LimeLogCapture().start()
        val provider = AndroidCryptoProvider(context)
        val key = checkNotNull(provider.clientPrivateKey)
        log.close()

        assertThat(log.entries.first()).isEqualTo(Level.WARNING to "Corrupted certificate")
        assertThat(key.encoded).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
        assertThat(key.encoded).isEqualTo(keyFile.readBytes())
        assertThat(parse(certFile.readBytes()).encoded)
            .isEqualTo(provider.clientCertificate?.encoded)
        assertSamePair(checkNotNull(provider.clientCertificate), key)
    }

    @Test
    fun `a corrupt key file makes a whole new identity when the key is asked for first`() {
        writeIdentity(TestIdentities.clientRsa)
        keyFile.writeText("this is not a key")

        val log = LimeLogCapture().start()
        val provider = AndroidCryptoProvider(context)
        val key = checkNotNull(provider.clientPrivateKey)
        log.close()

        assertThat(log.entries.first()).isEqualTo(Level.WARNING to "Corrupted key")
        assertThat(key.encoded).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
        assertThat(provider.clientCertificate?.encoded)
            .isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(provider.pemEncodedClientCertificate).isEqualTo(certFile.readBytes())
        assertThat(privateKeyOf(keyFile.readBytes()).modulus)
            .isEqualTo((provider.clientCertificate?.publicKey as RSAPublicKey).modulus)
        assertSamePair(checkNotNull(provider.clientCertificate), key)
    }

    @Test
    fun `a certificate path that cannot be written still gives a key in memory`() {
        certFile.mkdirs()
        val provider = AndroidCryptoProvider(context)

        val key = provider.clientPrivateKey

        assertThat(key).isNotNull()
        assertThat(keyFile.exists()).isFalse()
        assertThat(provider.pemEncodedClientCertificate).isNull()
        assertThat(provider.clientPrivateKey).isSameInstanceAs(key)
    }

    @Test
    fun `a key path that cannot be written leaves an empty certificate file, it is opened first`() {
        keyFile.mkdirs()
        val log = LimeLogCapture().start()
        val provider = AndroidCryptoProvider(context)

        val key = provider.clientPrivateKey
        val certificate = provider.clientCertificate
        log.close()

        // Weakness kept as it is: the certificate file is opened, and so emptied, before the key
        // file fails to open, and the pair is not saved, so the next start makes another identity.
        assertThat(key).isNotNull()
        assertThat(certificate).isNotNull()
        assertThat(certFile.isFile).isTrue()
        assertThat(certFile.length()).isEqualTo(0L)
        assertThat(keyFile.isDirectory).isTrue()
        assertThat(provider.pemEncodedClientCertificate).isNull()
        assertThat(log.messages).doesNotContain("Saved generated key pair to disk")
        assertThat(log.entries)
            .containsExactly(
                Level.INFO to "Missing cert or key; need to generate a new one",
                Level.INFO to "Generated a new key pair",
                Level.INFO to "Missing cert or key; need to generate a new one",
            )
            .inOrder()
        assertThat(provider.clientPrivateKey).isSameInstanceAs(key)
        assertThat(provider.clientCertificate).isSameInstanceAs(certificate)
    }

    @Test
    fun `providers that ask for the key together share one identity`() {
        val pool = Executors.newFixedThreadPool(KEY_FIRST_THREADS)
        try {
            val ready = CountDownLatch(KEY_FIRST_THREADS)
            val go = CountDownLatch(1)
            val results =
                (1..KEY_FIRST_THREADS).map {
                    pool.submit<ByteArray?> {
                        val provider = AndroidCryptoProvider(context)
                        ready.countDown()
                        go.await()
                        provider.clientPrivateKey?.encoded
                    }
                }
            assertThat(ready.await(KEY_FIRST_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            go.countDown()

            val keys = results.map {
                checkNotNull(it.get(KEY_FIRST_WAIT_SECONDS, TimeUnit.SECONDS))
            }

            // Arrays compare by identity in a set: compare their lists.
            assertThat(keys.map { it.toList() }.toSet()).hasSize(1)
            assertThat(keys.first()).isEqualTo(keyFile.readBytes())
        } finally {
            pool.shutdownNow()
        }
    }
}
