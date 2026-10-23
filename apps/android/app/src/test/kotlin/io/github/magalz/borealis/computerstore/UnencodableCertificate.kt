package io.github.magalz.borealis.computerstore

import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.Date

/**
 * A certificate that cannot give its encoding, which is the one thing the saved host list asks of
 * it. Everything else fails loudly, so that a test notices a call that it did not expect.
 */
@Suppress("TooManyFunctions")
internal class UnencodableCertificate : X509Certificate() {
    private fun unused(): Nothing = error("not used by the saved host list")

    override fun getEncoded(): ByteArray = throw CertificateEncodingException("cannot encode")

    override fun verify(key: PublicKey?) = unused()

    override fun verify(key: PublicKey?, sigProvider: String?) = unused()

    override fun toString(): String = "UnencodableCertificate"

    override fun getPublicKey(): PublicKey = unused()

    override fun checkValidity() = unused()

    override fun checkValidity(date: Date?) = unused()

    override fun getVersion(): Int = unused()

    override fun getSerialNumber(): BigInteger = unused()

    @Suppress("OVERRIDE_DEPRECATION") override fun getIssuerDN(): Principal = unused()

    @Suppress("OVERRIDE_DEPRECATION") override fun getSubjectDN(): Principal = unused()

    override fun getNotBefore(): Date = unused()

    override fun getNotAfter(): Date = unused()

    override fun getTBSCertificate(): ByteArray = unused()

    override fun getSignature(): ByteArray = unused()

    override fun getSigAlgName(): String = unused()

    override fun getSigAlgOID(): String = unused()

    override fun getSigAlgParams(): ByteArray = unused()

    override fun getIssuerUniqueID(): BooleanArray = unused()

    override fun getSubjectUniqueID(): BooleanArray = unused()

    override fun getKeyUsage(): BooleanArray = unused()

    override fun getBasicConstraints(): Int = unused()

    override fun hasUnsupportedCriticalExtension(): Boolean = unused()

    override fun getCriticalExtensionOIDs(): MutableSet<String> = unused()

    override fun getNonCriticalExtensionOIDs(): MutableSet<String> = unused()

    override fun getExtensionValue(oid: String?): ByteArray = unused()
}
