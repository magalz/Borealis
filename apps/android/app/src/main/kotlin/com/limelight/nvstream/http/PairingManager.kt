package com.limelight.nvstream.http

import com.limelight.LimeLog
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.Key
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Locale
import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.engines.AESLightEngine
import org.bouncycastle.crypto.params.KeyParameter
import org.xmlpull.v1.XmlPullParserException

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
// The stack traces that the Java prints to stderr and the RuntimeException that wraps their cause
// stay as they were: PrintStackTrace and TooGenericExceptionThrown are suppressed for that.
@Suppress("TooManyFunctions", "PrintStackTrace", "TooGenericExceptionThrown")
class PairingManager(private val http: NvHTTP, cryptoProvider: LimelightCryptoProvider) {
    private val cert: X509Certificate? = cryptoProvider.clientCertificate
    private val pemCertBytes: ByteArray? = cryptoProvider.pemEncodedClientCertificate
    private val pk: PrivateKey? = cryptoProvider.clientPrivateKey

    private var serverCert: X509Certificate? = null

    enum class PairState {
        NOT_PAIRED,
        PAIRED,
        PIN_WRONG,
        FAILED,
        ALREADY_IN_PROGRESS,
    }

    private fun extractPlainCert(text: String): X509Certificate? {
        // Plaincert may be null if another client is already trying to pair
        val certText = NvHTTP.getXmlString(text, "plaincert", false)
        if (certText != null) {
            val certBytes = hexToBytes(certText)

            try {
                val cf = CertificateFactory.getInstance("X.509")
                return cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate?
            } catch (e: CertificateException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        } else {
            return null
        }
    }

    private fun generateRandomBytes(length: Int): ByteArray {
        val rand = ByteArray(length)
        SecureRandom().nextBytes(rand)
        return rand
    }

    val pairedCert: X509Certificate?
        get() = serverCert

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    @Throws(IOException::class, XmlPullParserException::class)
    fun pair(serverInfo: String, pin: String): PairState {
        val serverMajorVersion = http.getServerMajorVersion(serverInfo)
        LimeLog.info("Pairing with server generation: $serverMajorVersion")
        val hashAlgo: PairingHashAlgorithm =
            if (serverMajorVersion >= GEN7) {
                // Gen 7+ uses SHA-256 hashing
                Sha256PairingHash()
            } else {
                // Prior to Gen 7, SHA-1 is used
                Sha1PairingHash()
            }

        // Generate a salt for hashing the PIN
        val salt = generateRandomBytes(SALT_LENGTH)

        // Combine the salt and pin, then create an AES key from them
        val aesKey = generateAesKey(hashAlgo, saltPin(salt, pin))

        // Send the salt and get the server cert. This doesn't have a read timeout
        // because the user must enter the PIN before the server responds
        val getCert =
            http.executePairingCommand(
                "phrase=getservercert&salt=" +
                    bytesToHex(salt) +
                    "&clientcert=" +
                    bytesToHex(checkNotNull(pemCertBytes)),
                false,
            )
        if (NvHTTP.getXmlString(getCert, "paired", true) != "1") {
            return PairState.FAILED
        }

        // Save this cert for retrieval later
        val plainCert = extractPlainCert(getCert)
        serverCert = plainCert
        if (plainCert == null) {
            // Attempting to pair while another device is pairing will cause GFE
            // to give an empty cert in the response.
            http.unpair()
            return PairState.ALREADY_IN_PROGRESS
        }

        // Require this cert for TLS to this host
        http.setServerCert(plainCert)

        // Generate a random challenge and encrypt it with our AES key
        val randomChallenge = generateRandomBytes(CHALLENGE_LENGTH)
        val encryptedChallenge = encryptAes(randomChallenge, aesKey)

        // Send the encrypted challenge to the server
        val challengeResp =
            http.executePairingCommand("clientchallenge=" + bytesToHex(encryptedChallenge), true)
        if (NvHTTP.getXmlString(challengeResp, "paired", true) != "1") {
            http.unpair()
            return PairState.FAILED
        }

        // Decode the server's response and subsequent challenge
        val encServerChallengeResponse =
            hexToBytes(checkNotNull(NvHTTP.getXmlString(challengeResp, "challengeresponse", true)))
        val decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey)

        // These ranges can run past the end of a short answer: Arrays pads that with zeros.
        val serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, hashAlgo.hashLength)
        val serverChallenge =
            Arrays.copyOfRange(
                decServerChallengeResponse,
                hashAlgo.hashLength,
                hashAlgo.hashLength + CHALLENGE_LENGTH,
            )

