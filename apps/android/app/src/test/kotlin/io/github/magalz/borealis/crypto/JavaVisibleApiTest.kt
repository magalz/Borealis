package io.github.magalz.borealis.crypto

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.PlatformBinding
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.PairingManager
import java.io.IOException
import java.lang.reflect.Modifier
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException

/**
 * The JVM members that the Java of the app uses (`Game`, `ComputerManagerService`, `NvHTTP` and
 * `NvConnection`): they keep their names, their signatures and their `throws`, whatever language
 * the classes are written in.
 */
class JavaVisibleApiTest {
    @Test
    fun `the platform binding is a public static method that takes a context`() {
        val method = PlatformBinding::class.java.getMethod("getCryptoProvider", Context::class.java)

        assertThat(Modifier.isStatic(method.modifiers)).isTrue()
        assertThat(method.returnType).isEqualTo(LimelightCryptoProvider::class.java)
    }

    @Test
    fun `the crypto provider interface keeps its four methods`() {
        val methods =
            LimelightCryptoProvider::class.java.methods.associate {
                it.name to (it.returnType to it.parameterTypes.toList())
            }

        assertThat(LimelightCryptoProvider::class.java.isInterface).isTrue()
        assertThat(methods)
            .containsExactly(
                "getClientCertificate",
                X509Certificate::class.java to emptyList<Class<*>>(),
                "getClientPrivateKey",
                PrivateKey::class.java to emptyList<Class<*>>(),
                "getPemEncodedClientCertificate",
                ByteArray::class.java to emptyList<Class<*>>(),
                "encodeBase64String",
                String::class.java to listOf<Class<*>>(ByteArray::class.java),
            )
    }

    @Test
    fun `the Android provider has a public constructor of a context and implements the interface`() {
        val constructor = AndroidCryptoProvider::class.java.getConstructor(Context::class.java)

        assertThat(Modifier.isPublic(constructor.modifiers)).isTrue()
        assertThat(LimelightCryptoProvider::class.java.isAssignableFrom(AndroidCryptoProvider::class.java))
            .isTrue()
    }

    @Test
    fun `the pairing manager keeps its constructor, its static PIN and its throwing pair`() {
        val pair =
            PairingManager::class.java.getMethod("pair", String::class.java, String::class.java)
        val generate = PairingManager::class.java.getMethod("generatePinString")

        assertThat(pair.returnType).isEqualTo(PairingManager.PairState::class.java)
        assertThat(pair.exceptionTypes.toList())
            .containsExactly(IOException::class.java, XmlPullParserException::class.java)
        assertThat(Modifier.isStatic(generate.modifiers)).isTrue()
        assertThat(generate.returnType).isEqualTo(String::class.java)
        assertThat(PairingManager::class.java.getMethod("getPairedCert").returnType)
            .isEqualTo(X509Certificate::class.java)
        assertThat(PairingManager::class.java.constructors.single().parameterTypes.toList())
            .containsExactly(
                com.limelight.nvstream.http.NvHTTP::class.java,
                LimelightCryptoProvider::class.java,
            )
            .inOrder()
    }
}
