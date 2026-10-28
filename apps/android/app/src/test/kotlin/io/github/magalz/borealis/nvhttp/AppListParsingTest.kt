package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.StringReader
import java.util.NoSuchElementException
import java.util.logging.Level
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

/**
 * `NvHTTP.getAppListByReader`: the app list of a host, written after the host contract (there is no
 * capture of `/applist`). LuminalShine writes `IsHdrSupported`, `AppTitle` and `ID` for each app.
 */
@RunWith(RobolectricTestRunner::class)
class AppListParsingTest {
    private fun parse(xml: String): List<NvApp> = NvHTTP.getAppListByReader(StringReader(xml))

    private fun app(title: String? = "Game", id: String? = "1", hdr: String? = "0"): String =
        "<App>" +
            hdr?.let { "<IsHdrSupported>$it</IsHdrSupported>" }.orEmpty() +
            title?.let { "<AppTitle>$it</AppTitle>" }.orEmpty() +
            id?.let { "<ID>$it</ID>" }.orEmpty() +
            "</App>"

    private fun list(vararg apps: String): String =
        """<?xml version="1.0" encoding="utf-8"?><root status_code="200">${apps.joinToString("")}</root>"""

    private fun NvApp.summary(): String = "$appName/$appId/$isHdrSupported"

    @Test
    fun `the apps of a host come back in order with their id, title and HDR flag`() {
        val apps =
            parse(
                list(
                    app("Desktop", "881448767", "1"),
                    app("Steam Big Picture", "1093255277", "0"),
                    app("Cyberpunk 2077", "1234", "1"),
                )
            )

        assertThat(apps.map { it.summary() })
            .containsExactly(
                "Desktop/881448767/true",
                "Steam Big Picture/1093255277/false",
                "Cyberpunk 2077/1234/true",
            )
            .inOrder()
        assertThat(apps).hasSize(3)
        assertThat(apps.all { it.isInitialized }).isTrue()
    }

    @Test
    fun `the order of the tags inside an app does not matter`() {
        val apps =
            parse(
                list(
                    "<App><ID>5</ID><AppTitle>First</AppTitle><IsHdrSupported>1</IsHdrSupported></App>",
                    "<App><AppTitle>Second</AppTitle><ID>6</ID></App>",
                )
            )

        assertThat(apps.map { it.summary() })
            .containsExactly("First/5/true", "Second/6/false")
            .inOrder()
    }

    @Test
    fun `an app without the HDR tag, or with another value than 1, is not HDR`() {
        val apps =
            parse(
                list(
                    app(hdr = null, id = "1"),
                    app(hdr = "0", id = "2"),
                    app(hdr = "01", id = "3"),
                    app(hdr = "true", id = "4"),
                    app(hdr = "1", id = "5"),
                )
            )

        assertThat(apps.map { it.isHdrSupported })
            .containsExactly(false, false, false, false, true)
            .inOrder()
    }

    @Test
    fun `an app without a title, or with an empty one, has an empty name and stays in the list`() {
        val apps = parse(list(app(title = null, id = "7"), app(title = "", id = "8")))

        assertThat(apps.map { it.summary() }).containsExactly("/7/false", "/8/false").inOrder()
    }

    @Test
    fun `a title keeps its spaces and its entities are decoded`() {
        val apps =
            parse(list(app(title = " Half &amp; Half &lt;3&gt; ", id = "9"), app("Café", "10")))

        assertThat(apps.map { it.appName }).containsExactly(" Half & Half <3> ", "Café").inOrder()
    }

    @Test
    fun `an id that is a signed int is taken, even a negative or a zero one`() {
        val apps =
            parse(
                list(
                    app(id = "-5"),
                    app(id = "0"),
                    app(id = "+7"),
                    app(id = "2147483647"),
                    app(id = "-2147483648"),
                )
            )

        assertThat(apps.map { it.appId })
            .containsExactly(-5, 0, 7, Int.MAX_VALUE, Int.MIN_VALUE)
            .inOrder()
    }

    @Test
    fun `an app with an odd id is dropped and the host is warned about it`() {
        LimeLogCapture().start().use { log ->
            for (id in listOf("abc", "2147483648", " 7", "7 ", "1.5", "0x10", "", "4294967295")) {
                val apps = parse(list(app(title = "Odd", id = id), app(title = "Fine", id = "3")))

                assertWithMessage("id '$id'")
                    .that(apps.map { it.summary() })
                    .containsExactly("Fine/3/false")
            }
            assertThat(log.entries).contains(Level.WARNING to "Malformed app ID: abc")
            assertThat(log.entries).contains(Level.WARNING to "Malformed app ID: 2147483648")
            // The app never got an id, so it is the one that the list reports as incomplete.
            assertThat(log.entries).contains(Level.WARNING to "GFE returned incomplete app: 0 Odd")
        }
    }

