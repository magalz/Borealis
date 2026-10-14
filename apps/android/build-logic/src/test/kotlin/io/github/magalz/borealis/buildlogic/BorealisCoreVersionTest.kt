package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BorealisCoreVersionTest {
    @get:Rule val temp = TemporaryFolder()

    private val cmakeLists =
        """
        cmake_minimum_required(VERSION 3.25)

        project(
          borealis_core
          VERSION 0.1.0
          DESCRIPTION "Platform-neutral rules"
          LANGUAGES CXX
        )
        set_target_properties(borealis_core PROPERTIES VERSION ${'$'}{PROJECT_VERSION})
        """
            .trimIndent()

    @Test
    fun `reads the project version, not the minimum CMake version`() {
        assertThat(BorealisCoreVersion.parse(cmakeLists)).isEqualTo("0.1.0")
    }

    @Test
    fun `reads a single line project call`() {
        val text =
            "cmake_minimum_required(VERSION 3.25)\nproject(core VERSION 12.34.567 LANGUAGES C)"

        assertThat(BorealisCoreVersion.parse(text)).isEqualTo("12.34.567")
    }

    @Test
    fun `a project without a version is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                BorealisCoreVersion.parse("project(core LANGUAGES C)")
            }

        assertThat(error).hasMessageThat().contains("core/CMakeLists.txt")
    }

    @Test
    fun `reads the file below the repository root`() {
        File(temp.root, "core").mkdirs()
        File(temp.root, "core/CMakeLists.txt").writeText(cmakeLists)

        assertThat(BorealisCoreVersion.read(temp.root)).isEqualTo("0.1.0")
    }
}
