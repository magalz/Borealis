package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.cert.X509Certificate
import java.util.LinkedList
import okhttp3.HttpUrl
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException

/**
 * The JVM members of `NvHTTP` that the Java of the app uses (`ComputerManagerService`,
 * `NvConnection`, `Game` and the legacy readers): they keep their names, their signatures and their
 * `throws`, whatever language the class is written in.
 */
class NvHttpJavaApiTest {
    private val io = setOf(IOException::class.java)
    private val ioAndXml = setOf(IOException::class.java, XmlPullParserException::class.java)

    private fun method(name: String, vararg parameters: Class<*>): Method =
        NvHTTP::class.java.getMethod(name, *parameters)

    private fun assertMember(
        method: Method,
        returns: Class<*>,
        throws: Set<Class<*>> = emptySet(),
        static: Boolean = false,
    ) {
        val description = method.toString()
        assertWithMessage(description).that(method.returnType).isEqualTo(returns)
        assertWithMessage(description).that(method.exceptionTypes.toSet()).isEqualTo(throws)
        assertWithMessage(description).that(Modifier.isStatic(method.modifiers)).isEqualTo(static)
        assertWithMessage(description).that(Modifier.isPublic(method.modifiers)).isTrue()
    }

    @Test
    fun `the class is public and has one public constructor`() {
        assertThat(Modifier.isPublic(NvHTTP::class.java.modifiers)).isTrue()
        val constructor = NvHTTP::class.java.constructors.single()

        assertThat(constructor.parameterTypes.toList())
            .containsExactly(
                AddressTuple::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                X509Certificate::class.java,
                LimelightCryptoProvider::class.java,
            )
            .inOrder()
        assertThat(constructor.exceptionTypes.toSet()).isEqualTo(io)
    }

    @Test
    fun `the constants stay compile-time ints for the Java that uses them`() {
        for ((name, value) in
            mapOf(
                "DEFAULT_HTTP_PORT" to 47989,
                "SHORT_CONNECTION_TIMEOUT" to 3000,
                "LONG_CONNECTION_TIMEOUT" to 5000,
                "READ_TIMEOUT" to 7000,
            )) {
            val field = NvHTTP::class.java.getField(name)

            assertWithMessage(name).that(field.type).isEqualTo(Int::class.javaPrimitiveType)
            assertWithMessage(name).that(Modifier.isStatic(field.modifiers)).isTrue()
            assertWithMessage(name).that(Modifier.isFinal(field.modifiers)).isTrue()
            assertWithMessage(name).that(field.getInt(null)).isEqualTo(value)
        }
    }

    @Test
    fun `the requests keep their signatures and their checked exceptions`() {
        val int = Int::class.javaPrimitiveType!!
        val boolean = Boolean::class.javaPrimitiveType!!
        val string = String::class.java

        assertMember(method("getHttpsUrl", boolean), HttpUrl::class.java, io)
        assertMember(method("getServerInfo", boolean), string, ioAndXml)
        assertMember(method("getComputerDetails", string), ComputerDetails::class.java, ioAndXml)
        assertMember(method("getComputerDetails", boolean), ComputerDetails::class.java, ioAndXml)
        assertMember(method("getPairState"), PairingManager.PairState::class.java, ioAndXml)
        assertMember(method("getAppById", int), NvApp::class.java, ioAndXml)
        assertMember(method("getAppByName", string), NvApp::class.java, ioAndXml)
        assertMember(method("getAppListRaw"), string, io)
        assertMember(
            method("getAppList"),
            LinkedList::class.java,
            setOf(
                HostHttpResponseException::class.java,
                IOException::class.java,
                XmlPullParserException::class.java,
            ),
        )
        assertMember(method("getBoxArt", NvApp::class.java), InputStream::class.java, io)
        assertMember(method("unpair"), boolean, io)
        assertMember(method("cancelPendingRequests"), Void.TYPE)
        assertMember(method("sawCertificateMismatch"), boolean)
        assertMember(method("getPairingManager"), PairingManager::class.java)
        assertMember(
            method("launchApp", ConnectionContext::class.java, string, int, boolean),
            boolean,
            ioAndXml,
        )
        assertMember(method("quitApp"), boolean, ioAndXml)
    }

    @Test
    fun `the readers of serverinfo keep their signatures and their checked exceptions`() {
        val int = Int::class.javaPrimitiveType!!
        val long = Long::class.javaPrimitiveType!!
        val boolean = Boolean::class.javaPrimitiveType!!
        val string = String::class.java

        assertMember(method("getServerVersion", string), string, ioAndXml)
        assertMember(method("getPairState", string), PairingManager.PairState::class.java, ioAndXml)
        assertMember(method("getMaxLumaPixelsH264", string), long, ioAndXml)
        assertMember(method("getMaxLumaPixelsHEVC", string), long, ioAndXml)
        assertMember(method("getServerCodecModeSupport", string), long, ioAndXml)
        assertMember(method("getGpuType", string), string, ioAndXml)
        assertMember(method("getGfeVersion", string), string, ioAndXml)
        assertMember(method("supports4K", string), boolean, ioAndXml)
        assertMember(method("getCurrentGame", string), int, ioAndXml)
        assertMember(method("getHttpsPort", string), int)
        assertMember(method("getExternalPort", string), int)
        assertMember(method("getServerMajorVersion", string), int, ioAndXml)
        assertMember(method("getServerAppVersionQuad", string), IntArray::class.java, ioAndXml)
    }

    @Test
    fun `the app list parser is a public static method on the class`() {
        assertMember(
            method("getAppListByReader", Reader::class.java),
            LinkedList::class.java,
            ioAndXml,
            static = true,
        )
    }

    @Test
    fun `the members that Java never calls stay out of the public API`() {
        val publicNames = NvHTTP::class.java.methods.map { it.name }.toSet()

        // Package-private in the Java: the pairing manager of the same package is the only caller.
        assertThat(publicNames)
            .containsNoneOf(
                "getXmlString",
                "setServerCert",
                "executePairingCommand",
                "executePairingChallenge",
            )
    }
}
