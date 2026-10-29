package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassScene
import io.github.magalz.borealis.designsystem.glass.GlassSpec
import io.github.magalz.borealis.designsystem.glass.LocalGlassScene
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.luminance
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [LocalTextHalo]: while a screen sets it, every [Text] under it draws the halo of a [BackdropText]
 * behind its glyphs, at the stroke and the opacity it says, and it draws nothing more and no less
 * otherwise. The stream sets it over the glass on its video, which can show any frame. The text is
 * set over the lightest backdrop that a glass of 40 percent can have, which is a white frame under
 * it, and the ring of pixels that the halo covers (the ones within 2 px of a glyph that no glyph
 * covers, as `contrast.py halo` takes it) is read in what is drawn, for the strings of the menu,
 * the dot of an i included, which is where a halo is weakest.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h2400dp-xhdpi")
class TextHaloTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()

    /** The surface at 40 percent over a white frame: the lightest glass of the Standard level. */
    private val glass = colors.surface.copy(alpha = 0.4f).compositeOver(Color.White)

    /** One text, set in [style] over [background], with the halo of [halo] where there is one. */
    @Composable
    private fun Sample(
        tag: String,
        text: String,
        color: Color,
        halo: TextHaloStyle?,
        style: TextStyle = MaterialTheme.typography.bodyMedium,
    ) {
        CompositionLocalProvider(LocalTextHalo provides halo) {
            Box(
                Modifier.testTag(tag).size(SAMPLE_WIDTH.dp, SAMPLE_HEIGHT.dp).background(glass),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = color, style = style)
            }
        }
    }

    private fun show(content: @Composable () -> Unit) = composeRule.setContent {
        BorealisTheme(FormFactor.Phone) { Column { content() } }
    }

    // What it draws

    @Test
    fun `with the hook a text has a dark halo round its glyphs, where it has none without`() {
        show {
            Sample("plain", "Quit Desktop", colors.ink, halo = null)
            Sample("halo", "Quit Desktop", colors.ink, halo = HALO)
        }

        val plain = composeRule.frame("plain")
        val haloed = composeRule.frame("halo")
        val ring = ring(plain, glass)

        assertThat(ring.count { luminance(haloed[it.first, it.second]) < luminance(glass) })
            .isGreaterThan(MIN_RING_PIXELS)
        assertThat(ring.count { plain[it.first, it.second] == glass }).isEqualTo(ring.size)
    }

    @Test
    fun `at the stroke and the opacity of a backdrop text the halo is the one of a backdrop text`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                Column {
                    CompositionLocalProvider(
                        LocalGlassScene provides
                            GlassScene(GlassSpec.Solid, BackgroundStyle.Starfield)
                    ) {
                        Box(
                            Modifier.testTag("backdrop")
                                .size(SAMPLE_WIDTH.dp, SAMPLE_HEIGHT.dp)
                                .background(glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            BackdropText(
                                "Quit Desktop",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Sample("hook", "Quit Desktop", colors.ink, BackdropHalo.OVER_BACKGROUND)
                }
            }
        }

        val backdrop = composeRule.frame("backdrop")
        val hook = composeRule.frame("hook")

        assertThat(differences(backdrop, hook)).isEqualTo(0)
    }

    @Test
    fun `a stronger opacity is a darker halo, and a wider stroke is a wider one`() {
        show {
            Sample("plain", "Quit Desktop", colors.ink, halo = null)
            Sample("weak", "Quit Desktop", colors.ink, halo = TextHaloStyle(0.6f, 3))
            Sample("strong", "Quit Desktop", colors.ink, halo = TextHaloStyle(1f, 3))
            Sample("wide", "Quit Desktop", colors.ink, halo = TextHaloStyle(1f, 5))
        }
        val plain = composeRule.frame("plain")
        val ring = ring(plain, glass)
        val far = ring(plain, glass, radius = FAR_RING_PX)

        assertThat(lightest(composeRule.frame("strong"), ring))
            .isLessThan(lightest(composeRule.frame("weak"), ring))
        assertThat(meanLuminance(composeRule.frame("wide"), far))
            .isLessThan(meanLuminance(composeRule.frame("strong"), far))
    }

    @Test
    fun `a text under the hook says it has a halo, as a backdrop text does`() {
        show {
            Sample("plain", "Quit Desktop", colors.ink, halo = null)
            Sample("halo", "Quit Desktop", colors.ink, halo = HALO)
        }

        val nodes =
            composeRule
                .onAllNodesWithText("Quit Desktop", useUnmergedTree = true)
                .fetchSemanticsNodes()

        assertThat(nodes.map { BackdropTextHalo in it.config && it.config[BackdropTextHalo] })
            .containsExactly(false, true)
            .inOrder()
    }

    // The measure

    /** A string of the menu, the text colour and the style it is drawn in. */
    private class Case(val text: String, val color: (BorealisColors) -> Color, val style: String)

    private val cases =
        listOf(
            // The titles of the rows and of the popup, in ink.
            Case("Back to stream", { it.ink }, "bodyLarge"),
            Case("Refresh the picture", { it.ink }, "bodyLarge"),
            Case("Disconnect", { it.ink }, "bodyLarge"),
            Case("Quit Desktop?", { it.ink }, "titleLarge"),
            Case("Desktop · Gaming PC", { it.ink }, "titleMedium"),
            Case("Cancel", { it.ink }, "bodyMedium"),
            // The consequences and the values, ink-2 (ink-3 is ink-2 on the glass).
            Case("Refresh the picture", { it.ink2 }, "bodySmall"),
            Case("Ends the stream.", { it.ink2 }, "bodyMedium"),
            Case("Session info", { it.ink2 }, "bodySmall"),
            // The red of the Quit row and of the Quit button.
            Case("Quit Desktop", { it.dangerInk }, "bodyLarge"),
            Case("Quit", { it.dangerInk }, "bodyMedium"),
            // The soft accent, which is the label of whatever is selected.
            Case("Disconnect", { it.accentSoft }, "bodyMedium"),
            Case("Quit Desktop", { it.accentSoft }, "bodySmall"),
        )

    @Composable
    private fun style(name: String): TextStyle =
        when (name) {
            "bodySmall" -> MaterialTheme.typography.bodySmall
            "bodyLarge" -> MaterialTheme.typography.bodyLarge
            "titleMedium" -> MaterialTheme.typography.titleMedium
            "titleLarge" -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.bodyMedium
        }

    @Test
    fun `over the lightest glass every text of the menu keeps 4_5 to 1 on the ring of its halo`() {
        show {
            cases.forEachIndexed { index, case ->
                val color = case.color(colors)
                Sample("plain-$index", case.text, color, halo = null, style = style(case.style))
                Sample("halo-$index", case.text, color, halo = HALO, style = style(case.style))
            }
        }

        cases.forEachIndexed { index, case ->
            val color = case.color(colors)
            val ring = ring(composeRule.frame("plain-$index"), glass)
            val haloed = composeRule.frame("halo-$index")

            val worst = ring.minOf { contrast(color, luminance(haloed[it.first, it.second])) }

            assertWithMessage("\"${case.text}\" in ${case.style} over its halo")
                .that(worst)
                .isAtLeast(AA_RATIO)
        }
    }

    @Test
    fun `without the halo the same text falls short, so the measure can fail`() {
        show { Sample("plain-ink", "Quit Desktop", colors.ink, halo = null) }

        val plain = composeRule.frame("plain-ink")
        val ring = ring(plain, glass)

        assertThat(ring.minOf { contrast(colors.ink, luminance(plain[it.first, it.second])) })
            .isLessThan(AA_RATIO)
    }

    @Test
    fun `a halo of one pass has a hole at the dot of an i, where the red of Quit falls short`() {
        show {
            Sample("plain", "Quit", colors.dangerInk, halo = null)
            Sample("one", "Quit", colors.dangerInk, halo = TextHaloStyle(1f, 3))
            Sample("filled", "Quit", colors.dangerInk, halo = HALO)
        }
        val ring = ring(composeRule.frame("plain"), glass)

        val one = composeRule.frame("one")
        val filled = composeRule.frame("filled")

        assertThat(ring.minOf { contrast(colors.dangerInk, luminance(one[it.first, it.second])) })
            .isLessThan(AA_RATIO)
        assertThat(
                ring.minOf { contrast(colors.dangerInk, luminance(filled[it.first, it.second])) }
            )
            .isAtLeast(AA_RATIO)
    }

    // The icons

    @Test
    fun `an icon under the hook has the halo of the text behind it`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                CompositionLocalProvider(LocalTextHalo provides HALO) {
                    BackdropIcon(BorealisIcons.ChevronRight, "Chevron")
                }
            }
        }

        val node =
            composeRule
                .onNode(SemanticsMatcher.keyIsDefined(BackdropIconHalo), useUnmergedTree = true)
                .fetchSemanticsNode()

        assertThat(node.config[BackdropIconHalo]).isTrue()
    }

    // Helpers

    /** The pixels, as (x, y), within [radius] px of one that [plain] draws unlike [background]. */
    private fun ring(
        plain: PixelMap,
        background: Color,
        radius: Int = RING_PX,
    ): List<Pair<Int, Int>> {
        val glyph = { x: Int, y: Int ->
            x in 0 until plain.width && y in 0 until plain.height && !plain[x, y].near(background)
        }
        val found = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until plain.height) {
            for (x in 0 until plain.width) {
                if (glyph(x, y)) continue
                val near =
                    (-radius..radius).any { dy ->
                        (-radius..radius).any { dx ->
                            dx * dx + dy * dy <= radius * radius && glyph(x + dx, y + dy)
                        }
                    }
                if (near) found += x to y
            }
        }
        return found
    }

    private fun lightest(pixels: PixelMap, ring: List<Pair<Int, Int>>) = ring.maxOf {
        luminance(pixels[it.first, it.second])
    }

    private fun meanLuminance(pixels: PixelMap, ring: List<Pair<Int, Int>>) =
        ring.sumOf { luminance(pixels[it.first, it.second]) } / ring.size

    private fun differences(first: PixelMap, second: PixelMap): Int {
        var count = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) if (!first[x, y].near(second[x, y])) count++
        }
        return count
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < NEAR

    private companion object {
        const val SAMPLE_WIDTH = 240
        const val SAMPLE_HEIGHT = 40

        /** The halo of the text on the glass of the stream: 3 dp at 100 percent, filled. */
        val HALO = TextHaloStyle(opacity = 1f, strokeDp = 3, filled = true)
        const val AA_RATIO = 4.5
        const val RING_PX = 2
        const val FAR_RING_PX = 9
        const val MIN_RING_PIXELS = 200
        const val NEAR = 0.004f
    }
}
