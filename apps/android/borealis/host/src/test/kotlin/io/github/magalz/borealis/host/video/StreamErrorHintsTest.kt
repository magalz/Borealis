package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class StreamErrorHintsTest {
    private val hint = "HINT"
    private val asked = mutableListOf<Int>()
    private val lookup: (Int) -> String = { id ->
        asked += id
        if (id == R.string.stream_luminalshine_hint) hint else error("unexpected string $id")
    }

    @Test
    fun `the no-video-traffic error keeps the upstream text and gets the hint`() {
        val result = StreamErrorHints.withHint(-100, "No video received from host.", lookup)

        assertThat(result).isEqualTo("No video received from host.\n\n$hint")
    }

    @Test
    fun `the no-video-frame error keeps the upstream text and gets the hint`() {
        val result =
            StreamErrorHints.withHint(
                -101,
                "Your network connection isn't performing well.",
                lookup,
            )

        assertThat(result).isEqualTo("Your network connection isn't performing well.\n\n$hint")
    }

    @Test
    fun `other errors are left alone`() {
        for (code in listOf(0, -102, -1, -99, 100, 5030)) {
            assertThat(StreamErrorHints.withHint(code, "message", lookup)).isEqualTo("message")
        }
        assertThat(asked).isEmpty()
    }

    @Test
    fun `the error codes match the native constants`() {
        assertThat(StreamErrorHints.ML_ERROR_NO_VIDEO_TRAFFIC).isEqualTo(-100)
        assertThat(StreamErrorHints.ML_ERROR_NO_VIDEO_FRAME).isEqualTo(-101)
    }

    @Test
    fun `the stream strings are not blank and promise no duration`() {
        val english = strings("values")

        assertThat(english.keys)
            .containsExactly("stream_preparing_display", "stream_luminalshine_hint")
        for (text in english.values) assertThat(text).isNotEmpty()
        assertThat(english.getValue("stream_preparing_display").any { it.isDigit() }).isFalse()
    }

    private fun strings(directory: String): Map<String, String> {
        val file = File("src/main/res/$directory/strings_stream.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it) as Element
            node.getAttribute("name") to node.textContent.trim()
        }
    }
}
