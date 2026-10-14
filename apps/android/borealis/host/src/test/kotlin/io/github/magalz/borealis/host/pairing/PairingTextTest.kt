package io.github.magalz.borealis.host.pairing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class PairingTextTest {
    /** Renders a string as `id:arg|arg` so a test sees which resource got which arguments. */
    private val fake: (Int, List<Any>) -> String = { id, args ->
        "$id:" + args.joinToString("|")
    }

    @Test
    fun `the PIN hint names the Web UI address and a device name without xbox or webos`() {
        val hint = PairingHints.pinHint("192.0.2.10", 47989, "Xbox Pixel 8", fake)

        assertThat(hint).isEqualTo("${R.string.pair_pin_hint}:https://192.0.2.10:47990|Pixel 8")
    }

    @Test
    fun `the PIN hint says where to look when the address cannot be turned into a URL`() {
        val hint = PairingHints.pinHint("192.0.2.10", 0, null, fake)

        assertThat(hint)
            .isEqualTo("${R.string.pair_pin_hint}:${R.string.webui_url_unknown}:|Borealis")
    }

    @Test
    fun `the unpair hint sends the user to the Web UI`() {
        val hint = PairingHints.unpairHint("2001:db8::10", 47989, fake)

        assertThat(hint).isEqualTo("${R.string.unpair_webui_hint}:https://[2001:db8::10]:47990")
    }

    @Test
    fun `the unpair hint says where to look when the address cannot be turned into a URL`() {
        val hint = PairingHints.unpairHint("", 47989, fake)

        assertThat(hint).isEqualTo("${R.string.unpair_webui_hint}:${R.string.webui_url_unknown}:")
    }

    @Test
    fun `English and Brazilian Portuguese define the same strings with the same placeholders`() {
        val english = strings("values")
        val portuguese = strings("values-pt-rBR")

        assertThat(english.keys)
            .containsExactly("pair_pin_hint", "unpair_webui_hint", "webui_url_unknown")
        assertThat(portuguese.keys).isEqualTo(english.keys)
        for ((name, text) in english) {
            assertThat(placeholders(portuguese.getValue(name))).isEqualTo(placeholders(text))
        }
    }

    @Test
    fun `the PIN hint explains the xbox and webos rule in both languages`() {
        for (folder in listOf("values", "values-pt-rBR")) {
            val text = strings(folder).getValue("pair_pin_hint").lowercase()

            assertThat(text).contains("xbox")
            assertThat(text).contains("webos")
        }
    }

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings_pairing.xml")
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%\d+\$[a-z]""").findAll(text).map { it.value }.sorted().toList()
}
