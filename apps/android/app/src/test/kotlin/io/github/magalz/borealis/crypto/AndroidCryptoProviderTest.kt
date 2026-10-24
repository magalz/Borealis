package io.github.magalz.borealis.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.crypto.AndroidCryptoProvider
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val RSA_BITS = 2048
private const val PEM_LINE = 64
private const val SHA256_WITH_RSA_OID = "1.2.840.113549.1.1.11"
private const val VALIDITY_YEARS = 20
private const val SERIAL_BITS = 64
private const val SERIAL_DRAWS = 6
private const val THREADS = 6
private const val WAIT_SECONDS = 120L

/**
 * The client identity: `client.crt` and `client.key` in the files directory of the app, which a
 * user's pairings depend on. Every key of these tests is generated while they run.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidCryptoProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val certFile = File(context.filesDir, "client.crt")
    private val keyFile = File(context.filesDir, "client.key")

    private fun parse(pem: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.inputStream())
            as X509Certificate

    private fun privateKeyOf(pkcs8: ByteArray): RSAPrivateCrtKey =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey

    /** The files of an identity that something else than the provider has written. */
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
    fun `the first use generates a PEM certificate with LF line endings in client dot crt`() {
        val certificate = AndroidCryptoProvider(context).clientCertificate

        assertThat(certificate).isNotNull()
        val text = certFile.readText(Charsets.US_ASCII)
        assertThat(text).startsWith("-----BEGIN CERTIFICATE-----\n")
        assertThat(text).endsWith("\n-----END CERTIFICATE-----\n")
        assertThat(text).doesNotContain("\r")
        val lines = text.lines().filter { it.isNotEmpty() }
        assertThat(lines.drop(1).dropLast(1).all { it.length <= PEM_LINE }).isTrue()
        assertThat(parse(certFile.readBytes()).encoded).isEqualTo(certificate?.encoded)
    }

    @Test
    fun `the first use logs that it generates and saves an identity`() {
        LimeLogCapture().use { log ->
            AndroidCryptoProvider(context).clientCertificate

            assertThat(log.records)
                .containsExactly(
                    Level.INFO to "Missing cert or key; need to generate a new one",
                    Level.INFO to "Generated a new key pair",
                    Level.INFO to "Saved generated key pair to disk",
                )
                .inOrder()
        }
    }

    @Test
    fun `a provider that loads an identity logs nothing`() {
        AndroidCryptoProvider(context).clientCertificate

        LimeLogCapture().use { log ->
            val provider = AndroidCryptoProvider(context)
            provider.clientCertificate
            provider.clientPrivateKey
            provider.pemEncodedClientCertificate

            assertThat(log.records).isEmpty()
        }
    }

    @Test
    fun `the first use writes the key in client dot key as PKCS8 binary, not as PEM`() {
        val provider = AndroidCryptoProvider(context)

        val key = provider.clientPrivateKey

        assertThat(key).isNotNull()
        val bytes = keyFile.readBytes()
        assertThat(bytes.toString(Charsets.ISO_8859_1)).doesNotContain("-----")
        assertThat(key?.format).isEqualTo("PKCS#8")
        assertThat(key?.algorithm).isEqualTo("RSA")
        assertThat(privateKeyOf(bytes).privateExponent)
            .isEqualTo((key as RSAPrivateCrtKey).privateExponent)
        assertThat(certFile.exists()).isTrue()
        assertThat(context.filesDir.list()?.sorted()).containsAtLeast("client.crt", "client.key")
    }

    @Test
    fun `the generated certificate is a version 3 RSA 2048 certificate signed with SHA-256`() {
        val before = System.currentTimeMillis()
        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)
        val after = System.currentTimeMillis()

        assertThat(certificate.version).isEqualTo(3)
        assertThat(certificate.sigAlgOID).isEqualTo(SHA256_WITH_RSA_OID)
        assertThat(certificate.publicKey.algorithm).isEqualTo("RSA")
        assertThat((certificate.publicKey as RSAPublicKey).modulus.bitLength()).isEqualTo(RSA_BITS)
        // A certificate with no extensions at all.
        assertThat(certificate.criticalExtensionOIDs.orEmpty()).isEmpty()
        assertThat(certificate.nonCriticalExtensionOIDs.orEmpty()).isEmpty()
        assertThat(certificate.basicConstraints).isEqualTo(-1)
        // Both the key and the certificate come out of Bouncy Castle.
        assertThat(certificate.javaClass.name).startsWith("org.bouncycastle.")
        assertThat(AndroidCryptoProvider(context).clientPrivateKey?.javaClass?.name)
            .startsWith("org.bouncycastle.")
        // The validity starts now, to the second.
        assertThat(certificate.notBefore.time).isAtLeast(before - 1_000)
        assertThat(certificate.notBefore.time).isAtMost(after)
    }

    @Test
    fun `the certificate names its own subject as the issuer, with only the common name`() {
        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        assertThat(certificate.subjectX500Principal.name).isEqualTo("CN=NVIDIA GameStream Client")
        assertThat(certificate.issuerX500Principal).isEqualTo(certificate.subjectX500Principal)
    }

    @Test
    fun `the certificate is valid for twenty years from its start`() {
        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        val expected =
            Calendar.getInstance()
                .apply {
                    time = certificate.notBefore
                    add(Calendar.YEAR, VALIDITY_YEARS)
                }
                .time
        assertThat(certificate.notAfter).isEqualTo(expected)
    }

    @Test
    fun `the serial number is positive and comes from eight random bytes`() {
        val serials =
            (1..SERIAL_DRAWS).map {
                context.filesDir.listFiles()?.forEach { file -> file.delete() }
                checkNotNull(AndroidCryptoProvider(context).clientCertificate).serialNumber
            }

        // Eight random bytes read as a signed number are negative half of the time unless the
        // rule takes the absolute value: that many draws make a miss as unlikely as 1 in 64.
        for (serial in serials) {
            assertThat(serial.signum()).isEqualTo(1)
            assertThat(serial.bitLength()).isAtMost(SERIAL_BITS)
        }
        assertThat(serials.toSet()).hasSize(SERIAL_DRAWS)
        assertThat(serials).doesNotContain(BigInteger.ZERO)
    }

    @Test
    fun `the PEM bytes are the bytes of the certificate file`() {
        val provider = AndroidCryptoProvider(context)

        val pem = provider.pemEncodedClientCertificate

        assertThat(pem).isNotEmpty()
        assertThat(pem).isEqualTo(certFile.readBytes())
    }

    @Test
    fun `a second provider loads the same certificate and key, byte for byte`() {
        val first = AndroidCryptoProvider(context)
        val certificate = checkNotNull(first.clientCertificate)
        val key = checkNotNull(first.clientPrivateKey)
        val certBytes = certFile.readBytes()
        val keyBytes = keyFile.readBytes()

        val second = AndroidCryptoProvider(context)

        assertThat(second.clientCertificate?.encoded).isEqualTo(certificate.encoded)
        assertThat(second.clientPrivateKey?.encoded).isEqualTo(key.encoded)
        assertThat(second.pemEncodedClientCertificate).isEqualTo(certBytes)
        assertThat(certFile.readBytes()).isEqualTo(certBytes)
        assertThat(keyFile.readBytes()).isEqualTo(keyBytes)
        assertSamePair(checkNotNull(second.clientCertificate), checkNotNull(second.clientPrivateKey))
    }

    @Test
    fun `a provider keeps what it loaded even when the files go away`() {
        val provider = AndroidCryptoProvider(context)
        val certificate = provider.clientCertificate
        val key = provider.clientPrivateKey

        certFile.delete()
        keyFile.delete()

        assertThat(provider.clientCertificate).isSameInstanceAs(certificate)
        assertThat(provider.clientPrivateKey).isSameInstanceAs(key)
        assertThat(certFile.exists()).isFalse()
    }

    @Test
    fun `files written by another writer load unchanged and are not rewritten`() {
        val identity = TestIdentities.clientRsa
        writeIdentity(identity)

        val provider = AndroidCryptoProvider(context)

        assertThat(provider.clientCertificate?.encoded).isEqualTo(identity.certificate.encoded)
        assertThat(provider.pemEncodedClientCertificate).isEqualTo(identity.pem)
        assertThat((provider.clientPrivateKey as RSAPrivateCrtKey).privateExponent)
            .isEqualTo((identity.keyPair.private as RSAPrivateCrtKey).privateExponent)
        assertThat(certFile.readBytes()).isEqualTo(identity.pem)
        assertThat(keyFile.readBytes()).isEqualTo(identity.keyPair.private.encoded)
        assertSamePair(checkNotNull(provider.clientCertificate), checkNotNull(provider.clientPrivateKey))
    }

    @Test
    fun `a missing key file makes a whole new identity`() {
        writeIdentity(TestIdentities.clientRsa)
        keyFile.delete()

        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(keyFile.exists()).isTrue()
        assertThat(keyFile.readBytes()).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
        assertThat(parse(certFile.readBytes()).encoded).isEqualTo(certificate.encoded)
        assertSamePair(certificate, privateKeyOf(keyFile.readBytes()))
    }

    @Test
    fun `a missing certificate file makes a whole new identity`() {
        writeIdentity(TestIdentities.clientRsa)
        certFile.delete()

        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(keyFile.readBytes()).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
        assertSamePair(certificate, privateKeyOf(keyFile.readBytes()))
    }

    @Test
    fun `a corrupt certificate file makes a whole new identity`() {
        writeIdentity(TestIdentities.clientRsa)
        certFile.writeText("this is not a certificate")

        val log = LimeLogCapture()
        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)
        log.close()

        assertThat(log.records.first()).isEqualTo(Level.WARNING to "Corrupted certificate")

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(parse(certFile.readBytes()).encoded).isEqualTo(certificate.encoded)
        assertThat(keyFile.readBytes()).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
    }

    @Test
    fun `a corrupt key file makes a whole new identity, certificate included`() {
        writeIdentity(TestIdentities.clientRsa)
        keyFile.writeText("this is not a key")

        val log = LimeLogCapture()
        val provider = AndroidCryptoProvider(context)
        val certificate = checkNotNull(provider.clientCertificate)
        log.close()

        assertThat(log.records.first()).isEqualTo(Level.WARNING to "Corrupted key")

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(provider.pemEncodedClientCertificate).isEqualTo(certFile.readBytes())
        assertThat(provider.pemEncodedClientCertificate).isNotEqualTo(TestIdentities.clientRsa.pem)
        assertSamePair(certificate, checkNotNull(provider.clientPrivateKey))
        assertThat(privateKeyOf(keyFile.readBytes()).modulus)
            .isEqualTo((certificate.publicKey as RSAPublicKey).modulus)
    }

    @Test
    fun `an empty key file makes a whole new identity`() {
        writeIdentity(TestIdentities.clientRsa)
        keyFile.writeBytes(ByteArray(0))

        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(keyFile.length()).isGreaterThan(0)
    }

    @Test
    fun `an empty certificate file makes a whole new identity`() {
        writeIdentity(TestIdentities.clientRsa)
        certFile.writeBytes(ByteArray(0))

        val certificate = checkNotNull(AndroidCryptoProvider(context).clientCertificate)

        assertThat(certificate.encoded).isNotEqualTo(TestIdentities.clientRsa.certificate.encoded)
        assertThat(parse(certFile.readBytes()).encoded).isEqualTo(certificate.encoded)
        assertThat(keyFile.readBytes()).isNotEqualTo(TestIdentities.clientRsa.keyPair.private.encoded)
    }

    @Test
    fun `a certificate path that cannot be written still gives an identity in memory`() {
        certFile.mkdirs()
        val log = LimeLogCapture()
        val provider = AndroidCryptoProvider(context)

        val certificate = provider.clientCertificate
        log.close()

        // Weakness kept as it is: nothing is saved, so the next start makes another identity, and
        // the PEM bytes are missing.
        assertThat(certificate).isNotNull()
        assertThat(provider.clientPrivateKey).isNotNull()
        assertThat(provider.pemEncodedClientCertificate).isNull()
        assertThat(log.messages).doesNotContain("Saved generated key pair to disk")
        assertThat(keyFile.exists()).isFalse()
        assertThat(provider.clientCertificate).isSameInstanceAs(certificate)
    }

    @Test
    fun `providers that start together share one identity`() {
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val ready = CountDownLatch(THREADS)
            val go = CountDownLatch(1)
            val results =
                (1..THREADS).map {
                    pool.submit<ByteArray?> {
                        val provider = AndroidCryptoProvider(context)
                        ready.countDown()
                        go.await()
                        provider.clientCertificate?.encoded
                    }
                }
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            go.countDown()

            val certificates = results.map { checkNotNull(it.get(WAIT_SECONDS, TimeUnit.SECONDS)) }

            // Arrays compare by identity in a set: compare their lists.
            assertThat(certificates.map { it.toList() }.toSet()).hasSize(1)
            assertThat(certificates.first()).isEqualTo(parse(certFile.readBytes()).encoded)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `base64 has no line breaks, uses the standard alphabet and keeps the padding`() {
        val provider = AndroidCryptoProvider(context)

        assertThat(provider.encodeBase64String("Man".toByteArray())).isEqualTo("TWFu")
        assertThat(provider.encodeBase64String(byteArrayOf(0xFB.toByte(), 0xFF.toByte())))
            .isEqualTo("+/8=")
        assertThat(provider.encodeBase64String(ByteArray(100) { it.toByte() })).doesNotContain("\n")
        assertThat(provider.encodeBase64String(ByteArray(0))).isEmpty()
    }
}
