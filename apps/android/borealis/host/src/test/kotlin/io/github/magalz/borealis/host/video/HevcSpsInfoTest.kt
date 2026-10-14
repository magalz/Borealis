package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HevcSpsInfoTest {
    private fun info(
        profileIdc: Int = 1,
        chroma: Int = 1,
        separate: Boolean = false,
        luma: Int = 8,
        chromaDepth: Int = 8,
    ) = HevcSpsInfo(profileIdc, 0, chroma, separate, luma, chromaDepth)

    @Test
    fun `profiles have their specification names`() {
        val names = listOf(1, 2, 3, 4, 5, 9, 42).map { info(profileIdc = it).profileName }

        assertThat(names)
            .containsExactly(
                "Main",
                "Main10",
                "MainStillPicture",
                "RExt",
                "HighThroughput",
                "SCC",
                "idc42",
            )
            .inOrder()
    }

    @Test
    fun `chroma formats have their usual names`() {
        val names = (0..3).map { info(chroma = it).chromaName }

        assertThat(names).containsExactly("4:0:0", "4:2:0", "4:2:2", "4:4:4").inOrder()
        assertThat(info(chroma = 3, separate = true).chromaName).isEqualTo("4:4:4-separate-planes")
    }

    @Test
    fun `more than 8 bits in either plane needs Main10`() {
        assertThat(info(luma = 8, chromaDepth = 8).needsMain10).isFalse()
        assertThat(info(luma = 10, chromaDepth = 8).needsMain10).isTrue()
        assertThat(info(luma = 8, chromaDepth = 10).needsMain10).isTrue()
    }

    @Test
    fun `the description lists profile chroma and depth`() {
        assertThat(info(profileIdc = 2, luma = 10, chromaDepth = 10).describe())
            .isEqualTo("profile=Main10 chroma=4:2:0 depth=10/10")
    }
}
