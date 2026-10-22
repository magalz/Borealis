package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** NOTICE credits every bundled font (ADR 0058, decision 6). */
class FontCreditsTest {
    // Gradle runs the tests from the build-logic project directory.
    private val notice = File("../../../NOTICE").readText().replace("\r\n", "\n")

    /** The entry of the font [name] in the Fonts section: its line and the lines under it. */
    private fun entry(name: String): String {
        val start = notice.indexOf("  $name\n")
        assertThat(start).isAtLeast(0)
        val end = notice.indexOf("\n\n", start).let { if (it < 0) notice.length else it }
        return notice.substring(start, end)
    }

    @Test
    fun `the fonts have a section of NOTICE`() {
        assertThat(notice).contains("Fonts (design/fonts/)")
    }

    @Test
    fun `IBM Plex keeps its credit`() {
        assertThat(entry("IBM Plex Sans and IBM Plex Mono")).contains("Reserved Font Name \"Plex\"")
    }

    @Test
    fun `Luciole is credited as its licence asks, with CC BY 4 point 0`() {
        assertThat(entry("Luciole"))
            .contains(
                "Luciole © Laurent Bourcellier & Jonathan Perez, CC BY 4.0, " +
                    "https://www.luciole-vision.com"
            )
    }

    @Test
    fun `the Atkinson fonts are credited with OFL 1 point 1`() {
        for (name in listOf("Atkinson Hyperlegible Next", "Atkinson Hyperlegible Mono")) {
            assertThat(entry(name)).contains("SIL Open Font License 1.1")
        }
    }

    @Test
    fun `Lexend names its reserved font name`() {
        assertThat(entry("Lexend")).contains("\"RevReading Lexend\"")
    }

    @Test
    fun `Andika names its reserved font names`() {
        assertThat(entry("Andika")).contains("\"Andika\"")
        assertThat(entry("Andika")).contains("\"SIL\"")
    }

    @Test
    fun `B612 says that its name is a trademark of the Eclipse Foundation`() {
        assertThat(entry("B612 and B612 Mono")).contains("trademark of the Eclipse Foundation")
        assertThat(entry("B612 and B612 Mono")).contains("EPL 2.0")
    }
}
