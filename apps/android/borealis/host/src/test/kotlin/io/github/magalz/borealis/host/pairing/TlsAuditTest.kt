package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import javax.net.ssl.SSLHandshakeException
import org.junit.Rule
import org.junit.Test

class TlsAuditTest {
    @get:Rule val audit = RecordingAudit()

    private class FakeCertificate(private val der: ByteArray?) : Certificate("X.509") {
        override fun getEncoded(): ByteArray = der ?: throw CertificateEncodingException("broken")

        override fun verify(key: PublicKey?) {}

        override fun verify(key: PublicKey?, sigProvider: String?) {}

        override fun toString() = "FAKE CERTIFICATE WITH SECRET CONTENT"

        override fun getPublicKey(): PublicKey? = null
    }

    @Test
    fun `a changed certificate records short fingerprints of both certificates`() {
        // SHA-256 of "abc" and of "abd".
        TlsAudit.certificateChanged(
            "den-pc",
            presented = FakeCertificate("abc".toByteArray()),
            pinned = FakeCertificate("abd".toByteArray()),
        )

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.CERTIFICATE_CHANGED)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo("den-pc")
        assertThat(event.detail)
            .isEqualTo("presented=ba7816bf8f01cfea pinned=${sha256Prefix("abd")}")
    }

    @Test
    fun `neither the certificate nor its full hash is recorded`() {
        TlsAudit.certificateChanged(
            "den-pc",
            FakeCertificate("abc".toByteArray()),
            FakeCertificate("abd".toByteArray()),
        )

        val detail = audit.events.single().detail!!
        assertThat(detail).doesNotContain("ba7816bf8f01cfea414140de5dae2223")
        assertThat(detail).doesNotContain("SECRET")
        assertThat(detail).doesNotContain("abc")
    }

    @Test
    fun `a certificate that cannot be encoded is recorded without a fingerprint`() {
        TlsAudit.certificateChanged("den-pc", FakeCertificate(null), FakeCertificate(null))

        assertThat(audit.events.single().detail)
            .isEqualTo("presented=unavailable pinned=unavailable")
    }

    @Test
    fun `a handshake failure records the exception class and no message`() {
        TlsAudit.failure(
            "den-pc",
            SSLHandshakeException("pin=1234 certificate chain for 192.168.1.5 rejected"),
        )

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.TLS_FAILURE)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("error=SSLHandshakeException")
    }

    private fun sha256Prefix(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
