package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHostOver
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The text that stands on the animated background, outside any glass, has a soft dark halo and its
 * secondary ink is ink-2 (issue #33). Where there is no background, which is the plain page, there
 * is no halo and the ink is the token, as it was.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BackdropTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()

    // Where the halo is drawn

    @Test
    fun `the halo is drawn over a background of every style that draws one`() {
        show(BackgroundStyle.AuroraCurtains) { BackdropText("Hosts") }

        for (next in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
            change(style = next)

            assertWithMessage("over $next").that(haloOf("Hosts")).isTrue()
        }
    }

    @Test
    fun `the halo is drawn where the glass is Off or High contrast, for the background is still behind`() {
        show(BackgroundStyle.AuroraCurtains, BorealisLook(transparency = Transparency.Off)) {
            BackdropText("Hosts")
        }
        assertThat(haloOf("Hosts")).isTrue()

        change(look = BorealisLook(highContrast = true))
        assertThat(haloOf("Hosts")).isTrue()
    }

    @Test
    fun `no halo is drawn on the plain page`() {
        show(BackgroundStyle.Off) { BackdropText("Hosts") }

        assertThat(haloOf("Hosts")).isFalse()
    }

    @Test
    fun `no halo is drawn outside a host, which has no background`() {
        composeRule.setContent { BorealisTheme(FormFactor.Tv) { BackdropText("Hosts") } }

        assertThat(haloOf("Hosts")).isFalse()
    }

    // The ink

    @Test
    fun `the primary inks are the tokens whatever is behind the text`() {
        show(BackgroundStyle.Starfield) {
            BackdropText("Ink")
            BackdropText("Ink two", ink = BackdropInk.Ink2)
        }

        assertThat(composeRule.textColor("Ink")).isEqualTo(colors.ink)
        assertThat(composeRule.textColor("Ink two")).isEqualTo(colors.ink2)
    }

    @Test
    fun `ink-3 and ink-4 are ink-2 over a background`() {
        show(BackgroundStyle.Starfield) {
            BackdropText("Three", ink = BackdropInk.Ink3)
            BackdropText("Four", ink = BackdropInk.Ink4)
        }

        assertThat(composeRule.textColor("Three")).isEqualTo(colors.ink2)
        assertThat(composeRule.textColor("Four")).isEqualTo(colors.ink2)
    }

    @Test
    fun `ink-3 and ink-4 are the tokens on the plain page`() {
        show(BackgroundStyle.Off) {
            BackdropText("Three", ink = BackdropInk.Ink3)
            BackdropText("Four", ink = BackdropInk.Ink4)
        }

        assertThat(composeRule.textColor("Three")).isEqualTo(colors.ink3)
        assertThat(composeRule.textColor("Four")).isEqualTo(colors.ink4)
    }

    @Test
    fun `under high contrast the inks are those of high contrast, and ink-3 and ink-4 are its ink-2`() {
        val look = BorealisLook(highContrast = true)
        val high = borealisColors(look)
        show(BackgroundStyle.AuroraCurtains, look) {
            BackdropText("Title")
            BackdropText("Three", ink = BackdropInk.Ink3)
            BackdropKicker("Four")
        }

        assertThat(composeRule.textColor("Title")).isEqualTo(high.ink)
        assertThat(composeRule.textColor("Three")).isEqualTo(high.ink2)
        assertThat(composeRule.textColor("FOUR")).isEqualTo(high.ink2)
        // The inks of high contrast are not the tokens, and the halo is drawn there too.
        assertThat(high.ink2).isNotEqualTo(colors.ink2)
        assertThat(haloOf("Three")).isTrue()
    }

    @Test
    fun `a kicker is capitals in ink-2 over a background and ink-4 on the plain page`() {
        show(BackgroundStyle.NorthernLights) { BackdropKicker("Hosts") }
        assertThat(composeRule.textColor("HOSTS")).isEqualTo(colors.ink2)
        assertThat(haloOf("HOSTS")).isTrue()

        change(style = BackgroundStyle.Off)
        assertThat(composeRule.textColor("HOSTS")).isEqualTo(colors.ink4)
        assertThat(haloOf("HOSTS")).isFalse()
    }

    @Test
    fun `an empty state is drawn as backdrop text, its body in ink-2`() {
        show(BackgroundStyle.NorthernLights) { EmptyState("Nothing here", "Add a host") }

        assertThat(haloOf("Nothing here")).isTrue()
        assertThat(haloOf("Add a host")).isTrue()
        assertThat(composeRule.textColor("Add a host")).isEqualTo(colors.ink2)
    }

    @Test
    fun `a modifier of the caller that clears the semantics leaves the halo in them`() {
        show(BackgroundStyle.Starfield) { BackdropText("Hosts", Modifier.clearAndSetSemantics {}) }

        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    @Test
    fun `the name under a cover tile is on the background`() {
        show(BackgroundStyle.NorthernLights) { CoverTile("Steam Big Picture", onClick = {}) }

        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    @Test
    fun `the name under a cover tile has no halo on the plain page`() {
        show(BackgroundStyle.Off) { CoverTile("Steam Big Picture", onClick = {}) }

        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(BackdropTextHalo, false),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    // The drawing

    @Test
    fun `the halo darkens the bright background round the text and the text is drawn over it`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(24.dp)) {
                BackdropText("Hosts", style = MaterialTheme.typography.titleLarge)
            }
        }

        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()

        assertThat(pixels.count { it.luminance() < DARK }).isGreaterThan(MIN_HALO_PIXELS)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_TEXT_PIXELS)
    }

    @Test
    fun `no halo is drawn on the plain page, so nothing round the text is dark`() {
        show(BackgroundStyle.Off, host = HOST) {
            Box(Modifier.padding(24.dp)) {
                BackdropText("Hosts", style = MaterialTheme.typography.titleLarge)
            }
        }

        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()

        assertThat(pixels.count { it.luminance() < DARK }).isEqualTo(0)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_TEXT_PIXELS)
    }

    @Test
    fun `the halo round a stem has the alpha that the notes choose, under the text`() {
        val choice = NotesChoice.read()
        // The stem of a capital I on black, with no halo: where its edges are, to a fraction of
        // a pixel.
        showStem(BackgroundStyle.Off, Color.Black)
        val stem = Stem.of(composeRule.frame(HOST))
        // The same stem over white, with the halo: the alpha of the halo at a few distances
        // from it.
        change(BackgroundStyle.Starfield, backdrop = Color.White)
        val pixels = composeRule.frame(HOST)

        for (distance in DISTANCES_PX) {
            val at = stem.left - distance
            val seen = alphaOver(pixels[at.toInt(), stem.row].green)
            assertWithMessage("alpha at $distance px from the stem")
                .that(seen)
                .isWithin(ALPHA_TOLERANCE)
                .of(expectedAlpha(choice, stem.left - (at.toInt() + 0.5f), stem.width))
        }
        // The halo is behind the text: the middle of the stem is the ink, and far from it is white.
        assertThat(pixels[(stem.left + stem.width / 2).toInt(), stem.row].near(colors.ink)).isTrue()
        assertThat(pixels[(stem.left - FAR_PX).toInt(), stem.row].near(Color.White)).isTrue()
    }

    // Helpers

    private var style by mutableStateOf(BackgroundStyle.Off)
    private var look by mutableStateOf(BorealisLook())
    private var backdrop by mutableStateOf(Color.White)

    /** [content] in a host of [style], over a bright backdrop, under [look]. */
    private fun show(
        style: BackgroundStyle,
        look: BorealisLook = BorealisLook(),
        host: String? = null,
        backdrop: Color = Color.White,
        content: @Composable () -> Unit,
    ) {
        this.style = style
        this.look = look
        this.backdrop = backdrop
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, this.look) {
                GlassHostOver(
                    this.style,
                    background = { Box(Modifier.fillMaxSize().background(this.backdrop)) },
                    modifier =
                        if (host != null) Modifier.size(HOST_WIDTH.dp, HOST_HEIGHT.dp).testTag(host)
                        else Modifier,
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Changes the host that is shown, as the viewer does in Settings. */
    private fun change(
        style: BackgroundStyle = this.style,
        look: BorealisLook = this.look,
        backdrop: Color = this.backdrop,
    ) {
        composeRule.runOnIdle {
            this.style = style
            this.look = look
            this.backdrop = backdrop
        }
        composeRule.waitForIdle()
    }

    /** A capital I over [backdrop], in a host of [style]. */
    private fun showStem(style: BackgroundStyle, backdrop: Color) {
        show(style, host = HOST, backdrop = backdrop) {
            Box(Modifier.padding(STEM_PADDING.dp)) {
                BackdropText("I", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    /** The alpha of `page` that makes [green] of white. */
    private fun alphaOver(green: Float) = (1f - green) / (1f - colors.page.green)

    /**
     * The alpha that a halo of [choice] has at [distance] px outside the left edge of a stem
     * [width] px wide: the bar of the stem widened by the stroke on both sides, blurred by the
     * Gaussian of the shadow, at the opacity. It is worked out along a line across the stem, far
     * from its ends.
     */
    private fun expectedAlpha(choice: NotesChoice, distance: Float, width: Float): Float {
        val stroke = choice.strokeDp * PIXELS_PER_DP
        val sigma = BLUR_SIGMA_PER_PX * choice.blurDp * PIXELS_PER_DP + BLUR_SIGMA_BASE
        var covered = 0.0
        var step = -(stroke + 6 * sigma)
        while (step <= width + stroke + 6 * sigma) {
            if (step >= -stroke && step <= width + stroke) {
                val offset = (step - (-distance)) / sigma
                covered +=
                    kotlin.math.exp(-offset * offset / 2) /
                        (sigma * kotlin.math.sqrt(2 * Math.PI)) * STEP
            }
            step += STEP
        }
        return (covered * choice.opacity / 100).toFloat()
    }

    /** Where the stem of the I is in a picture of it over black: its left edge, width and row. */
    private class Stem(val left: Float, val width: Float, val row: Int) {
        companion object {
            fun of(pixels: PixelMap): Stem {
                val ink = BorealisColors().ink.green
                val coverage = { x: Int, y: Int -> (pixels[x, y].green / ink).coerceIn(0f, 1f) }
                val rows =
                    (0 until pixels.height).filter { y ->
                        (0 until pixels.width).any { x -> coverage(x, y) > 0.5f }
                    }
                val row = (rows.first() + rows.last()) / 2
                val covered = (0 until pixels.width).filter { coverage(it, row) > 0f }
                val first = covered.first()
                return Stem(
                    first + (1f - coverage(first, row)),
                    covered.sumOf { coverage(it, row).toDouble() }.toFloat(),
                    row,
                )
            }
        }
    }

    private fun haloOf(text: String): Boolean {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true)
        node.assert(SemanticsMatcher.keyIsDefined(BackdropTextHalo))
        return node.fetchSemanticsNode().config[BackdropTextHalo]
    }

    private fun PixelMap.count(test: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (test(this[x, y])) found++
        return found
    }

    private fun Color.near(other: Color) =
        kotlin.math.abs(red - other.red) +
            kotlin.math.abs(green - other.green) +
            kotlin.math.abs(blue - other.blue) < NEAR

    private companion object {
        const val HOST = "backdrop-host"
        const val HOST_WIDTH = 220
        const val HOST_HEIGHT = 90

        /** A pixel of the halo over white is `page` at 80 percent over it: well under this. */
        const val DARK = 0.5f
        const val MIN_HALO_PIXELS = 400
        const val MIN_TEXT_PIXELS = 30
        const val NEAR = 0.03f

        const val STEM_PADDING = 40
        const val PIXELS_PER_DP = 2f

        /** Skia turns the radius of a blur in pixels into a sigma: 0.57735 * radius + 0.5. */
        const val BLUR_SIGMA_PER_PX = 0.57735
        const val BLUR_SIGMA_BASE = 0.5
        const val STEP = 0.05

        /** How far outside the stem the halo is read, in pixels, and how far is far. */
        val DISTANCES_PX = listOf(2f, 5f, 8f, 12f)
        const val FAR_PX = 40f
        const val ALPHA_TOLERANCE = 0.07f
    }
}
