package io.github.magalz.borealis.designsystem.components

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import org.junit.Test

/**
 * The halo of the text on the background is the one that the contrast tool chose and
 * `docs/ux/accessibility.md` records (issue #33), and the ink that goes with it.
 */
class BackdropHaloTest {
    private val colors = BorealisColors()

    @Test
    fun `the halo is the one that the notes choose`() {
        val choice = NotesChoice.read()

        assertThat(BackdropHalo.STROKE_DP).isEqualTo(choice.strokeDp)
        assertThat(BackdropHalo.BLUR_DP).isEqualTo(choice.blurDp)
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
}
