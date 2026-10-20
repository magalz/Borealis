package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The glass surface on a phone at xhdpi (720 x 1280 pixels): the tint it draws over the backdrop,
 * the blur where the device has one, the solid surface where the glass is off, and the text colours
 * that the content of a glass surface sees.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassSurfaceTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisTokens.Color.Surface
    private val round = RoundedCornerShape(16.dp)

    // The tint

    @Test
    fun `a panel draws the surface colour at the tint over the backdrop that shows through`() {
        show(background = { Box(Modifier.fillMaxSize().background(Color.White)) })

        // The curtains at 40 percent: 40 percent of the surface and 60 percent of the white.
        val pixel = panel().center()
        assertThat(pixel.red).isWithin(TOLERANCE).of(0.4f * surface.red + 0.6f)
        assertThat(pixel.green).isWithin(TOLERANCE).of(0.4f * surface.green + 0.6f)
        assertThat(pixel.blue).isWithin(TOLERANCE).of(0.4f * surface.blue + 0.6f)
    }

    @Test
    fun `a panel over a backdrop that needs more tint is raised to the floor`() {
        show(
            style = BackgroundStyle.NorthernLights,
            background = { Box(Modifier.fillMaxSize().background(Color.White)) },
        )

        // Northern lights at 8 dp is 70 percent: 70 percent of the surface and 30 percent of white.
        assertThat(panel().center().red).isWithin(TOLERANCE).of(0.7f * surface.red + 0.3f)
    }

    @Test
    fun `a panel clips its backdrop to its shape`() {
        show(
            background = { Box(Modifier.fillMaxSize().background(Color.White)) },
            shape = RoundedCornerShape(50),
        )

        val map = panel()
        // The corner of a pill is outside it, so it shows the backdrop and not the tint.
        assertThat(map[1, 1].red).isWithin(TOLERANCE).of(1f)
        assertThat(map.center().red).isWithin(TOLERANCE).of(0.4f * surface.red + 0.6f)
    }

    // The blur

    @Test
    fun `the backdrop behind a panel is blurred, with no step where its light meets its dark`() {
        show(background = { HalfAndHalf() }, panelAt = PANEL_ACROSS_THE_EDGE)

        val row = panel().row(y = PANEL_ROW)
        assertThat(row[EDGE_IN_PANEL - FAR].red).isGreaterThan(0.5f)
        assertThat(row[EDGE_IN_PANEL + FAR].red).isLessThan(0.2f)
        assertThat(row.maxStep()).isLessThan(SMOOTH)
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `on API 30 the panel draws the 85 percent tint and the backdrop is not blurred`() {
        show(background = { HalfAndHalf() }, panelAt = PANEL_ACROSS_THE_EDGE)

        val row = panel().row(y = PANEL_ROW)
        // 85 percent of the surface over white on one side and over black on the other: a step of
        // 15 percent of white between two pixels.
        assertThat(row[EDGE_IN_PANEL - FAR].red).isWithin(TOLERANCE).of(0.85f * surface.red + 0.15f)
        assertThat(row[EDGE_IN_PANEL + FAR].red).isWithin(TOLERANCE).of(0.85f * surface.red)
        assertThat(row.maxStep()).isGreaterThan(SHARP)
    }

    // The solid surface

    @Test
    fun `Off draws exactly the surface background, which is what a panel was before the glass`() {
        assertSolid(BorealisLook(transparency = Transparency.Off))
    }

    @Test
    fun `high contrast draws exactly the surface background`() {
        assertSolid(BorealisLook(highContrast = true))
    }

    @Test
    fun `the plain page draws exactly the surface background`() {
        assertSolid(BorealisLook(), style = BackgroundStyle.Off)
    }

    @Test
    fun `a panel outside any host draws exactly the surface background`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.size(100.dp).testTag(PANEL).glassSurface(round))
                Box(
                    Modifier.size(100.dp)
                        .offset(y = 120.dp)
                        .testTag(CONTROL)
                        .background(surface, round)
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(pixels(PANEL).buffer).isEqualTo(pixels(CONTROL).buffer)
    }

    private fun assertSolid(
        look: BorealisLook,
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalBorealisLook provides look) {
                GlassHostOver(
                    style,
                    background = { Box(Modifier.fillMaxSize().background(Color.White)) },
                ) {
                    Box(Modifier.size(100.dp).testTag(PANEL).glassSurface(round))
                    Box(
                        Modifier.size(100.dp)
                            .offset(y = 120.dp)
                            .testTag(CONTROL)
                            .background(surface, round)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertThat(pixels(PANEL).buffer).isEqualTo(pixels(CONTROL).buffer)
        assertThat(panel().center().red).isWithin(TOLERANCE).of(surface.red)
    }

    // The text colours

    @Test
    fun `content inside a glass surface sees the glass colours for ink-3 and ink-4`() {
        var seen: BorealisColors? = null
        showText(BackgroundStyle.FlowingLight, BorealisLook()) { seen = it }

        assertThat(seen?.ink3?.toArgbInt()).isEqualTo(0xFFA8ACB1.toInt())
        assertThat(seen?.ink?.toArgbInt()).isEqualTo(BorealisColors().ink.toArgbInt())
    }

    @Test
    fun `content sees the tokens as they are where the glass is off`() {
        var seen: BorealisColors? = null
        showText(BackgroundStyle.FlowingLight, BorealisLook(transparency = Transparency.Off)) {
            seen = it
        }

        assertThat(seen?.ink3).isEqualTo(BorealisColors().ink3)
        assertThat(seen?.ink4).isEqualTo(BorealisColors().ink4)
    }

    @Test
    fun `content outside any host sees the tokens as they are`() {
        var seen: BorealisColors? = null
        composeRule.setContent { GlassTextColors { seen = LocalBorealisColors.current } }
        composeRule.waitForIdle()

        assertThat(seen?.ink3).isEqualTo(BorealisColors().ink3)
    }

    // Helpers

    private fun showText(
        style: BackgroundStyle,
        look: BorealisLook,
        onColors: (BorealisColors) -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalBorealisLook provides look) {
                GlassHostOver(style, background = { Box(Modifier.fillMaxSize()) }) {
                    GlassTextColors { onColors(LocalBorealisColors.current) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Shows a host over [background] with a 100 dp panel at [panelAt], in dp from the corner. */
    private fun show(
        style: BackgroundStyle = BackgroundStyle.AuroraCurtains,
        background: @Composable () -> Unit,
        shape: Shape = round,
        panelAt: Pair<Int, Int> = PANEL_ON_THE_LIGHT_SIDE,
    ) {
        composeRule.setContent {
            GlassHostOver(style, background = background, modifier = Modifier.testTag(ROOT)) {
                Box(
                    Modifier.offset(panelAt.first.dp, panelAt.second.dp)
                        .size(PANEL_DP.dp)
                        .testTag(PANEL)
                        .glassSurface(shape)
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun HalfAndHalf() {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().background(Color.White))
            Box(Modifier.weight(1f).fillMaxSize().background(Color.Black))
        }
    }

    private fun panel(): PixelMap = pixels(PANEL)

    private fun pixels(tag: String): PixelMap =
        composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()

    private fun PixelMap.center() = this[width / 2, height / 2]

    private fun PixelMap.row(y: Int): List<Color> = (0 until width).map { this[it, y] }

    private fun List<Color>.maxStep(): Float = zipWithNext { a, b -> abs(a.red - b.red) }.max()

    private fun Color.toArgbInt() = toArgb()

    private companion object {
        const val ROOT = "root"
        const val PANEL = "panel"
        const val CONTROL = "control"
        const val PANEL_DP = 100
        const val API_NO_BLUR = 30
        const val TOLERANCE = 0.02f

        // The backdrop is white on the left half of the 360 dp window and black on the right.
        // A panel at 130 dp is 260 px from the left: the edge, at 360 px, is 100 px into it.
        val PANEL_ON_THE_LIGHT_SIDE = 20 to 100
        val PANEL_ACROSS_THE_EDGE = 130 to 100
        const val EDGE_IN_PANEL = 100
        const val PANEL_ROW = 100
        const val FAR = 40
        const val SMOOTH = 0.09f
        const val SHARP = 0.1f
    }
}
