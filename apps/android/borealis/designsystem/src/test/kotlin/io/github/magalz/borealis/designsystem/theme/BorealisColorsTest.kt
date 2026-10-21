package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Test

class BorealisColorsTest {
    private val tokens = BorealisTokens.Color

    @Test
    fun `every role is its token until it is changed`() {
        val colors = BorealisColors()

        assertThat(colors.page).isEqualTo(tokens.Page)
        assertThat(colors.surface).isEqualTo(tokens.Surface)
        assertThat(colors.ink).isEqualTo(tokens.Ink)
        assertThat(colors.ink3).isEqualTo(tokens.Ink3)
        assertThat(colors.ink4).isEqualTo(tokens.Ink4)
        assertThat(colors.accentSoft).isEqualTo(tokens.AccentSoft)
        assertThat(colors.success).isEqualTo(tokens.Success)
        assertThat(colors.offline).isEqualTo(tokens.Offline)
    }

    @Test
    fun `the offline colour keeps 3 to 1 on the page and the surfaces`() {
        // The ring of an offline host is an indicator that WCAG 1.4.11 asks 3:1 for.
        val colors = BorealisColors()

        for ((name, surface) in
            mapOf(
                "page" to colors.page,
                "chrome" to colors.chrome,
                "surface" to colors.surface,
                "surface-raised" to colors.surfaceRaised,
            )) {
            assertWithMessage("offline on $name")
                .that(contrastRatio(colors.offline, surface))
                .isAtLeast(3.0)
        }
    }

    @Test
    fun `the secondary ink is replaced and every other role is kept`() {
        // A palette that is not the tokens, as a colour-blind palette is for the status roles.
        val palette =
            BorealisColors(success = Color.Magenta, dangerInk = Color.Cyan, ink2 = Color.Yellow)

        val onGlass = palette.withSecondaryInk(ink3 = Color.Red, ink4 = Color.Green)

        assertThat(onGlass.ink3).isEqualTo(Color.Red)
        assertThat(onGlass.ink4).isEqualTo(Color.Green)
        assertThat(onGlass.success).isEqualTo(Color.Magenta)
        assertThat(onGlass.dangerInk).isEqualTo(Color.Cyan)
        assertThat(onGlass.ink2).isEqualTo(Color.Yellow)
        assertThat(onGlass).isEqualTo(palette.copy(ink3 = Color.Red, ink4 = Color.Green))
    }

    @Test
    fun `replacing the secondary ink leaves the tokens of the other roles as they are`() {
        val onGlass = BorealisColors().withSecondaryInk(Color.Red, Color.Green)

        assertThat(onGlass.copy(ink3 = tokens.Ink3, ink4 = tokens.Ink4)).isEqualTo(BorealisColors())
    }
}
