package io.github.magalz.borealis.designsystem.adaptive

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Test

/**
 * The columns of the grids of Hosts and Apps in each Card density (ADR 0058, 7), and how the Text
 * spacing takes the extra column away at Wider.
 */
class GridColumnsTest {
    @Test
    fun `Comfortable has the columns of the layout before the setting`() {
        assertThat(columns(CardDensity.Comfortable)).containsExactly(3, 2, 1, 2, 5, 4, 3, 4)
    }

    @Test
    fun `Compact adds one column to every device, a wide phone included`() {
        assertThat(columns(CardDensity.Compact)).containsExactly(4, 3, 2, 3, 6, 5, 4, 5)
    }

    @Test
    fun `Dense has the columns of Compact`() {
        assertThat(columns(CardDensity.Dense)).isEqualTo(columns(CardDensity.Compact))
    }

    @Test
    fun `Wide has the columns of Normal in every density`() {
        for (density in CardDensity.entries) {
            assertThat(columns(density, TextSpacing.Wide)).isEqualTo(columns(density))
        }
    }

    @Test
    fun `Wider keeps the columns of Comfortable in every density`() {
        val comfortable = columns(CardDensity.Comfortable)
        for (density in CardDensity.entries) {
            assertThat(columns(density, TextSpacing.Wider)).isEqualTo(comfortable)
        }
    }

    @Test
    fun `the wide phone starts at 600dp in every density and spacing`() {
        for (density in CardDensity.entries) {
            for (spacing in TextSpacing.entries) {
                val narrow = hostGridColumns(FormFactor.Phone, widthDp = 599, density, spacing)
                val wide = hostGridColumns(FormFactor.Phone, widthDp = 600, density, spacing)
                assertThat(wide).isEqualTo(narrow + 1)
                val narrowApps = appGridColumns(FormFactor.Phone, widthDp = 599, density, spacing)
                val wideApps = appGridColumns(FormFactor.Phone, widthDp = 600, density, spacing)
                assertThat(wideApps).isEqualTo(narrowApps + 1)
            }
        }
    }

    /** The host columns of a TV, a handheld, a phone and a wide phone, then the app columns. */
    private fun columns(
        density: CardDensity,
        spacing: TextSpacing = TextSpacing.Normal,
    ): List<Int> =
        listOf(
            hostGridColumns(FormFactor.Tv, 960, density, spacing),
            hostGridColumns(FormFactor.Handheld, 640, density, spacing),
            hostGridColumns(FormFactor.Phone, 360, density, spacing),
            hostGridColumns(FormFactor.Phone, 600, density, spacing),
            appGridColumns(FormFactor.Tv, 960, density, spacing),
            appGridColumns(FormFactor.Handheld, 640, density, spacing),
            appGridColumns(FormFactor.Phone, 360, density, spacing),
            appGridColumns(FormFactor.Phone, 600, density, spacing),
        )
}
