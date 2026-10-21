package io.github.magalz.borealis.designsystem.components

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import java.io.File
import org.junit.Test

/**
 * The halo of the text on the background is the one that the contrast tool chose and
 * `docs/ux/accessibility.md` records (issue #33), and the ink that goes with it.
 */
class BackdropHaloTest {
    private val colors = BorealisColors()

    @Test
    fun `the halo is the one that the notes choose`() {
        val choice = chosenHalo()

        assertThat(BackdropHalo.STROKE_DP).isEqualTo(choice.stroke)
        assertThat(BackdropHalo.BLUR_DP).isEqualTo(choice.blur)
        assertThat(BackdropHalo.OPACITY_PERCENT).isEqualTo(choice.opacity)
    }

    @Test
    fun `ink and ink-2 are the tokens with or without a halo`() {
        for (halo in listOf(true, false)) {
            assertThat(colors.backdropInk(BackdropInk.Ink, halo)).isEqualTo(colors.ink)
            assertThat(colors.backdropInk(BackdropInk.Ink2, halo)).isEqualTo(colors.ink2)
        }
    }

    @Test
    fun `ink-3 and ink-4 are ink-2 where there is a halo`() {
        assertThat(colors.backdropInk(BackdropInk.Ink3, halo = true)).isEqualTo(colors.ink2)
        assertThat(colors.backdropInk(BackdropInk.Ink4, halo = true)).isEqualTo(colors.ink2)
    }

    @Test
    fun `ink-3 and ink-4 are the tokens where there is no halo`() {
        assertThat(colors.backdropInk(BackdropInk.Ink3, halo = false)).isEqualTo(colors.ink3)
        assertThat(colors.backdropInk(BackdropInk.Ink4, halo = false)).isEqualTo(colors.ink4)
    }

    /** What the notes say the tool chose, as it prints it: "The choice: stroke 2 dp, ...". */
    private class Choice(val stroke: Int, val blur: Int, val opacity: Int)

    private fun chosenHalo(): Choice {
        // A unit test runs in the directory of its module, four levels below the repository root.
        val notes = File("../../../../docs/ux/accessibility.md")
        assertWithMessage("the notes are not at ${notes.absolutePath}").that(notes.isFile).isTrue()
        val found = CHOICE.findAll(notes.readText()).toList()
        assertWithMessage("the notes have to say the choice once").that(found).hasSize(1)
        val (stroke, blur, opacity) = found.single().destructured
        return Choice(stroke.toInt(), blur.toInt(), opacity.toInt())
    }

    private companion object {
        val CHOICE = Regex("""The choice: stroke (\d+) dp, blur (\d+) dp, opacity (\d+) %\.""")
    }
}
