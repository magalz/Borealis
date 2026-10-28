package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import java.lang.reflect.Modifier
import java.security.cert.X509Certificate
import javax.crypto.SecretKey
import org.junit.Test

/**
 * The state `NvConnection` and `NvHTTP` share through a [ConnectionContext]: public, mutable
 * fields, all unset on a new instance. The inherited Java reads and writes them as fields, so they
 * stay fields.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
class ConnectionContextCharacterizationTest {
    private val int: Class<*> = Integer.TYPE
    private val boolean: Class<*> = java.lang.Boolean.TYPE

    /** The fields of the class, by name, with their types. */
    private val fields: Map<String, Class<*>> =
        mapOf(
            "serverAddress" to ComputerDetails.AddressTuple::class.java,
            "httpsPort" to int,
            "isNvidiaServerSoftware" to boolean,
            "serverCert" to X509Certificate::class.java,
            "streamConfig" to StreamConfiguration::class.java,
            "connListener" to NvConnectionListener::class.java,
            "riKey" to SecretKey::class.java,
            "riKeyId" to int,
            "serverAppVersion" to String::class.java,
            "serverGfeVersion" to String::class.java,
            "serverCodecModeSupport" to int,
            "rtspSessionUrl" to String::class.java,
            "negotiatedWidth" to int,
            "negotiatedHeight" to int,
            "negotiatedHdr" to boolean,
            "negotiatedRemoteStreaming" to int,
            "negotiatedPacketSize" to int,
            "videoCapabilities" to int,
        )

    @Test
    fun `the context has exactly these public mutable fields`() {
        val declared =
            ConnectionContext::class.java.fields.filterNot { Modifier.isStatic(it.modifiers) }

        assertThat(declared.map { it.name }).containsExactlyElementsIn(fields.keys)
        for (field in declared) {
            assertWithMessage(field.name).that(field.type).isEqualTo(fields.getValue(field.name))
            assertWithMessage(field.name).that(Modifier.isFinal(field.modifiers)).isFalse()
        }
    }

    @Test
    fun `a new context has every reference unset and every number zero`() {
        val context = ConnectionContext()

        for (name in fields.keys) {
            val value = ConnectionContext::class.java.getField(name).get(context)
            val default: Any? =
                when (fields.getValue(name)) {
                    boolean -> false
                    int -> 0
                    else -> null
                }
            assertWithMessage(name).that(value).isEqualTo(default)
        }
    }
}
