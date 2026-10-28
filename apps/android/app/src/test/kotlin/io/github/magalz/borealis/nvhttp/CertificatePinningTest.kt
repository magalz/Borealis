package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.TestIdentity
import io.github.magalz.borealis.crypto.providerOf
import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import io.github.magalz.borealis.library.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import java.io.Closeable
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The TLS side of `NvHTTP`: the certificate of the host is the one pinned when the PC was paired,
 * and nothing else. A certificate that chains up to a trusted root is accepted as well. This is
 * security-sensitive code (the lint baseline holds its `CustomX509TrustManager` finding), so it is
 * pinned over real TLS and member by member.
 */
@RunWith(RobolectricTestRunner::class)
class CertificatePinningTest {
    @get:Rule val audit = RecordingAudit()

    @get:Rule val folder = TemporaryFolder()

    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private val pinned: TestIdentity = TestIdentities.hostRsa
    private val other: TestIdentity = TestIdentities.otherHostRsa

    private fun host(identity: TestIdentity = pinned): NvFakeHost =
        NvFakeHost(identity).also { closeables += it }

    private val RecordingAudit.types: List<AuditType>
        get() = events.map { it.type }

    private fun answerApps(host: NvFakeHost) =
        host.answer(
            "/applist",
            HostReply.root("<App><AppTitle>Desktop</AppTitle><ID>1</ID></App>"),
            "https",
        )

    private fun NvHTTP.field(name: String): Any? =
        NvHTTP::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

    private fun NvHTTP.trustManager() = field("trustManager") as X509TrustManager

    private fun NvHTTP.keyManager() = field("keyManager") as X509KeyManager

    private fun NvHTTP.hostnameVerifier() =
        (field("httpClientLongConnectTimeout") as OkHttpClient).hostnameVerifier

