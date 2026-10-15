package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import java.io.FileNotFoundException
import org.junit.Test

class LegalTextsTest {
    private fun assets(files: Map<String, String>): (String) -> java.io.InputStream = { name ->
        files[name]?.byteInputStream() ?: throw FileNotFoundException(name)
    }

    @Test
    fun `notice and licence are joined under headings`() {
        val text =
            LegalTexts.load(
                assets(mapOf("legal/NOTICE" to "Notice body", "legal/LICENSE" to "Licence body"))
            )

        assertThat(text).isNotNull()
        assertThat(text).contains("NOTICE")
        assertThat(text).contains("Notice body")
        assertThat(text).contains("LICENSE")
        assertThat(text).contains("Licence body")
        assertThat(text!!.indexOf("Notice body")).isLessThan(text.indexOf("Licence body"))
    }

    @Test
    fun `one missing file leaves the other`() {
        val text = LegalTexts.load(assets(mapOf("legal/LICENSE" to "Licence body")))

        assertThat(text).contains("Licence body")
        assertThat(text).doesNotContain("NOTICE")
    }

    @Test
    fun `no bundled file gives null`() {
        assertThat(LegalTexts.load(assets(emptyMap()))).isNull()
    }

    @Test
    fun `text is read as utf-8`() {
        val text = LegalTexts.load(assets(mapOf("legal/NOTICE" to "Atribuição – ©")))

        assertThat(text).contains("Atribuição – ©")
    }
}