        // Using another 16 bytes secret, compute a challenge response hash using the secret, our
        // cert sig, and the challenge
        val clientSecret = generateRandomBytes(SECRET_LENGTH)
        val challengeRespHash =
            hashAlgo.hashData(
                concatBytes(
                    concatBytes(serverChallenge, checkNotNull(cert).signature),
                    clientSecret,
                )
            )
        val challengeRespEncrypted = encryptAes(challengeRespHash, aesKey)
        val secretResp =
            http.executePairingCommand(
                "serverchallengeresp=" + bytesToHex(challengeRespEncrypted),
                true,
            )
        if (NvHTTP.getXmlString(secretResp, "paired", true) != "1") {
            http.unpair()
            return PairState.FAILED
        }

        // Get the server's signed secret
        val serverSecretResp =
            hexToBytes(checkNotNull(NvHTTP.getXmlString(secretResp, "pairingsecret", true)))
        val serverSecret = Arrays.copyOfRange(serverSecretResp, 0, SECRET_LENGTH)
        val serverSignature =
            Arrays.copyOfRange(serverSecretResp, SECRET_LENGTH, serverSecretResp.size)

        // Ensure the authenticity of the data
        if (!verifySignature(serverSecret, serverSignature, plainCert)) {
            // Cancel the pairing process
            http.unpair()

            // Looks like a MITM
            return PairState.FAILED
        }

        // Ensure the server challenge matched what we expected (aka the PIN was correct)
        val serverChallengeRespHash =
            hashAlgo.hashData(
                concatBytes(concatBytes(randomChallenge, plainCert.signature), serverSecret)
            )
        if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
            // Cancel the pairing process
            http.unpair()

