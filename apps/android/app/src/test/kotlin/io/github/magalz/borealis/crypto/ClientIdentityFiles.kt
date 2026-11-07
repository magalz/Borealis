package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec

/** The certificate that a PEM file holds. */
internal fun parseCertificate(pem: ByteArray): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(pem.inputStream())
        as X509Certificate

/** The RSA key that a PKCS8 file holds. */
internal fun privateKeyOf(pkcs8: ByteArray): RSAPrivateCrtKey =
    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey

/** Writes the files of an identity that something else than the provider has written. */
internal fun writeIdentity(certFile: File, keyFile: File, identity: TestIdentity) {
    certFile.writeBytes(identity.pem)
    keyFile.writeBytes(identity.keyPair.private.encoded)
}

/** Checks that [key] signs what the public key of [certificate] verifies. */
internal fun assertSamePair(certificate: X509Certificate, key: PrivateKey) {
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
