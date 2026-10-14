package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BorealisVersionTest {
    @Test
    fun `exact tag gives the plain version`() {
        val version = BorealisVersion.fromGitDescribe("android-v1.2.3-0-gabcdef1")

        assertThat(version.name).isEqualTo("1.2.3")
        assertThat(version.code).isEqualTo(1_002_003)
    }

    @Test
    fun `commits after the tag are visible in the name but not in the code`() {
        val version = BorealisVersion.fromGitDescribe("android-v1.2.3-5-gabcdef1\n")

        assertThat(version.name).isEqualTo("1.2.3-5-gabcdef1")
        assertThat(version.code).isEqualTo(1_002_003)
    }

    @Test
    fun `code is major times one million plus minor times one thousand plus patch`() {
        val version = BorealisVersion.fromGitDescribe("android-v12.34.567-0-g1234567")

        assertThat(version.code).isEqualTo(12_034_567)
    }

    @Test
    fun `tag 0 0 0 still yields a version code of at least 1`() {
        val version = BorealisVersion.fromGitDescribe("android-v0.0.0-0-g1234567")

        assertThat(version.name).isEqualTo("0.0.0")
        assertThat(version.code).isEqualTo(1)
    }

    @Test
    fun `no describe output falls back to the dev version`() {
        assertThat(BorealisVersion.fromGitDescribe(null)).isEqualTo(BorealisVersion.DEV)
        assertThat(BorealisVersion.fromGitDescribe("  ")).isEqualTo(BorealisVersion.DEV)
        assertThat(BorealisVersion.DEV.name).isEqualTo("0.0.0-dev")
        assertThat(BorealisVersion.DEV.code).isEqualTo(1)
    }

    @Test
    fun `output that is not an android tag falls back to the dev version`() {
        assertThat(BorealisVersion.fromGitDescribe("v1.2.3-0-gabcdef1"))
            .isEqualTo(BorealisVersion.DEV)
        assertThat(BorealisVersion.fromGitDescribe("fatal: No names found"))
            .isEqualTo(BorealisVersion.DEV)
    }

    @Test
    fun `pre-release tags are not valid release versions and fall back to dev`() {
        // git is asked to skip these tags (--exclude "android-v*-*"); if one ever shows up it is
        // ignored.
        assertThat(BorealisVersion.fromGitDescribe("android-v1.2.3-rc1-0-gabc1234"))
            .isEqualTo(BorealisVersion.DEV)
    }

    @Test
    fun `components above 999 fail loudly instead of overflowing the version code`() {
        for (tag in listOf("android-v2200.0.0", "android-v1.1000.0", "android-v1.0.1000")) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    BorealisVersion.fromGitDescribe("$tag-0-gabc1234")
                }
            assertThat(error).hasMessageThat().contains(tag)
        }
    }

    @Test
    fun `the largest supported version still fits an int`() {
        val version = BorealisVersion.fromGitDescribe("android-v999.999.999-0-gabc1234")

        assertThat(version.code).isEqualTo(999_999_999)
    }
}
