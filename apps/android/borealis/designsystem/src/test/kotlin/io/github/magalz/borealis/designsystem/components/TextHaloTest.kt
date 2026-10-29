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
 * behind its glyphs, at the opacity it says, and it draws nothing more and no less otherwise. The
 * stream sets it over the glass on its video, which can show any frame. The text is set over the
 * lightest backdrop that a glass of 40 percent can have, which is a white frame under it, and the
 * ring of pixels that the halo covers (the ones within 2 px of a glyph that no glyph covers, as
 * `contrast.py halo` takes it) is read in what is drawn.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TextHaloTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()

    /** The surface at 40 percent over a white frame: the lightest glass of the Standard level. */
    private val glass = colors.surface.copy(alpha = 0.4f).compositeOver(Color.White)

    @Composable
    private fun Sample(tag: String, color: Color, halo: Float?, background: Color = glass) {
        CompositionLocalProvider(LocalTextHalo provides halo) {
            Box(
                Modifier.testTag(tag)
                    .size(SAMPLE_WIDTH.dp, SAMPLE_HEIGHT.dp)
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Text("Quit Desktop", color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    private fun show(content: @Composable () -> Unit) = composeRule.setContent {
        BorealisTheme(FormFactor.Phone) { Column { content() } }
    }

    private val textColours =
        mapOf(
            "ink" to colors.ink,
            "ink-2" to colors.ink2,
            "accent-soft" to colors.accentSoft,
            "danger-ink" to colors.dangerInk,
        )

    // What it draws

    @Test
    fun `with the hook a text has a dark halo round its glyphs, where it has none without`() {
        show {
            Sample("plain", colors.ink, halo = null)
            Sample("halo", colors.ink, halo = HALO)
        }

        val plain = composeRule.frame("plain")
        val haloed = composeRule.frame("halo")

        assertThat(
                ring(plain, glass).count {
                    luminance(haloed[it.first, it.second]) < luminance(glass)
                }
            )
            .isGreaterThan(MIN_RING_PIXELS)
        assertThat(ring(plain, glass).count { plain[it.first, it.second] == glass })
            .isEqualTo(ring(plain, glass).size)
    }

    @Test
    fun `at the opacity of a backdrop text the halo is the one of a backdrop text`() {
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
                    Sample("hook", colors.ink, halo = BackdropHalo.OPACITY_PERCENT / PERCENT)
                }
            }
        }

        val backdrop = composeRule.frame("backdrop")
        val hook = composeRule.frame("hook")

        assertThat(differences(backdrop, hook)).isEqualTo(0)
    }

    @Test
    fun `a stronger opacity is a darker halo`() {
        show {
            Sample("weak", colors.ink, halo = 0.6f)
            Sample("strong", colors.ink, halo = 1f)
            Sample("plain", colors.ink, halo = null)
        }
        val ring = ring(composeRule.frame("plain"), glass)

        assertThat(lightest(composeRule.frame("strong"), ring))
            .isLessThan(lightest(composeRule.frame("weak"), ring))
    }

    @Test
    fun `a text under the hook says it has a halo, as a backdrop text does`() {
        show {
            Sample("plain", colors.ink, halo = null)
            Sample("halo", colors.ink, halo = HALO)
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

    @Test
    fun `over the lightest glass every text colour keeps 4_5 to 1 on the ring of its halo`() {
        show {
            for ((name, color) in textColours) {
                Sample("plain-$name", color, halo = null)
                Sample("halo-$name", color, halo = HALO)
            }
        }

        for ((name, color) in textColours) {
            val ring = ring(composeRule.frame("plain-$name"), glass)
            val haloed = composeRule.frame("halo-$name")

            val worst = ring.minOf { contrast(color, luminance(haloed[it.first, it.second])) }

            assertWithMessage("$name over its halo").that(worst).isAtLeast(AA_RATIO)
        }
    }

    @Test
    fun `without the halo the same text falls short, so the measure can fail`() {
        show {
            Sample("plain-ink", colors.ink, halo = null)
        }

        val plain = composeRule.frame("plain-ink")
        val ring = ring(plain, glass)

        assertThat(ring.minOf { contrast(colors.ink, luminance(plain[it.first, it.second])) })
            .isLessThan(AA_RATIO)
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
                .onNode(
                    SemanticsMatcher.keyIsDefined(BackdropIconHalo),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
        assertThat(node.config[BackdropIconHalo]).isTrue()
    }

    // Helpers

    /**
     * The pixels, as (x, y), within 2 px of one that [plain] draws differently from [background].
     */
    private fun ring(plain: PixelMap, background: Color): List<Pair<Int, Int>> {
        val glyph = { x: Int, y: Int ->
            x in 0 until plain.width && y in 0 until plain.height && !plain[x, y].near(background)
        }
        val found = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until plain.height) {
            for (x in 0 until plain.width) {
                if (glyph(x, y)) continue
                val near =
                    (-RING_PX..RING_PX).any { dy ->
                        (-RING_PX..RING_PX).any { dx ->
                            dx * dx + dy * dy <= RING_PX * RING_PX && glyph(x + dx, y + dy)
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
        const val SAMPLE_WIDTH = 160
        const val SAMPLE_HEIGHT = 40
        const val HALO = 1f
        const val PERCENT = 100f
        const val AA_RATIO = 4.5
        const val RING_PX = 2
        const val MIN_RING_PIXELS = 200
        const val NEAR = 0.004f
    }
}
