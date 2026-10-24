package io.github.magalz.borealis.crypto

import com.limelight.nvstream.http.LimelightCryptoProvider
import java.math.BigInteger
import java.security.Key
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

private const val RSA_BITS = 2048
private const val DSA_BITS = 2048
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val PEM_COLUMNS = 64
private const val SERIAL_BYTES = 8

/**
 * A key pair and the self-signed certificate of a test client or host. Every identity is generated
 * when a test first asks for it and lives in memory only: no key of a test is stored anywhere.
 */
internal class TestIdentity(val keyPair: KeyPair, val certificate: X509Certificate) {
    /** The certificate in the PEM form of the hosts: LF line endings and 64 columns. */
    val pem: ByteArray = pemOf(certificate)

}

/** The SHA-256 signature algorithm that goes with a key of the three types the tests use. */
internal fun signatureAlgorithmOf(key: Key): String =
    when (key.algorithm) {
        "RSA" -> "SHA256withRSA"
        "EC" -> "SHA256withECDSA"
        "DSA" -> "SHA256withDSA"
        else -> error("No test signature for ${key.algorithm}")
    }

internal fun pemOf(certificate: X509Certificate): ByteArray {
    val body =
        Base64.getMimeEncoder(PEM_COLUMNS, byteArrayOf('\n'.code.toByte()))
            .encodeToString(certificate.encoded)
    return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n".toByteArray(Charsets.US_ASCII)
}

private fun generate(algorithm: String, init: KeyPairGenerator.() -> Unit): KeyPair =
    KeyPairGenerator.getInstance(algorithm).apply(init).generateKeyPair()

private fun identityOf(keyPair: KeyPair, subject: String): TestIdentity {
    val name = X500Name("CN=$subject")
    val now = System.currentTimeMillis()
    val serial = BigInteger(SERIAL_BYTES * Byte.SIZE_BITS - 1, SecureRandom()).add(BigInteger.ONE)
    val holder =
        JcaX509v3CertificateBuilder(
                name,
                serial,
                Date(now - DAY_MS),
                Date(now + 365 * DAY_MS),
                name,
                keyPair.public,
            )
            .build(JcaContentSignerBuilder(signatureAlgorithmOf(keyPair.private)).build(keyPair.private))
    return TestIdentity(keyPair, JcaX509CertificateConverter().getCertificate(holder))
}

/** The identities that the pairing tests use, each generated on first use. */
internal object TestIdentities {
    val clientRsa: TestIdentity by lazy {
        identityOf(generate("RSA") { initialize(RSA_BITS) }, "Test Client RSA")
    }

    val clientEc: TestIdentity by lazy {
        identityOf(generate("EC") { initialize(ECGenParameterSpec("secp256r1")) }, "Test Client EC")
    }

    val clientDsa: TestIdentity by lazy {
        identityOf(generate("DSA") { initialize(DSA_BITS) }, "Test Client DSA")
    }

    val hostRsa: TestIdentity by lazy {
        identityOf(generate("RSA") { initialize(RSA_BITS) }, "Test Host RSA")
    }

    val otherHostRsa: TestIdentity by lazy {
        identityOf(generate("RSA") { initialize(RSA_BITS) }, "Test Other Host RSA")
    }

    val hostEc: TestIdentity by lazy {
        identityOf(generate("EC") { initialize(ECGenParameterSpec("secp256r1")) }, "Test Host EC")
    }

    val hostDsa: TestIdentity by lazy {
        identityOf(generate("DSA") { initialize(DSA_BITS) }, "Test Host DSA")
    }
}

/** A private key that reports another algorithm name than its own, and nothing else. */
internal class RenamedKey(private val inner: PrivateKey, private val name: String) : PrivateKey {
    override fun getAlgorithm(): String = name

    override fun getFormat(): String? = inner.format

    override fun getEncoded(): ByteArray? = inner.encoded
}

/**
 * A [LimelightCryptoProvider] that answers with the given values. It is a [java.lang.reflect.Proxy]
 * so that the same test source runs against the interface in Java and in Kotlin, where its members
 * are properties.
 */
internal fun providerOf(
    certificate: X509Certificate?,
    key: PrivateKey?,
    pem: ByteArray?,
    calls: MutableList<String>? = null,
): LimelightCryptoProvider =
    java.lang.reflect.Proxy.newProxyInstance(
        LimelightCryptoProvider::class.java.classLoader,
        arrayOf(LimelightCryptoProvider::class.java),
    ) { _, method, _ ->
        calls?.add(method.name)
        when (method.name) {
            "getClientCertificate" -> certificate
            "getClientPrivateKey" -> key
            "getPemEncodedClientCertificate" -> pem
            "encodeBase64String" -> ""
            else -> error("Unexpected call of ${method.name}")
        }
    } as LimelightCryptoProvider

internal fun providerOf(identity: TestIdentity): LimelightCryptoProvider =
    providerOf(identity.certificate, identity.keyPair.private, identity.pem)
