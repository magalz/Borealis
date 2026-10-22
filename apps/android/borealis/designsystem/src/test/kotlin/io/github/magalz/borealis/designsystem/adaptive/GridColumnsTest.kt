package io.github.magalz.borealis.designsystem.adaptive

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Test

/**
 * The columns and the padding of the grids of Hosts and Apps in each Card density (ADR 0058, 7).
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
    fun `the wide phone starts at 600dp in every density`() {
        for (density in CardDensity.entries) {
            val narrow = hostGridColumns(FormFactor.Phone, widthDp = 599, density)
            val wide = hostGridColumns(FormFactor.Phone, widthDp = 600, density)
            assertThat(wide).isEqualTo(narrow + 1)
            val narrowApps = appGridColumns(FormFactor.Phone, widthDp = 599, density)
            val wideApps = appGridColumns(FormFactor.Phone, widthDp = 600, density)
            assertThat(wideApps).isEqualTo(narrowApps + 1)
        }
    }

    /** The host columns of a TV, a handheld, a phone and a wide phone, then the app columns. */
    private fun columns(density: CardDensity): List<Int> =
        listOf(
            hostGridColumns(FormFactor.Tv, 960, density),
            hostGridColumns(FormFactor.Handheld, 640, density),
            hostGridColumns(FormFactor.Phone, 360, density),
            hostGridColumns(FormFactor.Phone, 600, density),
            appGridColumns(FormFactor.Tv, 960, density),
            appGridColumns(FormFactor.Handheld, 640, density),
            appGridColumns(FormFactor.Phone, 360, density),
            appGridColumns(FormFactor.Phone, 600, density),
        )
}
