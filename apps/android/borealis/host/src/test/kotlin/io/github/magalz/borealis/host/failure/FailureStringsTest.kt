package io.github.magalz.borealis.host.failure

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class FailureStringsTest {
    private val strings: Map<String, String> by lazy {
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/values/strings_failure.xml"))
                .getElementsByTagName("string")
        (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    @Test
    fun `an unreadable RTSP reply has its own line`() {
        assertThat(strings["failure_reply_unreadable"])
            .isEqualTo("The PC answered, but its reply could not be read.")
    }
}
