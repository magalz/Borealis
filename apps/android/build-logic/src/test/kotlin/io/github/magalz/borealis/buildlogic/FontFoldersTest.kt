package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import org.junit.Test

/**
 * The bundled fonts under `design/fonts/`: one folder for each, with the licence of its release and
 * a README that records the SHA-256 of every file, so a file that was changed shows.
 */
class FontFoldersTest {
    // Gradle runs the tests from the build-logic project directory.
    private val fonts = File("../../../design/fonts")

    private val folders =
        listOf("ibm-plex", "atkinson-next", "atkinson-mono", "luciole", "lexend", "andika", "b612")

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }

    private fun resourceFiles(folder: String): List<File> =
        fonts.resolve("$folder/res/font").listFiles().orEmpty().sortedBy { it.name }

    @Test
    fun `every font has a folder with its fonts, its licence and a README`() {
        for (folder in folders) {
            val dir = fonts.resolve(folder)
            assertWithMessage("$folder has font files").that(resourceFiles(folder)).isNotEmpty()
            assertWithMessage("$folder has a README").that(dir.resolve("README.md").isFile).isTrue()
            val licences =
                dir.listFiles()
                    .orEmpty()
                    .map { it.name }
                    .filter {
                        it == "OFL.txt" || it == "LICENSE.txt"
                    }
            assertWithMessage("$folder has its licence").that(licences).hasSize(1)
        }
    }

    @Test
    fun `resource names are lowercase, as Android asks`() {
        for (folder in folders) {
            for (file in resourceFiles(folder)) {
                assertThat(file.name).matches("[a-z0-9_]+\\.(ttf|otf)")
            }
        }
    }

    @Test
    fun `the README of a font records the SHA-256 of each of its files`() {
        for (folder in folders) {
            val readme = fonts.resolve("$folder/README.md").readText()
            for (file in resourceFiles(folder)) {
                assertWithMessage("README of $folder, ${file.name}")
                    .that(readme)
                    .contains(sha256(file))
            }
        }
    }

    @Test
    fun `resource names are unique over all the fonts`() {
        val names = folders.flatMap { folder -> resourceFiles(folder).map { it.name } }

        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `the fonts of the choice have the weights that the families use`() {
        fun names(folder: String) = resourceFiles(folder).map { it.nameWithoutExtension }

        assertThat(names("atkinson-next"))
            .containsExactly(
                "atkinson_next_regular",
                "atkinson_next_medium",
                "atkinson_next_semibold",
            )
        assertThat(names("atkinson-mono"))
            .containsExactly(
                "atkinson_mono_regular",
                "atkinson_mono_medium",
                "atkinson_mono_semibold",
            )
        assertThat(names("luciole")).containsExactly("luciole_regular", "luciole_bold")
        assertThat(names("lexend"))
            .containsExactly("lexend_regular", "lexend_medium", "lexend_semibold")
        // Andika ships Regular and SemiBold only: Compose takes the nearest weight for Medium.
        assertThat(names("andika")).containsExactly("andika_regular", "andika_semibold")
        assertThat(names("b612"))
            .containsExactly("b612_regular", "b612_bold", "b612_mono_regular", "b612_mono_bold")
    }
}
