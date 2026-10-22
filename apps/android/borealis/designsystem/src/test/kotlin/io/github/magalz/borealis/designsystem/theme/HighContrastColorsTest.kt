package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.glass.luminance
import org.junit.Test

/**
 * High contrast (ADR 0057, decision 11): text at 7:1 or more, opaque borders at 3:1 or more. The
 * ratios are WCAG 2.x, and every one is checked against each surface a text or a border lies on.
 */
class HighContrastColorsTest {
    private val base = BorealisColors()
    private val high = base.withHighContrast()

    private fun textRoles(colors: BorealisColors) =
        mapOf(
            "ink" to colors.ink,
            "ink-2" to colors.ink2,
            "ink-3" to colors.ink3,
            "ink-4" to colors.ink4,
            "accent" to colors.accent,
            "accent-soft" to colors.accentSoft,
            "success-ink" to colors.successInk,
            "warning-ink" to colors.warningInk,
            "danger-ink" to colors.dangerInk,
        )

    @Test
    fun `every text role reaches 7 to 1 on the page, the surfaces and the selected rows`() {
        for ((role, color) in textRoles(high)) {
            for ((backdrop, fill) in textBackdrops(high)) {
                assertWithMessage("$role on $backdrop")
                    .that(contrastRatio(color, fill))
                    .isAtLeast(7.0)
            }
        }
    }

    @Test
    fun `the text roles do not reach 7 to 1 without high contrast`() {
        // The test above can fail: ink-3 and ink-4 are below 7 to 1 on the page today.
        val short = textRoles(base).filter { contrastRatio(it.value, base.page) < 7.0 }

        assertThat(short.keys).containsAtLeast("ink-3", "ink-4")
    }

    @Test
    fun `the label on an accent fill reaches 7 to 1`() {
        assertThat(contrastRatio(high.onAccent, high.accent)).isAtLeast(7.0)
    }

    @Test
    fun `the ink keeps its four steps, each lighter than the one after it`() {
        val steps = listOf(high.ink, high.ink2, high.ink3, high.ink4).map(::luminance)

        assertThat(steps).isInOrder(Comparator<Double> { a, b -> b.compareTo(a) })
        assertThat(steps.distinct()).hasSize(4)
    }

    @Test
    fun `the borders are opaque and keep 3 to 1 on the page and the surfaces`() {
        val borders =
            mapOf(
                "line" to high.line,
                "line-strong" to high.lineStrong,
                "accent-line" to high.accentLine,
            )

        for ((role, color) in borders) {
            assertWithMessage("$role is opaque").that(color.alpha).isEqualTo(1f)
            for ((backdrop, fill) in textBackdrops(high)) {
                assertWithMessage("$role on $backdrop")
                    .that(contrastRatio(color, fill))
                    .isAtLeast(3.0)
            }
        }
    }

    @Test
    fun `the borders of today are translucent`() {
        // The test above can fail: a hairline of white at 7 percent is not a border to see.
        assertThat(base.line.alpha).isLessThan(1f)
        assertThat(base.lineStrong.alpha).isLessThan(1f)
        assertThat(base.accentLine.alpha).isLessThan(1f)
    }

    @Test
    fun `the offline indicator keeps 3 to 1 on the page and the surfaces`() {
        for ((backdrop, fill) in textBackdrops(high)) {
            assertWithMessage("offline on $backdrop")
                .that(contrastRatio(high.offline, fill))
                .isAtLeast(3.0)
        }
    }

    @Test
    fun `the page and the surfaces stay as they are`() {
        assertThat(high.page).isEqualTo(base.page)
        assertThat(high.chrome).isEqualTo(base.chrome)
        assertThat(high.surface).isEqualTo(base.surface)
        assertThat(high.surfaceRaised).isEqualTo(base.surfaceRaised)
        assertThat(high.scrim).isEqualTo(base.scrim)
    }

    @Test
    fun `high contrast composes with the roles that another variant changed`() {
        val variant = base.copy(success = Color.Magenta, ink3 = Color.Red)

        val composed = variant.withHighContrast()

        // The roles that high contrast does not own are kept; the ones it owns are its own.
        assertThat(composed.success).isEqualTo(Color.Magenta)
        assertThat(composed.ink3).isEqualTo(high.ink3)
    }
}
