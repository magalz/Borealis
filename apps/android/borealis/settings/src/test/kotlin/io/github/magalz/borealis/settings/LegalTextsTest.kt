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

    /** The assets of the fonts and the family that each one is shown under. */
    private val fontLicences =
        listOf(
            "ibm-plex-OFL.txt" to "IBM Plex Sans and Mono",
            "atkinson-next-OFL.txt" to "Atkinson Hyperlegible Next",
            "atkinson-mono-OFL.txt" to "Atkinson Hyperlegible Mono",
            "luciole-LICENSE.txt" to "Luciole",
            "lexend-OFL.txt" to "Lexend",
            "andika-OFL.txt" to "Andika",
            "b612-OFL.txt" to "B612 and B612 Mono",
        )

    private fun everything(): Map<String, String> =
        mapOf("legal/NOTICE" to "Notice body", "legal/LICENSE" to "Licence body") +
            fontLicences.associate { (asset, _) -> "legal/$asset" to "Body of $asset" }

    @Test
    fun `the licence of every font is shown, each under the name of its font`() {
        val text = LegalTexts.load(assets(everything()))!!

        for ((asset, family) in fontLicences) {
            assertThat(text).contains("Body of $asset")
            assertThat(text.indexOf(family)).isAtLeast(0)
            assertThat(text.indexOf(family)).isLessThan(text.indexOf("Body of $asset"))
        }
    }

    @Test
    fun `the font licences come after NOTICE and LICENSE, in the order of the choice`() {
        val text = LegalTexts.load(assets(everything()))!!

        val positions =
            listOf("Notice body", "Licence body") + fontLicences.map { "Body of ${it.first}" }
        assertThat(positions.map { text.indexOf(it) }).isInStrictOrder()
        assertThat(text.indexOf("Notice body")).isAtLeast(0)
    }

    @Test
    fun `a font licence that the build does not bundle leaves the others`() {
        val text = LegalTexts.load(assets(everything() - "legal/lexend-OFL.txt"))!!

        assertThat(text).doesNotContain("Body of lexend-OFL.txt")
        assertThat(text).contains("Body of andika-OFL.txt")
        assertThat(text).contains("Licence body")
    }

    @Test
    fun `the font licences alone are shown when the build bundles no NOTICE`() {
        val text = LegalTexts.load(assets(mapOf("legal/luciole-LICENSE.txt" to "CC BY 4.0")))

        assertThat(text).contains("CC BY 4.0")
        assertThat(text).contains("Luciole")
    }

    @Test
    fun `text is read as utf-8`() {
        val text = LegalTexts.load(assets(mapOf("legal/NOTICE" to "Atribuição – ©")))

        assertThat(text).contains("Atribuição – ©")
    }
}