            // Probably got the wrong PIN
            return PairState.PIN_WRONG
        }

        // Send the server our signed secret
        val clientPairingSecret =
            concatBytes(clientSecret, signData(clientSecret, checkNotNull(pk)))
        val clientSecretResp =
            http.executePairingCommand(
                "clientpairingsecret=" + bytesToHex(clientPairingSecret),
                true,
            )
        if (NvHTTP.getXmlString(clientSecretResp, "paired", true) != "1") {
            http.unpair()
            return PairState.FAILED
        }

        // Do the initial challenge (seems necessary for us to show as paired)
        val pairChallenge = http.executePairingChallenge()
        if (NvHTTP.getXmlString(pairChallenge, "paired", true) != "1") {
            http.unpair()
            return PairState.FAILED
        }

        return PairState.PAIRED
    }

    private interface PairingHashAlgorithm {
        val hashLength: Int

        fun hashData(data: ByteArray): ByteArray
    }

    private class Sha1PairingHash : PairingHashAlgorithm {
        override val hashLength: Int
            get() = SHA1_LENGTH

        override fun hashData(data: ByteArray): ByteArray {
            try {
                val md = MessageDigest.getInstance("SHA-1")
                return md.digest(data)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }
    }

    private class Sha256PairingHash : PairingHashAlgorithm {
        override val hashLength: Int
            get() = SHA256_LENGTH

        override fun hashData(data: ByteArray): ByteArray {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                return md.digest(data)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        private const val GEN7 = 7
        private const val SHA1_LENGTH = 20
        private const val SHA256_LENGTH = 32
        private const val SALT_LENGTH = 16
        private const val CHALLENGE_LENGTH = 16
        private const val SECRET_LENGTH = 16
        private const val AES_KEY_LENGTH = 16
        private const val PIN_DIGIT_VALUES = 10
        private const val HEX_RADIX = 16
        private const val NIBBLE_BITS = 4
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_MASK = 0x0F

        private const val HEX_DIGITS = "0123456789ABCDEF"

        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and BYTE_MASK
                hexChars[j * 2] = HEX_DIGITS[v ushr NIBBLE_BITS]
                hexChars[j * 2 + 1] = HEX_DIGITS[v and NIBBLE_MASK]
            }
            return String(hexChars)
        }

        private fun hexToBytes(s: String): ByteArray {
            val len = s.length
            require(len % 2 == 0) { "Illegal string length: $len" }

            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                // Character.digit is -1 for a character that is not a digit, where digitToInt
                // throws
                data[i / 2] =
                    ((Character.digit(s[i], HEX_RADIX) shl NIBBLE_BITS) +
                            Character.digit(s[i + 1], HEX_RADIX))
                        .toByte()
            }
            return data
        }

        private fun saltPin(salt: ByteArray, pin: String): ByteArray {
            val saltedPin = ByteArray(salt.size + pin.length)
            System.arraycopy(salt, 0, saltedPin, 0, salt.size)
            System.arraycopy(pin.toByteArray(Charsets.UTF_8), 0, saltedPin, salt.size, pin.length)
            return saltedPin
        }

        @Throws(NoSuchAlgorithmException::class)
        private fun getSha256SignatureInstanceForKey(key: Key): Signature =
            when (checkNotNull(key.algorithm)) {
                "RSA" -> Signature.getInstance("SHA256withRSA")
                "EC" -> Signature.getInstance("SHA256withECDSA")
                else -> throw NoSuchAlgorithmException("Unhandled key algorithm: " + key.algorithm)
            }

        // The three exceptions that the Java caught together are all GeneralSecurityExceptions.
        private fun verifySignature(
            data: ByteArray,
            signature: ByteArray,
            cert: Certificate,
        ): Boolean {
            try {
                val sig = getSha256SignatureInstanceForKey(cert.publicKey)
                sig.initVerify(cert.publicKey)
                sig.update(data)
                return sig.verify(signature)
            } catch (e: GeneralSecurityException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        private fun signData(data: ByteArray, key: PrivateKey): ByteArray {
            try {
                val sig = getSha256SignatureInstanceForKey(key)
                sig.initSign(key)
                sig.update(data)
                return sig.sign()
            } catch (e: GeneralSecurityException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        private fun performBlockCipher(blockCipher: BlockCipher, input: ByteArray): ByteArray {
            val blockSize = blockCipher.blockSize
            val blockRoundedSize = (input.size + (blockSize - 1)) and (blockSize - 1).inv()

            val blockRoundedInputData = Arrays.copyOf(input, blockRoundedSize)
            val blockRoundedOutputData = ByteArray(blockRoundedSize)

            for (offset in 0 until blockRoundedSize step blockSize) {
                blockCipher.processBlock(
                    blockRoundedInputData,
                    offset,
                    blockRoundedOutputData,
                    offset,
                )
            }

            return blockRoundedOutputData
        }

        private fun decryptAes(encryptedData: ByteArray, aesKey: ByteArray): ByteArray {
            val aesEngine: BlockCipher = AESLightEngine()
            aesEngine.init(false, KeyParameter(aesKey))
            return performBlockCipher(aesEngine, encryptedData)
        }

        private fun encryptAes(plaintextData: ByteArray, aesKey: ByteArray): ByteArray {
            val aesEngine: BlockCipher = AESLightEngine()
            aesEngine.init(true, KeyParameter(aesKey))
            return performBlockCipher(aesEngine, plaintextData)
        }

        private fun generateAesKey(hashAlgo: PairingHashAlgorithm, keyData: ByteArray): ByteArray =
            Arrays.copyOf(hashAlgo.hashData(keyData), AES_KEY_LENGTH)

        private fun concatBytes(a: ByteArray, b: ByteArray): ByteArray {
            val c = ByteArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun generatePinString(): String {
            val r = SecureRandom()
            return String.format(
                null as Locale?,
                "%d%d%d%d",
                r.nextInt(PIN_DIGIT_VALUES),
                r.nextInt(PIN_DIGIT_VALUES),
                r.nextInt(PIN_DIGIT_VALUES),
                r.nextInt(PIN_DIGIT_VALUES),
            )
        }
    }
}