    @Test
    fun `an app without an id tag is dropped`() {
        LimeLogCapture().start().use { log ->
            val apps =
                parse(list(app(title = "Nameless id", id = null), app(title = "Ok", id = "4")))

            assertThat(apps.map { it.summary() }).containsExactly("Ok/4/false")
            assertThat(log.entries)
                .contains(Level.WARNING to "GFE returned incomplete app: 0 Nameless id")
        }
    }

    @Test
    fun `an app with two ids takes the last one`() {
        val apps = parse(list("<App><AppTitle>Twice</AppTitle><ID>1</ID><ID>2</ID></App>"))

        assertThat(apps.map { it.appId }).containsExactly(2)
    }

    @Test
    fun `text of tags that the client does not know is skipped`() {
        val apps =
            parse(
                list(
                    "<App><Unknown>text</Unknown><AppTitle>Known</AppTitle><ID>11</ID>" +
                        "<UUID>abc</UUID></App>"
                )
            )

        assertThat(apps.map { it.summary() }).containsExactly("Known/11/false")
    }

    @Test
    fun `an empty list is an empty list`() {
        assertThat(parse("""<root status_code="200"></root>""")).isEmpty()
        assertThat(parse("""<root status_code="200"/>""")).isEmpty()
        assertThat(parse(list())).isEmpty()
    }

    @Test
    fun `a long list keeps its order`() {
        val apps = parse(list(*Array(200) { app(title = "App $it", id = (it + 1).toString()) }))

        assertThat(apps.map { it.appId }).containsExactlyElementsIn((1..200).toList()).inOrder()
    }

    @Test
    fun `a list the host refuses raises the error of the host`() {
        val error =
            assertThrows(HostHttpResponseException::class.java) {
                parse("""<root status_code="401" status_message="denied"/>""")
            }

        assertThat(error.errorCode).isEqualTo(401)
        assertThat(error.errorMessage).isEqualTo("denied")
    }

    @Test
    fun `the status of the root is checked before any app is read`() {
        assertThat(
                assertThrows(HostHttpResponseException::class.java) {
                        parse("""<root status_code="500">${app("Late", "1")}</root>""")
                    }
                    .errorCode
            )
            .isEqualTo(500)
    }

    @Test
    fun `text outside an app, before the first app, is a NoSuchElementException`() {
        // The code reads the last app of the list for every text it sees, and there is none yet.
        assertThrows(NoSuchElementException::class.java) {
            parse("""<root status_code="200"><note>hello</note></root>""")
        }
        // Even white space between the root and the first app counts as text.
        assertThrows(NoSuchElementException::class.java) {
            parse("<root status_code=\"200\">\n  ${app("Pretty", "1")}\n</root>")
        }
    }

    @Test
    fun `text between two apps belongs to the last app and changes nothing`() {
        val apps = parse(list(app("One", "1"), "<note>between</note>", app("Two", "2")))

        assertThat(apps.map { it.summary() })
            .containsExactly("One/1/false", "Two/2/false")
            .inOrder()
    }

    @Test
    fun `a list that is cut off has no end of the root tag`() {
        // The parser does not complain about the end of the input: the list does.
        for (xml in
            listOf(
                """<root status_code="200"><App><ID>1</ID>""",
                """<root status_code="200"><App><ID>1""",
                """<root status_code="200">""",
            )) {
            assertWithMessage(xml)
                .that(assertThrows(XmlPullParserException::class.java) { parse(xml) }.message)
                .isEqualTo("Malformed XML: Root tag was not terminated")
        }
    }

    @Test
    fun `a document that is not XML is a parser exception`() {
        for ((xml, message) in
            listOf(
                "not xml" to "Unexpected token",
                "<a></b>" to "expected: /a read: b",
                """<root status_code="200"/>trailing""" to "Unexpected token",
                """<root status_code="200"><x>&bogus;</x></root>""" to "unresolved: &bogus;",
            )) {
            assertWithMessage(xml)
                .that(assertThrows(XmlPullParserException::class.java) { parse(xml) }.message)
                .startsWith(message)
        }
    }

    @Test
    fun `text that comes before the first app ends the list, also when it is a web page`() {
        assertThrows(NoSuchElementException::class.java) {
            parse("<html><body>error</body></html>")
        }
        assertThrows(NoSuchElementException::class.java) {
            parse("""<root status_code="200"><hostname>x""")
        }
    }

    @Test
    fun `an answer without a root tag is a malformed list`() {
        for (xml in listOf("", "<?xml version=\"1.0\"?>")) {
            assertWithMessage("'$xml'")
                .that(assertThrows(XmlPullParserException::class.java) { parse(xml) }.message)
                .isEqualTo("Malformed XML: Root tag was not terminated")
        }
    }

    @Test
    fun `a root without a status is a NumberFormatException`() {
        assertThrows(NumberFormatException::class.java) { parse("<root></root>") }
    }
}
