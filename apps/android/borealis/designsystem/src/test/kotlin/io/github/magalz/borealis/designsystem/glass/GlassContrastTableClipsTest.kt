package io.github.magalz.borealis.designsystem.glass

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Test

/**
 * [GlassContrastTable] is measured over frames of the four background clips, and its generator
 * (`glass-table` of design/accessibility/contrast.py) writes the SHA-256 of each clip into it. A
 * clip that is encoded again leaves the table stale, and this test says so: measure the frames
 * again and generate the table (design/accessibility/README.md). The two styles that are drawn in
 * code have no clip; design/backgrounds/README.md says what a change to them needs.
 */
class GlassContrastTableClipsTest {
    /** The clips are in the resources of the module, which is where a unit test runs. */
    private val clips = File("src/main/res/raw")

    private fun sha256(file: File): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))

    @Test
    fun `the table holds the hash of every clip of the app`() {
        val found =
            clips
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith("background_") && it.extension == "mp4" }
                .associate { it.nameWithoutExtension to sha256(it) }
        assertWithMessage("no clips in ${clips.absolutePath}").that(found).isNotEmpty()

        assertThat(GlassContrastTable.clipSha256).containsExactlyEntriesIn(found)
    }
}
