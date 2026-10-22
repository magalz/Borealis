package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class LegalAssetsTaskTest {
    // Gradle runs the tests from the build-logic project directory.
    private val repository = File("../../..")

    private val fontLicences =
        listOf(
            "design/fonts/ibm-plex/OFL.txt",
            "design/fonts/atkinson-next/OFL.txt",
            "design/fonts/atkinson-mono/OFL.txt",
            "design/fonts/luciole/LICENSE.txt",
            "design/fonts/lexend/OFL.txt",
            "design/fonts/andika/OFL.txt",
            "design/fonts/b612/OFL.txt",
        )

    @Test
    fun `NOTICE and LICENSE ship`() {
        assertThat(LegalAssetsTask.FILES).containsAtLeast("NOTICE", "LICENSE")
    }

    @Test
    fun `the licence of every font ships`() {
        assertThat(LegalAssetsTask.FILES).containsAtLeastElementsIn(fontLicences)
    }

    @Test
    fun `every legal file exists in the repository`() {
        for (path in LegalAssetsTask.FILES) {
            assertThat(repository.resolve(path).isFile).isTrue()
        }
    }

    @Test
    fun `every legal file has a name of its own in the assets`() {
        val names = LegalAssetsTask.FILES.map { LegalAssetsTask.assetName(it) }

        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `a font licence takes the name of the folder of its font`() {
        assertThat(LegalAssetsTask.assetName("design/fonts/lexend/OFL.txt"))
            .isEqualTo("lexend-OFL.txt")
        assertThat(LegalAssetsTask.assetName("design/fonts/luciole/LICENSE.txt"))
            .isEqualTo("luciole-LICENSE.txt")
        assertThat(LegalAssetsTask.assetName("design/fonts/ibm-plex/OFL.txt"))
            .isEqualTo("ibm-plex-OFL.txt")
    }

    @Test
    fun `NOTICE and LICENSE keep their names`() {
        assertThat(LegalAssetsTask.assetName("NOTICE")).isEqualTo("NOTICE")
        assertThat(LegalAssetsTask.assetName("LICENSE")).isEqualTo("LICENSE")
    }
}
