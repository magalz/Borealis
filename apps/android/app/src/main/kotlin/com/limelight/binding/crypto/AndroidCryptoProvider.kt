package com.limelight.binding.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import com.limelight.LimeLog
import com.limelight.nvstream.http.LimelightCryptoProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class AndroidCryptoProvider(c: Context) : LimelightCryptoProvider {
    private val certFile: File
    private val keyFile: File

    private var cert: X509Certificate? = null
    private var key: PrivateKey? = null
    private var pemCertBytes: ByteArray? = null

    init {
        val dataPath = c.filesDir.absolutePath

        certFile = File(dataPath + File.separator + "client.crt")
        keyFile = File(dataPath + File.separator + "client.key")
    }

    private fun loadFileToBytes(f: File): ByteArray? {
        if (!f.exists()) {
            return null
        }

        return try {
            FileInputStream(f).use { fin ->
                val fileData = ByteArray(f.length().toInt())
                // A short read is a failure to read
                if (fin.read(fileData).toLong() != f.length()) null else fileData
            }
        } catch (ignored: IOException) {
            null
        }
    }

    // Converted as is from the inherited Java; the RuntimeException that wraps the cause of a
    // missing algorithm stays as it was (Sprint 18c backlog).
    @Suppress("TooGenericExceptionThrown")
    private fun loadCertKeyPair(): Boolean {
        val certBytes = loadFileToBytes(certFile)
        val keyBytes = loadFileToBytes(keyFile)

        // If either file was missing, we definitely can't succeed
        if (certBytes == null || keyBytes == null) {
            LimeLog.info("Missing cert or key; need to generate a new one")
            return false
        }

        return try {
            val certFactory = CertificateFactory.getInstance("X.509", bcProvider)
            cert =
                certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate?
            pemCertBytes = certBytes
            val keyFactory = KeyFactory.getInstance("RSA", bcProvider)
            key = keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            true
        } catch (expected: CertificateException) {
            // May happen if the cert is corrupt
            LimeLog.warning("Corrupted certificate")
            false
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (expected: InvalidKeySpecException) {
            // May happen if the key is corrupt
            LimeLog.warning("Corrupted key")
            false
        }
    }

    // Converted as is from the inherited Java; the TrulyRandom lint suppression and the generic
    // RuntimeException that wraps every failure stay as they were (Sprint 18c backlog).
    @SuppressLint("TrulyRandom")
    @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
    private fun generateCertKeyPair(): Boolean {
        val snBytes = ByteArray(SERIAL_BYTES)
        SecureRandom().nextBytes(snBytes)

        val keyPair: KeyPair
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA", bcProvider)
            keyPairGenerator.initialize(KEY_SIZE_BITS)
            keyPair = keyPairGenerator.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }

        val now = Date()

        // Expires in 20 years
        val calendar = Calendar.getInstance()
        calendar.time = now
        calendar.add(Calendar.YEAR, VALIDITY_YEARS)
        val expirationDate = calendar.time

        val serial = BigInteger(snBytes).abs()

        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, "NVIDIA GameStream Client")
        val name: X500Name = nameBuilder.build()

        val certBuilder =
            X509v3CertificateBuilder(
                name,
                serial,
                now,
                expirationDate,
                Locale.ENGLISH,
                name,
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded),
            )

        try {
            val sigGen =
                JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(bcProvider)
                    .build(keyPair.private)
            cert =
                JcaX509CertificateConverter()
                    .setProvider(bcProvider)
                    .getCertificate(certBuilder.build(sigGen))
            key = keyPair.private
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        LimeLog.info("Generated a new key pair")

        // Save the resulting pair
        saveCertKeyPair()

        return true
    }

    // Converted as is from the inherited Java; the stack trace that it prints to the standard error
    // stream stays as it was (Sprint 18c backlog).
    @Suppress("PrintStackTrace")
    private fun saveCertKeyPair() {
        try {
            FileOutputStream(certFile).use { certOut ->
                FileOutputStream(keyFile).use { keyOut ->
                    writePemCertificate(certOut)

                    // Write the private out in PKCS8 format
                    keyOut.write(checkNotNull(key).encoded)

                    LimeLog.info("Saved generated key pair to disk")
                }
            }
        } catch (e: IOException) {
            // This isn't good because it means we'll have
            // to re-pair next time
            e.printStackTrace()
        }
    }

    private fun writePemCertificate(certOut: OutputStream) {
        // Write the certificate in OpenSSL PEM format (important for the server)
        val strWriter = StringWriter()
        JcaPEMWriter(strWriter).use { pemWriter -> pemWriter.writeObject(cert) }

        // Line endings MUST be UNIX for the PC to accept the cert properly
        OutputStreamWriter(certOut).use { certWriter ->
            val pemStr = strWriter.buffer.toString()
            for (i in pemStr.indices) {
                val c = pemStr[i]
                if (c != '\r') {
                    certWriter.append(c)
                }
            }
        }
    }

    override val clientCertificate: X509Certificate?
        get() = loadOrGenerateCertificate()

    override val clientPrivateKey: PrivateKey?
        get() = loadOrGeneratePrivateKey()

    override val pemEncodedClientCertificate: ByteArray?
        get() =
            synchronized(globalCryptoLock) {
                // Call our helper function to do the cert loading/generation for us
                loadOrGenerateCertificate()

                // Return a cached value if we have it
                pemCertBytes
            }

    /**
     * Loads the pair from disk or, when that fails, generates one and loads what was generated.
     * False when there is no pair to use. The caller holds [globalCryptoLock].
     */
    private fun loadOrGenerateCertKeyPair(): Boolean {
        // No loaded pair yet, let's see if we have one on disk
        if (loadCertKeyPair()) {
            // Got one
            return true
        }

        // Try to generate a new key pair
        if (!generateCertKeyPair()) {
            // Failed
            return false
        }

        // Load the generated pair
        loadCertKeyPair()
        return true
    }

    private fun loadOrGenerateCertificate(): X509Certificate? {
        // Use a lock here to ensure only one guy will be generating or loading
        // the certificate and key at a time
        return synchronized(globalCryptoLock) {
            // Return a loaded cert if we have one, or the one that the pair gives
            cert ?: if (loadOrGenerateCertKeyPair()) cert else null
        }
    }

    private fun loadOrGeneratePrivateKey(): PrivateKey? {
        // Use a lock here to ensure only one guy will be generating or loading
        // the certificate and key at a time
        return synchronized(globalCryptoLock) {
            // Return a loaded key if we have one, or the one that the pair gives
            key ?: if (loadOrGenerateCertKeyPair()) key else null
        }
    }

    override fun encodeBase64String(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    private companion object {
        private const val SERIAL_BYTES = 8
        private const val KEY_SIZE_BITS = 2048
        private const val VALIDITY_YEARS = 20

        private val globalCryptoLock = Any()

        private val bcProvider: Provider = BouncyCastleProvider()
    }
}