    private fun sessionOf(vararg chain: Certificate): SSLSession =
        Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            check(method.name == "getPeerCertificates") { "Unexpected call of ${method.name}" }
            chain
        } as SSLSession

    private fun unverifiedSession(): SSLSession =
        Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            check(method.name == "getPeerCertificates") { "Unexpected call of ${method.name}" }
            throw SSLPeerUnverifiedException("peer not verified")
        } as SSLSession

    @Test
    fun `the pinned certificate is accepted for any host name`() {
        val host = host()
        answerApps(host)
        // The certificate is for "Test Host RSA" and the request goes to 127.0.0.1.
        val http = clientOf(host, serverCert = pinned.certificate)

        assertThat(http.getAppList()).hasSize(1)

        assertThat(host.requests.single().scheme).isEqualTo("https")
        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `another certificate than the pinned one is refused and audited`() {
        val host = host(identity = other)
        answerApps(host)
        val http = clientOf(host, serverCert = pinned.certificate)

        val error = assertThrows(CertificateMismatchException::class.java) { http.getAppList() }

        assertThat(error.message).isEqualTo("Server certificate mismatch")
        assertThat(error.cause).isInstanceOf(SSLHandshakeException::class.java)
        assertThat(http.sawCertificateMismatch()).isTrue()
        // The request never reached the host.
        assertThat(host.requests).isEmpty()
        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.CERTIFICATE_CHANGED)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo("127.0.0.1")
    }

    @Test
    fun `serverinfo falls back to plain HTTP when the certificate is not the pinned one`() {
        val host = host(identity = other)
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned.certificate)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo")
        // The fallback over HTTP keeps the flag, so the caller can tell why it was unpaired.
        assertThat(http.sawCertificateMismatch()).isTrue()
        assertThat(audit.types).containsExactly(AuditType.CERTIFICATE_CHANGED)
    }

    @Test
    fun `serverinfo falls back to plain HTTP when the host says the client is not authorized`() {
        val host = host()
        host.answer(
            "/serverinfo",
            HostReply.failure(
                "401",
                "The client is not authorized. Certificate verification failed.",
            ),
            "https",
        )
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned.certificate)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("https /serverinfo", "http /serverinfo")
            .inOrder()
        assertThat(http.sawCertificateMismatch()).isFalse()
    }

    @Test
    fun `serverinfo does not fall back for another failure of the host`() {
        val host = host()
        host.answer("/serverinfo", HostReply.failure("503", "busy"), "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned.certificate)

        val error =
            assertThrows(HostHttpResponseException::class.java) {
                http.getServerInfo(true)
            }

        assertThat(error.errorCode).isEqualTo(503)
        assertThat(host.requests.map { it.scheme }).containsExactly("https")
    }

    @Test
    fun `serverinfo falls back to plain HTTP when the platform refuses the chain of the host`() {
        val host = host()
        host.chainExtras = listOf(other.certificate)
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned.certificate)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())

        // A chain of two certificates is not the pinned one, and the refusal is not a mismatch.
        assertThat(host.requests.map { it.scheme }).containsExactly("http")
        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(audit.types).containsExactly(AuditType.TLS_FAILURE)
    }

    @Test
    fun `serverinfo does not fall back when the handshake fails for another reason`() {
        val host = host()
        host.offersCertificate = false
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned.certificate)

        assertThrows(SSLHandshakeException::class.java) { http.getServerInfo(true) }

        assertThat(host.requests).isEmpty()
        assertThat(http.sawCertificateMismatch()).isFalse()
        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.TLS_FAILURE)
        assertThat(event.host).isEqualTo("127.0.0.1")
        assertThat(event.detail).isEqualTo("error=SSLHandshakeException")
    }

    @Test
    fun `a certificate that the platform trusts is accepted, with or without a pin`() {
        val trusted = trustedIdentity()
        val store = folder.newFile("trust.p12")
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("host", trusted.certificate)
            store.outputStream().use { store(it, TRUST_PASSWORD.toCharArray()) }
        }
        val properties =
            mapOf(
                "javax.net.ssl.trustStore" to store.path,
                "javax.net.ssl.trustStoreType" to "PKCS12",
                "javax.net.ssl.trustStorePassword" to TRUST_PASSWORD,
            )
        val before = properties.keys.associateWith { System.getProperty(it) }
        properties.forEach { (key, value) -> System.setProperty(key, value) }
        try {
            val host = host(identity = trusted)
            answerApps(host)
            // The default trust manager is built with the system trust store, when the client is.
            val unpinned = clientOf(host)
            val pinnedToAnother = clientOf(host, serverCert = other.certificate)

            assertThat(unpinned.getAppList()).hasSize(1)
            assertThat(pinnedToAnother.getAppList()).hasSize(1)

            assertThat(unpinned.sawCertificateMismatch()).isFalse()
            assertThat(pinnedToAnother.sawCertificateMismatch()).isFalse()
            assertThat(audit.events).isEmpty()
        } finally {
            before.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }

    /** A self-signed certificate for 127.0.0.1 and its name, which a trust store can hold. */
    private fun trustedIdentity(): TestIdentity {
        val keyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Trusted Test Host")
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val holder =
            JcaX509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(now),
                    Date(now - day),
                    Date(now + day),
                    name,
                    keyPair.public,
                )
                .addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    GeneralNames(GeneralName(GeneralName.iPAddress, "127.0.0.1")),
                )
                .build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        return TestIdentity(keyPair, JcaX509CertificateConverter().getCertificate(holder))
    }

    @Test
    fun `a platform without a trust manager algorithm cannot build a client`() {
        val key = "ssl.TrustManagerFactory.algorithm"
        val before = Security.getProperty(key)
        Security.setProperty(key, "NoSuchTrustManagerAlgorithm")
        try {
            val error = assertThrows(RuntimeException::class.java) { clientOf(host()) }

            assertThat(error.cause).isInstanceOf(NoSuchAlgorithmException::class.java)
        } finally {
            Security.setProperty(key, before)
        }
    }

    @Test
    fun `the flag of a mismatch is cleared by the next HTTPS request`() {
        val host = host(identity = other)
        answerApps(host)
        val http = clientOf(host, serverCert = pinned.certificate)
        assertThrows(CertificateMismatchException::class.java) { http.getAppList() }
        assertThat(http.sawCertificateMismatch()).isTrue()

        host.identity = pinned
        http.getAppList()

        assertThat(http.sawCertificateMismatch()).isFalse()
    }

    @Test
    fun `without a pinned certificate a self-signed certificate is refused and audited`() {
        val host = host()
        answerApps(host)
        val http = clientOf(host)

        assertThrows(SSLHandshakeException::class.java) { http.getAppList() }

        // Not a mismatch: there was nothing pinned to differ from.
        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(host.requests).isEmpty()
        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.TLS_FAILURE)
        assertThat(event.host).isEqualTo("127.0.0.1")
        assertThat(event.detail).startsWith("error=")
    }

    @Test
    fun `without a pinned certificate serverinfo is plain HTTP and never starts a handshake`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")

        clientOf(host).getServerInfo(true)

        assertThat(host.requests.map { it.scheme }).containsExactly("http")
        assertThat(host.failures).isEmpty()
    }

    @Test
    fun `the trust manager accepts the pinned certificate and refuses every other`() {
        val http = clientOf(host(), serverCert = pinned.certificate)
        val trust = http.trustManager()

        trust.checkServerTrusted(arrayOf(pinned.certificate), "RSA")

        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(audit.events).isEmpty()
        val error =
            assertThrows(CertificateException::class.java) {
                trust.checkServerTrusted(arrayOf(other.certificate), "RSA")
            }
        assertThat(error.message).isEqualTo("Certificate mismatch")
        assertThat(http.sawCertificateMismatch()).isTrue()
        assertThat(audit.types).containsExactly(AuditType.CERTIFICATE_CHANGED)
    }

    @Test
    fun `a chain of two certificates is never the pinned certificate`() {
        val http = clientOf(host(), serverCert = pinned.certificate)

        val error =
            assertThrows(CertificateException::class.java) {
                http
                    .trustManager()
                    .checkServerTrusted(arrayOf(pinned.certificate, other.certificate), "RSA")
            }

        // The error of the platform trust manager is passed on, and it is not a mismatch.
        assertThat(error.message).isNotEqualTo("Certificate mismatch")
        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(audit.types).containsExactly(AuditType.TLS_FAILURE)
    }

    @Test
    fun `with nothing pinned the trust manager passes the platform error on`() {
        val http = clientOf(host())

        val error =
            assertThrows(CertificateException::class.java) {
                http.trustManager().checkServerTrusted(arrayOf(pinned.certificate), "RSA")
            }

        assertThat(error.message).isNotEqualTo("Certificate mismatch")
        assertThat(http.sawCertificateMismatch()).isFalse()
        assertThat(audit.types).containsExactly(AuditType.TLS_FAILURE)
    }

    @Test
    fun `the trust manager has no issuers and never checks a client`() {
        val trust = clientOf(host()).trustManager()

        assertThat(trust.acceptedIssuers).isEmpty()
        val error =
            assertThrows(IllegalStateException::class.java) {
                trust.checkClientTrusted(arrayOf(pinned.certificate), "RSA")
            }
        assertThat(error.message).isEqualTo("Should never be called")
    }

    @Test
    fun `the host name check passes for the pinned certificate alone`() {
        val http = clientOf(host(), serverCert = pinned.certificate)
        val verifier = http.hostnameVerifier()

        assertThat(verifier.verify("any-name", sessionOf(pinned.certificate))).isTrue()
        assertThat(verifier.verify("127.0.0.1", sessionOf(pinned.certificate))).isTrue()
    }

    @Test
    fun `the host name check passes everything else to the platform check`() {
        val http = clientOf(host(), serverCert = pinned.certificate)
        val verifier = http.hostnameVerifier()
        val platform = HttpsURLConnection.getDefaultHostnameVerifier()

        for (session in
            listOf(
                sessionOf(other.certificate),
                sessionOf(pinned.certificate, other.certificate),
                unverifiedSession(),
            )) {
            assertThat(verifier.verify("den-pc.local", session))
                .isEqualTo(platform.verify("den-pc.local", session))
        }
        assertThat(verifier.verify("den-pc.local", sessionOf(other.certificate))).isFalse()
        assertThat(verifier.verify("den-pc.local", unverifiedSession())).isFalse()
    }

    @Test
    fun `nothing is pinned so no certificate passes the host name check`() {
        val verifier = clientOf(host()).hostnameVerifier()

        assertThat(verifier.verify("den-pc.local", sessionOf(pinned.certificate))).isFalse()
    }

    @Test
    fun `the key manager hands out the certificate and key of the client under one alias`() {
        val client = TestIdentities.clientRsa
        val keys = clientOf(host(), provider = providerOf(client)).keyManager()

        assertThat(keys.chooseClientAlias(arrayOf("RSA"), null, null)).isEqualTo("Limelight-RSA")
        assertThat(keys.getCertificateChain("anything"))
            .asList()
            .containsExactly(client.certificate)
        assertThat(keys.getPrivateKey("anything")).isEqualTo(client.keyPair.private)
        assertThat(keys.chooseServerAlias("RSA", null, null)).isNull()
        assertThat(keys.getClientAliases("RSA", null)).isNull()
        assertThat(keys.getServerAliases("RSA", null)).isNull()
    }

    @Test
    fun `the key manager asks the provider each time and gives null for what it lacks`() {
        val calls = mutableListOf<String>()
        val keys =
            clientOf(host(), provider = providerOf(null, null, byteArrayOf(1), calls)).keyManager()
        calls.clear()

        val chain = keys.getCertificateChain("a")
        val key = keys.getPrivateKey("a")

        // A provider without a certificate gives a chain with one null in it.
        assertThat(chain.asList()).containsExactly(null as X509Certificate?)
        assertThat(key).isNull()
        assertThat(calls).containsExactly("getClientCertificate", "getClientPrivateKey").inOrder()
    }

    private companion object {
        const val TRUST_PASSWORD = "changeit"
    }
}
