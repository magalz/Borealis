package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BorealisClangFormatTest {
    @Test
    fun `reads the version from the version banner`() {
        assertThat(ClangFormat.parseVersion("clang-format version 22.1.8\n")).isEqualTo("22.1.8")
    }

    @Test
    fun `reads the version when the distribution prefixes the banner`() {
        val banner = "Ubuntu clang-format version 22.1.8-1 (https://example.invalid)"

        assertThat(ClangFormat.parseVersion(banner)).isEqualTo("22.1.8")
    }

    @Test
    fun `no output means clang-format is absent`() {
        assertThat(ClangFormat.parseVersion(null)).isNull()
        assertThat(ClangFormat.parseVersion("")).isNull()
        assertThat(ClangFormat.parseVersion("something else entirely")).isNull()
    }

    @Test
    fun `the missing tool message names the tool and the building guide`() {
        assertThat(ClangFormat.MISSING_MESSAGE)
            .isEqualTo("clang-format 22.x not found on PATH (see docs/development/building.md)")
    }
}
