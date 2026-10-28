package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How `NvHTTP` reads the `status_code` of the root tag of an answer: every `getXml...` call checks
 * it before it looks for its tag, whatever the HTTP status of the response was.
 */
@RunWith(RobolectricTestRunner::class)
class HostStatusTest {
    private val http = parsingClient()

    private fun statusOf(rootAttributes: String): HostHttpResponseException =
        assertThrows(HostHttpResponseException::class.java) {
            http.getServerVersion("<root $rootAttributes><appversion>7.1.431.0</appversion></root>")
        }

    @Test
    fun `a status of 200 passes and any other status raises the error of the host`() {
        assertThat(
                http.getServerVersion(
                    """<root status_code="200"><appversion>7.1.431.0</appversion></root>"""
                )
            )
            .isEqualTo("7.1.431.0")

        val error = statusOf("""status_code="401" status_message="The client is not authorized."""")

        assertThat(error.errorCode).isEqualTo(401)
        assertThat(error.errorMessage).isEqualTo("The client is not authorized.")
        assertThat(error.message)
            .isEqualTo("Host PC returned error: The client is not authorized. (Error code: 401)")
        assertThat(error).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a status without a message has a null message`() {
        val error = statusOf("""status_code="404"""")

        assertThat(error.errorCode).isEqualTo(404)
        assertThat(error.errorMessage).isNull()
        assertThat(error.message).isEqualTo("Host PC returned error: null (Error code: 404)")
    }

    @Test
    fun `the status 0xFFFFFFFF is read as -1`() {
        // Integer.parseInt would refuse it: GFE sends it in rare cases.
        val error = statusOf("""status_code="4294967295" status_message="oops"""")

        assertThat(error.errorCode).isEqualTo(-1)
        assertThat(error.errorMessage).isEqualTo("oops")
    }

    @Test
    fun `the status -1 with the message Invalid is a missing audio capture device`() {
        for (code in listOf("-1", "4294967295")) {
            val error = statusOf("""status_code="$code" status_message="Invalid"""")

            assertWithMessage(code).that(error.errorCode).isEqualTo(418)
            assertWithMessage(code)
                .that(error.errorMessage)
                .isEqualTo("Missing audio capture device. Reinstall GeForce Experience.")
        }
    }

    @Test
    fun `the status -1 with another message or none keeps its code and message`() {
        val other = statusOf("""status_code="-1" status_message="invalid"""")
        val none = statusOf("""status_code="-1"""")

        assertThat(other.errorCode).isEqualTo(-1)
        assertThat(other.errorMessage).isEqualTo("invalid")
        assertThat(none.errorCode).isEqualTo(-1)
        assertThat(none.errorMessage).isNull()
    }

    @Test
    fun `a status above 32 bits is cut to its low 32 bits, so 2^32 plus 200 is a success`() {
        val answer = "<root status_code=\"4294967496\"><appversion>7.1.431.0</appversion></root>"

        assertThat(http.getServerVersion(answer)).isEqualTo("7.1.431.0")
        assertThat(statusOf("""status_code="4294967497"""").errorCode).isEqualTo(201)
        assertThat(statusOf("""status_code="8589934591"""").errorCode).isEqualTo(-1)
    }

    @Test
    fun `a status that is not a number is a NumberFormatException`() {
        for (code in listOf("ok", "", " 200", "200 ", "0xC8", "2.0", "99999999999999999999")) {
            assertWithMessage("'$code'")
                .that(
                    assertThrows(NumberFormatException::class.java) {
                        http.getServerVersion("""<root status_code="$code"></root>""")
                    }
                )
                .isNotNull()
        }
    }

    @Test
    fun `a root without a status is a NumberFormatException`() {
        assertThrows(NumberFormatException::class.java) {
            http.getServerVersion("<root><appversion>7.1.431.0</appversion></root>")
        }
    }

    @Test
    fun `only a root tag is checked`() {
        // The tag that holds the answer is not named root, so its status_code is not looked at.
        assertThat(
                http.getServerVersion(
                    """<other status_code="500"><appversion>7.1.431.0</appversion></other>"""
                )
            )
            .isEqualTo("7.1.431.0")
        // A root tag inside another is checked when the parser reaches it.
        assertThat(
                assertThrows(HostHttpResponseException::class.java) {
                        http.getServerVersion(
                            """<a><root status_code="403"/><appversion>1.2.3.4</appversion></a>"""
                        )
                    }
                    .errorCode
            )
            .isEqualTo(403)
    }

    @Test
    fun `the status is checked before the tag is looked for`() {
        // The tag is present, but the status says the request failed.
        val error =
            assertThrows(HostHttpResponseException::class.java) {
                http.getServerVersion(
                    """<root status_code="503" status_message="busy"><appversion>1.2.3.4</appversion></root>"""
                )
            }

        assertThat(error.errorCode).isEqualTo(503)
    }

    @Test
    fun `an optional lookup raises the error of the host too`() {
        val error =
            assertThrows(HostHttpResponseException::class.java) {
                http.getGpuType("""<root status_code="400" status_message="bad"/>""")
            }

        assertThat(error.errorCode).isEqualTo(400)
        assertThat(error.errorMessage).isEqualTo("bad")
    }

    @Test
    fun `the not found answer of a host that has no route is a 404 without a message`() {
        // An unknown path, and /unpair, which LuminalShine does not route, answer 200 OK with this
        // XML body (host contract, section 3).
        for ((version, name) in LuminalFixtures.NOT_FOUND_RESPONSES) {
            val text = String(LuminalFixtures.bytes(version, name), Charsets.ISO_8859_1)
            val body = checkNotNull(Regex("<root[^>]*/>").find(text)).value

            val error =
                assertThrows(HostHttpResponseException::class.java) {
                    http.getComputerDetails(body)
                }

            assertWithMessage("$version $name").that(error.errorCode).isEqualTo(404)
            assertWithMessage("$version $name").that(error.errorMessage).isNull()
        }
    }

    @Test
    fun `the constants of the client are those of the host contract and the old timeouts`() {
        assertThat(NvHTTP.DEFAULT_HTTP_PORT).isEqualTo(47989)
        assertThat(NvHTTP.SHORT_CONNECTION_TIMEOUT).isEqualTo(3000)
        assertThat(NvHTTP.LONG_CONNECTION_TIMEOUT).isEqualTo(5000)
        assertThat(NvHTTP.READ_TIMEOUT).isEqualTo(7000)
    }
}
