package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class LegalAssetsTaskTest {
    @Test
    fun `the font licence ships next to NOTICE and LICENSE`() {
        assertThat(LegalAssetsTask.FILES)
            .containsAtLeast("NOTICE", "LICENSE", "design/fonts/ibm-plex/OFL.txt")
    }

    @Test
    fun `every legal file exists in the repository and has a unique name`() {
        // Gradle runs the tests from the build-logic project directory.
        val repository = File("../../..")

        for (path in LegalAssetsTask.FILES) {
            assertThat(repository.resolve(path).isFile).isTrue()
        }
        val names = LegalAssetsTask.FILES.map { it.substringAfterLast('/') }
        assertThat(names).containsNoDuplicates()
    }
}
