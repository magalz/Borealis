package io.github.magalz.borealis.designsystem.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.PIXELS_PER_DP
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassContrastTable
import io.github.magalz.borealis.designsystem.glass.backdropLuminance
import io.github.magalz.borealis.designsystem.glass.glassSpec
import io.github.magalz.borealis.designsystem.glass.luminance
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The status dot of a host (WCAG 1.4.1, colour is not the only cue): each state has a shape of its
 * own, besides its colour. In a 10dp box: Online is a filled circle, Attention a filled triangle
 * and Offline a ring that is hollow. Unknown looks like Offline. Online also has a soft glow in the
 * success colour (issue #50, ADR 0058, decision 5), outside the box, so that an online host stands
 * out: it is not drawn in High contrast, and it leaves a gap round the dot, so that the dot keeps
 * 3:1 against what is next to it (WCAG 1.4.11).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class StatusDotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `an online host is a filled circle in the success colour`() {
        show(DotStatus.Online)

        assertAt(5f, 5f, BorealisTokens.Color.Success)
        assertAt(1.5f, 5f, BorealisTokens.Color.Success)
        // The corners of the box are outside a circle: the dot does not reach them, and the glow is
        // fainter than the dot.
        for ((x, y) in listOf(0.5f to 0.5f, 9.5f to 9.5f)) {
            assertThat(glowAt(frame(), x, y)).isLessThan(HALF)
        }
    }

    @Test
    fun `an online host that needs attention is a filled triangle in the warning colour`() {
        show(DotStatus.Attention)

        assertAt(5f, 5f, BorealisTokens.Color.Warning)
        // Narrow at the top and wide at the bottom: the top corners are empty, the bottom ones
        // full.
        assertAt(0.8f, 1f, BorealisTokens.Color.Page)
        assertAt(9.2f, 1f, BorealisTokens.Color.Page)
        assertAt(1.2f, 9f, BorealisTokens.Color.Warning)
        assertAt(8.8f, 9f, BorealisTokens.Color.Warning)
    }

    @Test
    fun `an offline host is a hollow ring in the offline colour`() {
        show(DotStatus.Offline)

        assertAt(0.8f, 5f, BorealisTokens.Color.Offline)
        assertAt(9.2f, 5f, BorealisTokens.Color.Offline)
        assertAt(5f, 0.8f, BorealisTokens.Color.Offline)
        assertAt(5f, 5f, BorealisTokens.Color.Page)
    }

    @Test
    fun `an unknown state looks like offline`() {
        show(DotStatus.Unknown)

        assertAt(0.8f, 5f, BorealisTokens.Color.Offline)
        assertAt(5f, 5f, BorealisTokens.Color.Page)
    }

    @Test
    fun `each state has a shape of its own, whatever its colour`() {
        val online = shapeOf(DotStatus.Online)
        val attention = shapeOf(DotStatus.Attention)
        val offline = shapeOf(DotStatus.Offline)

        assertThat(online.count { it }).isGreaterThan(0)
        for ((name, pair) in
            mapOf(
                "online and attention" to (online to attention),
                "online and offline" to (online to offline),
                "attention and offline" to (attention to offline),
            )) {
            val apart = pair.first.indices.count { pair.first[it] != pair.second[it] }
            assertWithMessage("the shapes of $name differ in $apart pixels")
                .that(apart)
                .isAtLeast(MIN_DIFFERENCE)
        }
        assertThat(shapeOf(DotStatus.Unknown)).isEqualTo(offline)
    }

    @Test
    fun `a colour-blind palette gives the dots the colours of the palette and keeps the shapes`() {
        val look = BorealisLook(colourVision = ColourVision.RedGreen)
        show(DotStatus.Online, look)

        assertAt(5f, 5f, BorealisTokens.ColourVision.RedGreen.Success)
        // The edge of a circle is blended with the page, and its colour decides how many of those
        // pixels count as covered: the shape is the same within a few pixels.
        val plain = shapeOf(DotStatus.Online)
        val palette = shapeOf(DotStatus.Online, look)
        assertThat(plain.indices.count { plain[it] != palette[it] }).isAtMost(SAME_SHAPE)
    }

    @Test
    fun `the dot carries its content description`() {
        show(DotStatus.Online, description = "Online")

        composeRule.onNodeWithContentDescription("Online").assertContentDescriptionEquals("Online")
    }

    // The glow of an online host

    @Test
    fun `an online host has a soft glow in the success colour outside the box of the dot`() {
        show(DotStatus.Online)
        val frame = frame()

        // The glow is round: the same in the four directions, 2dp outside the box and more.
        for (radius in listOf(NEAR_GLOW, MIDDLE_GLOW)) {
            val strengths = ring(radius).map { glowAt(frame, it) }
            assertWithMessage("the glow at $radius dp is $strengths")
                .that(strengths.min())
                .isGreaterThan(VISIBLE)
            assertWithMessage("the glow at $radius dp is $strengths")
                .that(strengths.max() - strengths.min())
                .isLessThan(ROUND)
            assertWithMessage("the glow at $radius dp is fainter than the dot")
                .that(strengths.max())
                .isLessThan(HALF)
        }
        // It is a glow in the success colour: the pixels are the page lit by it, not another hue.
        for (pixel in ring(NEAR_GLOW).map { frame[it.first, it.second] }) {
            assertThat(offTheLine(pixel, BorealisTokens.Color.Success)).isLessThan(HUE)
        }
    }

    @Test
    fun `the glow fades with the distance and is gone 5dp past the box`() {
        show(DotStatus.Online)
        val frame = frame()

        val near = ring(NEAR_GLOW).map { glowAt(frame, it) }.average()
        val middle = ring(MIDDLE_GLOW).map { glowAt(frame, it) }.average()
        val far = ring(FAR_GLOW).map { glowAt(frame, it) }.average()
        assertThat(middle).isLessThan(near)
        assertThat(far).isLessThan(middle)
        // The status text of a host card is 6dp from the box: the glow does not reach it.
        for (pixel in ring(PAST_GLOW)) assertThat(glowAt(frame, pixel)).isLessThan(NONE)
    }

    @Test
    fun `the glow changes neither the size of the dot nor the box that holds it`() {
        show(DotStatus.Online)

        composeRule.onNodeWithTag(DOT_TAG).assertWidthIsEqualTo(10.dp).assertHeightIsEqualTo(10.dp)
        composeRule.onNodeWithTag(FRAME).assertWidthIsEqualTo(26.dp).assertHeightIsEqualTo(26.dp)
    }

    @Test
    fun `the glow follows the colour of a colour-blind palette`() {
        val look = BorealisLook(colourVision = ColourVision.RedGreen)
        show(DotStatus.Online, look)

        val success = borealisColors(look).success
        val frame = frame()
        for (pixel in ring(NEAR_GLOW).map { frame[it.first, it.second] }) {
            assertThat(offTheLine(pixel, success)).isLessThan(HUE)
            assertThat(glowAt(pixel, success)).isGreaterThan(VISIBLE)
        }
    }

    @Test
    fun `high contrast draws the dot solid, with no glow`() {
        val look = BorealisLook(highContrast = true)
        show(DotStatus.Online, look)

        assertAt(5f, 5f, borealisColors(look).success)
        val frame = frame()
        for (radius in listOf(NEAR_GLOW, MIDDLE_GLOW, FAR_GLOW)) {
            for ((x, y) in ring(radius)) assertPixelIsPage(frame[x, y], "$radius dp")
        }
    }

    @Test
    fun `attention, offline and unknown have no glow`() {
        for (status in listOf(DotStatus.Attention, DotStatus.Offline, DotStatus.Unknown)) {
            show(status)
            val frame = frame()

            for (radius in listOf(NEAR_GLOW, MIDDLE_GLOW, FAR_GLOW)) {
                for ((x, y) in ring(radius)) assertPixelIsPage(frame[x, y], "$status at $radius dp")
            }
        }
    }

    @Test
    fun `the dot keeps 3 to 1 against what is next to it on the brightest glass of every palette`() {
        for (vision in ColourVision.entries) {
            val brightest = brightestHostGlass(vision)
            val look = BorealisLook(colourVision = vision)
            show(DotStatus.Online, look, backdrop = grey(brightest))
            val frame = frame()

            val dot = luminance(frame[CENTRE, CENTRE])
            val next = nextToTheDot(frame).maxOf { luminance(it) }
            val ratio = (max(dot, next) + NEAR_BLACK) / (min(dot, next) + NEAR_BLACK)
            assertWithMessage(
                    "$vision: the dot is $dot and what is next to it $next, over glass of " +
                        "$brightest, $ratio to 1"
                )
                .that(ratio)
                .isAtLeast(NON_TEXT_RATIO)
        }
    }

    /** The pixel at ([x], [y]) dp of the dot, which is 8dp into the frame. */
    private fun assertAt(x: Float, y: Float, expected: Color) {
        composeRule.onNodeWithTag(FRAME).assertPixel(x = MARGIN + x, y = MARGIN + y, expected)
    }

    private fun frame(): PixelMap = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()

    /** The pixel at ([x], [y]) dp of the dot, as a [glowAt] strength: 0 is the page, 1 the dot. */
    private fun glowAt(frame: PixelMap, x: Float, y: Float): Float =
        glowAt(
            frame[((MARGIN + x) * PIXELS_PER_DP).toInt(), ((MARGIN + y) * PIXELS_PER_DP).toInt()],
            BorealisTokens.Color.Success,
        )

    private fun glowAt(frame: PixelMap, pixel: Pair<Int, Int>): Float =
        glowAt(frame[pixel.first, pixel.second], BorealisTokens.Color.Success)

    /** How far [pixel] is from the page towards [colour]: 0 is the page, 1 is [colour]. */
    private fun glowAt(pixel: Color, colour: Color): Float {
        val page = BorealisTokens.Color.Page
        val along =
            (pixel.red - page.red) * (colour.red - page.red) +
                (pixel.green - page.green) * (colour.green - page.green) +
                (pixel.blue - page.blue) * (colour.blue - page.blue)
        val whole =
            (colour.red - page.red).pow(2) +
                (colour.green - page.green).pow(2) +
                (colour.blue - page.blue).pow(2)
        return along / whole
    }

    /** How far [pixel] is from the line from the page to [colour], as the sum of its channels. */
    private fun offTheLine(pixel: Color, colour: Color): Float {
        val page = BorealisTokens.Color.Page
        val strength = glowAt(pixel, colour)
        return abs(pixel.red - (page.red + strength * (colour.red - page.red))) +
            abs(pixel.green - (page.green + strength * (colour.green - page.green))) +
            abs(pixel.blue - (page.blue + strength * (colour.blue - page.blue)))
    }

    private fun assertPixelIsPage(pixel: Color, where: String) {
        val page = BorealisTokens.Color.Page
        val distance =
            abs(pixel.red - page.red) + abs(pixel.green - page.green) + abs(pixel.blue - page.blue)
        assertWithMessage("the pixel $where from the centre is $pixel, not the page")
            .that(distance)
            .isLessThan(PAGE_TOLERANCE)
    }

    /**
     * The four pixels of the frame that are [radius] dp from the centre of the dot, to the right,
     * to the left, below and above it, as (x, y). The frame is 52 pixels across and the centre of
     * the dot is the corner of four of them, so the distance is that of the centre of a pixel and
     * the four are the same distance away.
     */
    private fun ring(radius: Float): List<Pair<Int, Int>> {
        val offset = (radius * PIXELS_PER_DP - 0.5f).toInt()
        return listOf(
            CENTRE + offset to CENTRE,
            CENTRE - 1 - offset to CENTRE,
            CENTRE to CENTRE + offset,
            CENTRE to CENTRE - 1 - offset,
        )
    }

    /** The pixels just outside the edge of the dot, from 0.5dp to 1dp past it. */
    private fun nextToTheDot(frame: PixelMap): List<Color> =
        (0 until frame.width).flatMap { x ->
            (0 until frame.height).mapNotNull { y ->
                val distance =
                    hypot(
                        (x + 0.5f) / PIXELS_PER_DP - MARGIN - HALF_DOT,
                        (y + 0.5f) / PIXELS_PER_DP - MARGIN - HALF_DOT,
                    )
                if (distance in HALF_DOT + 0.5f..HALF_DOT + 1f) frame[x, y] else null
            }
        }

    /**
     * The luminance of the brightest backdrop that the glass of a host card can have for a viewer
     * of the [vision] palette: over every style, accent, blur and tint that Settings offers, at the
     * default level and at Reduced (a tint is raised to the floor of its style, and a style that is
     * off is solid and darker). It is the glass table, as the app reads it for the text on a card.
     */
    private fun brightestHostGlass(vision: ColourVision): Double {
        var brightest = 0.0
        for (accent in AccentChoice.entries) {
            for (style in BackgroundStyle.entries) {
                val looks =
                    listOf(BorealisLook(accent = accent, colourVision = vision)) +
                        GlassContrastTable.tintsPercent.flatMap { tint ->
                            GlassContrastTable.blursDp
                                .filter { it > 0 }
                                .map { blur ->
                                    BorealisLook(
                                        accent = accent,
                                        colourVision = vision,
                                        glassTintPercent = tint,
                                        glassBlurDp = blur,
                                    )
                                }
                        } +
                        BorealisLook(
                            accent = accent,
                            colourVision = vision,
                            transparency = Transparency.Reduced,
                        )
                for (look in looks) {
                    val spec = glassSpec(look, style, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    if (!spec.solid) {
                        brightest =
                            max(brightest, backdropLuminance(style, spec.blurDp, spec.tintPercent))
                    }
                }
            }
        }
        return brightest
    }

    /** The grey of [luminance], rounded up to 8 bits so that it is no darker than that. */
    private fun grey(luminance: Double): Color {
        val level = ceil((1.055 * luminance.pow(1 / 2.4) - 0.055) * 255).toInt().coerceIn(0, 255)
        return Color(level, level, level)
    }

    /** Which pixels of the dot are the colour of the dot rather than the page, for a status. */
    private fun shapeOf(status: DotStatus, look: BorealisLook = BorealisLook()): List<Boolean> {
        show(status, look)
        val frame = frame()
        val colours = borealisColors(look)
        val ink =
            when (status) {
                DotStatus.Online -> colours.success
                DotStatus.Attention -> colours.warning
                DotStatus.Offline,
                DotStatus.Unknown -> colours.offline
            }
        val from = (MARGIN * PIXELS_PER_DP).toInt()
        val to = ((MARGIN + DOT) * PIXELS_PER_DP).toInt()
        val page = BorealisTokens.Color.Page
        return (from until to).flatMap { y ->
            (from until to).map { x ->
                val pixel = frame[x, y]
                distance(pixel, ink) < distance(pixel, page)
            }
        }
    }

    private fun distance(a: Color, b: Color) =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private var shown by mutableStateOf(Dot(DotStatus.Online, null, BorealisLook(), PAGE))
    private var started = false

    private class Dot(
        val status: DotStatus,
        val description: String?,
        val look: BorealisLook,
        val backdrop: Color,
    )

    /** Shows the dot of [status]; the first call sets the content, the others change it. */
    private fun show(
        status: DotStatus,
        look: BorealisLook = BorealisLook(),
        description: String? = null,
        backdrop: Color = PAGE,
    ) {
        shown = Dot(status, description, look, backdrop)
        if (!started) {
            started = true
            composeRule.setContent { Specimen(shown) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(dot: Dot) {
        BorealisTheme(look = dot.look) {
            Box(Modifier.testTag(FRAME).background(dot.backdrop).padding(MARGIN.dp)) {
                StatusDot(
                    dot.status,
                    contentDescription = dot.description,
                    Modifier.testTag(DOT_TAG),
                )
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val DOT_TAG = "dot"
        const val MARGIN = 8f
        const val DOT = 10f
        const val HALF_DOT = 5f
        val PAGE = BorealisTokens.Color.Page

        /** The pixel of the frame that is the corner of four, and the centre of the dot. */
        const val CENTRE = 26

        /**
         * Distances from the centre of the dot, in dp: the edge is at 5, the box ends at 5 to 7.
         */
        const val NEAR_GLOW = 7.25f
        const val MIDDLE_GLOW = 8.75f
        const val FAR_GLOW = 9.75f
        const val PAST_GLOW = 11.75f

        const val MIN_DIFFERENCE = 12
        const val SAME_SHAPE = 4
        const val VISIBLE = 0.04f
        const val ROUND = 0.03f
        const val HALF = 0.5f
        const val HUE = 0.03f
        const val NONE = 0.01f
        const val PAGE_TOLERANCE = 0.02f
        const val NEAR_BLACK = 0.05
        const val NON_TEXT_RATIO = 3.0
    }
}
