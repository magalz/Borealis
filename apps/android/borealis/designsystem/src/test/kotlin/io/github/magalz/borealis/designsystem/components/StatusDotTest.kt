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
import io.github.magalz.borealis.designsystem.theme.FormFactor
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
 * and Offline a ring that is hollow. Unknown looks like Offline. Online also has a soft dark shadow
 * in the page colour (issue #50, ADR 0058, decision 5), outside the box, so that an online dot
 * stands out from the card: it is not drawn in High contrast, and it is darkest next to the dot, so
 * that the dot keeps 3:1 against what is next to it (WCAG 1.4.11), more than it does without it. On
 * a TV the glass is darker, and a shadow in the page colour read as a faint ring there (the device
 * round: luma 22 down to 17, where a phone goes from 30 to 18): the shadow of a TV is stronger and
 * reaches farther, so that it is as clear on the TV as the phone's is on a phone, and it still ends
 * before the status text. A handheld has the phone's.
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
        // The corners of the box are outside a circle.
        assertAt(0.5f, 0.5f, BorealisTokens.Color.Page)
        assertAt(9.5f, 9.5f, BorealisTokens.Color.Page)
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

    // The shadow of an online host

    @Test
    fun `an online host has a soft shadow in the page colour round the dot, outside its box`() {
        show(DotStatus.Online, backdrop = LIGHT)
        val frame = frame()

        // On a lighter backdrop the shadow shows. It is round, the same in the four directions, and
        // reaches 2dp outside the box and more.
        for (radius in listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW)) {
            val strengths = ring(radius).map { shadowAt(frame, it) }
            assertWithMessage("the shadow at $radius dp is $strengths")
                .that(strengths.min())
                .isGreaterThan(VISIBLE)
            assertWithMessage("the shadow at $radius dp is $strengths")
                .that(strengths.max() - strengths.min())
                .isLessThan(ROUND)
        }
        // It is the backdrop darkened towards the page, and not another colour.
        for (radius in listOf(EDGE_SHADOW, NEAR_SHADOW)) {
            for (pixel in ring(radius).map { frame[it.first, it.second] }) {
                assertThat(offTheLine(pixel)).isLessThan(HUE)
            }
        }
    }

    @Test
    fun `the shadow is strongest at the dot, fades with the distance and is gone 5dp past the box`() {
        show(DotStatus.Online, backdrop = LIGHT)
        val frame = frame()

        val strengths =
            listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW, FAR_SHADOW).map { radius ->
                ring(radius).map { shadowAt(frame, it) }.average()
            }
        assertWithMessage("the shadow from the edge outwards is $strengths")
            .that(strengths.zipWithNext().all { (nearer, farther) -> farther < nearer })
            .isTrue()
        assertThat(strengths.first()).isGreaterThan(STRONG)
        // The status text of a host card is 6dp from the box: the shadow does not reach it.
        for (pixel in ring(PAST_SHADOW)) assertThat(shadowAt(frame, pixel)).isLessThan(NONE)
    }

    @Test
    fun `the shadow changes neither the size of the dot nor the box that holds it`() {
        show(DotStatus.Online)

        composeRule.onNodeWithTag(DOT_TAG).assertWidthIsEqualTo(10.dp).assertHeightIsEqualTo(10.dp)
        composeRule.onNodeWithTag(FRAME).assertWidthIsEqualTo(26.dp).assertHeightIsEqualTo(26.dp)
    }

    @Test
    fun `high contrast draws the dot solid, with no shadow, on every form factor`() {
        val look = BorealisLook(highContrast = true)
        for (formFactor in FormFactor.entries) {
            show(DotStatus.Online, look, backdrop = LIGHT, formFactor = formFactor)

            assertAt(5f, 5f, borealisColors(look).success)
            val frame = frame()
            for (radius in listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW, FAR_SHADOW)) {
                for ((x, y) in ring(radius)) {
                    assertPixelIs(frame[x, y], LIGHT, "$radius dp on a $formFactor")
                }
            }
        }
    }

    @Test
    fun `attention, offline and unknown have no shadow`() {
        for (status in listOf(DotStatus.Attention, DotStatus.Offline, DotStatus.Unknown)) {
            show(status, backdrop = LIGHT)
            val frame = frame()

            for (radius in listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW, FAR_SHADOW)) {
                for ((x, y) in ring(radius)) {
                    assertPixelIs(frame[x, y], LIGHT, "$status at $radius dp")
                }
            }
        }
    }

    @Test
    fun `the dot keeps 3 to 1 against its shadow on the brightest glass, more than without it`() =
        assertDotContrast(FormFactor.Phone)

    // The shadow of an online host on a TV

    @Test
    fun `on a TV the dot keeps 3 to 1 against its shadow on the brightest glass, too`() =
        assertDotContrast(FormFactor.Tv)

    @Test
    fun `a handheld has the shadow of a phone`() {
        show(DotStatus.Online, backdrop = LIGHT)
        val phone = frame()
        show(DotStatus.Online, backdrop = LIGHT, formFactor = FormFactor.Handheld)
        val handheld = frame()

        val different =
            (0 until phone.width)
                .flatMap { x -> (0 until phone.height).map { y -> x to y } }
                .count { (x, y) -> distance(phone[x, y], handheld[x, y]) > PIXEL_TOLERANCE }
        assertWithMessage("$different pixels of the dot of a handheld differ from a phone's")
            .that(different)
            .isEqualTo(0)
    }

    @Test
    fun `on a TV the shadow is as clear over its darker glass as the phone's is over its own`() {
        val phone = drops(FormFactor.Phone, PHONE_GLASS)
        val tv = drops(FormFactor.Tv, TV_GLASS)

        for (radius in listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW)) {
            assertWithMessage(
                    "the shadow at $radius dp darkens the glass of a TV by ${tv[radius]} lightness" +
                        " and the glass of a phone by ${phone[radius]}"
                )
                .that(tv.getValue(radius))
                .isAtLeast(phone.getValue(radius))
        }
    }

    @Test
    fun `on a TV the shadow reaches farther than on a phone, and ends before the status text`() {
        val phoneReach = reach(FormFactor.Phone, PHONE_GLASS)
        val tvReach = reach(FormFactor.Tv, TV_GLASS)
        assertWithMessage(
                "the shadow shows $tvReach dp from the centre on a TV, $phoneReach on a phone"
            )
            .that(tvReach)
            .isGreaterThan(phoneReach)

        // The status text of a host card starts 6dp past the box, 11dp from the centre.
        show(DotStatus.Online, backdrop = TV_GLASS, formFactor = FormFactor.Tv)
        val frame = frame()
        for (radius in listOf(TEXT_EDGE, PAST_SHADOW)) {
            for ((x, y) in ring(radius)) {
                assertWithMessage("the shadow at $radius dp from the centre of the dot on a TV")
                    .that(lightness(TV_GLASS) - lightness(frame[x, y]))
                    .isLessThan(NO_DROP)
            }
        }
    }

    /**
     * The contrast of the dot of a [formFactor] against the lightest pixels next to it, over the
     * brightest glass of a host card, is 3 to 1 and more than it is without the shadow.
     */
    private fun assertDotContrast(formFactor: FormFactor) {
        for (vision in ColourVision.entries) {
            val brightest = brightestHostGlass(vision)
            val backdrop = grey(brightest)
            show(
                DotStatus.Online,
                BorealisLook(colourVision = vision),
                backdrop = backdrop,
                formFactor = formFactor,
            )
            val frame = frame()

            // What is next to the dot is the lightest of the pixels just outside its edge: the
            // innermost colour of the shadow, which is the darkest of it.
            val dot = luminance(frame[CENTRE, CENTRE])
            val next = nextToTheDot(frame).maxOf { luminance(it) }
            val ratio = ratio(dot, next)
            val without = ratio(dot, luminance(backdrop))
            assertWithMessage(
                    "$formFactor, $vision: the dot is $dot and its shadow $next, over glass of " +
                        "$brightest, $ratio to 1 (without the shadow $without to 1)"
                )
                .that(ratio)
                .isAtLeast(NON_TEXT_RATIO)
            assertWithMessage(
                    "$formFactor, $vision: $ratio to 1 with the shadow, $without to 1 without it"
                )
                .that(ratio)
                .isAtLeast(without)
        }
    }

    /**
     * How much the shadow of the dot of a [formFactor] darkens the [glass], as a loss of lightness
     * (CIE L*), at each distance from the centre of the dot that the tests look at.
     */
    private fun drops(formFactor: FormFactor, glass: Color): Map<Float, Double> {
        show(DotStatus.Online, backdrop = glass, formFactor = formFactor)
        val frame = frame()
        return listOf(EDGE_SHADOW, NEAR_SHADOW, MIDDLE_SHADOW).associateWith { radius ->
            ring(radius).map { (x, y) -> lightness(glass) - lightness(frame[x, y]) }.average()
        }
    }

    /**
     * The farthest distance from the centre of the dot, in steps of 0.5dp, where the shadow still
     * darkens the [glass] by a visible step of lightness all round.
     */
    private fun reach(formFactor: FormFactor, glass: Color): Float {
        show(DotStatus.Online, backdrop = glass, formFactor = formFactor)
        val frame = frame()
        return REACH_RADII.lastOrNull { radius ->
            ring(radius).all { (x, y) -> lightness(glass) - lightness(frame[x, y]) >= VISIBLE_DROP }
        } ?: 0f
    }

    /** CIE L* of [color]: what an eye sees as a step of brightness, where the luminance is not. */
    private fun lightness(color: Color): Double {
        val y = luminance(color)
        return if (y <= LINEAR_PART) y * LINEAR_SLOPE else 116 * y.pow(1 / 3.0) - 16
    }

    /** The pixel at ([x], [y]) dp of the dot, which is 8dp into the frame. */
    private fun assertAt(x: Float, y: Float, expected: Color) {
        composeRule.onNodeWithTag(FRAME).assertPixel(x = MARGIN + x, y = MARGIN + y, expected)
    }

    private fun frame(): PixelMap = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()

    /** The contrast ratio of two luminances, as WCAG 2.x has it. */
    private fun ratio(a: Double, b: Double) = (max(a, b) + NEAR_BLACK) / (min(a, b) + NEAR_BLACK)

    private fun shadowAt(frame: PixelMap, pixel: Pair<Int, Int>): Float =
        shadowAt(frame[pixel.first, pixel.second])

    /**
     * How far [pixel] is from the [LIGHT] backdrop towards the page: 0 is the backdrop, 1 the page.
     */
    private fun shadowAt(pixel: Color): Float {
        val along =
            (LIGHT.red - pixel.red) * (LIGHT.red - PAGE.red) +
                (LIGHT.green - pixel.green) * (LIGHT.green - PAGE.green) +
                (LIGHT.blue - pixel.blue) * (LIGHT.blue - PAGE.blue)
        val whole =
            (LIGHT.red - PAGE.red).pow(2) +
                (LIGHT.green - PAGE.green).pow(2) +
                (LIGHT.blue - PAGE.blue).pow(2)
        return along / whole
    }

    /**
     * How far [pixel] is from the line from the [LIGHT] backdrop to the page, as a sum of channels.
     */
    private fun offTheLine(pixel: Color): Float {
        val strength = shadowAt(pixel)
        return abs(pixel.red - (LIGHT.red + strength * (PAGE.red - LIGHT.red))) +
            abs(pixel.green - (LIGHT.green + strength * (PAGE.green - LIGHT.green))) +
            abs(pixel.blue - (LIGHT.blue + strength * (PAGE.blue - LIGHT.blue)))
    }

    private fun assertPixelIs(pixel: Color, expected: Color, where: String) {
        assertWithMessage("the pixel $where from the centre is $pixel, not $expected")
            .that(distance(pixel, expected))
            .isLessThan(PIXEL_TOLERANCE)
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

    private var shown by
        mutableStateOf(Dot(DotStatus.Online, null, BorealisLook(), PAGE, FormFactor.Phone))
    private var started = false

    private class Dot(
        val status: DotStatus,
        val description: String?,
        val look: BorealisLook,
        val backdrop: Color,
        val formFactor: FormFactor,
    )

    /** Shows the dot of [status]; the first call sets the content, the others change it. */
    private fun show(
        status: DotStatus,
        look: BorealisLook = BorealisLook(),
        description: String? = null,
        backdrop: Color = PAGE,
        formFactor: FormFactor = FormFactor.Phone,
    ) {
        shown = Dot(status, description, look, backdrop, formFactor)
        if (!started) {
            started = true
            composeRule.setContent { Specimen(shown) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(dot: Dot) {
        BorealisTheme(dot.formFactor, dot.look) {
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
         * A backdrop lighter than the page, so that a shadow in the page colour shows: the grey of
         * the brightest glass behind a host card (luminance 0.0497).
         */
        val LIGHT = Color(63, 63, 63)

        /**
         * Distances from the centre of the dot, in dp: its edge is at 5, the box ends at 5 to 7.
         */
        const val EDGE_SHADOW = 5.75f
        const val NEAR_SHADOW = 7.25f
        const val MIDDLE_SHADOW = 8.75f
        const val FAR_SHADOW = 9.75f
        const val PAST_SHADOW = 11.75f

        /** The status text of a host card starts 6dp past the box: 11dp from the centre. */
        const val TEXT_EDGE = 11f

        /**
         * The glass behind the dot as the device round measured it, in the capture of a TV (luma
         * 22) and of a phone (luma 30), where the shadow of a TV read as a faint ring.
         */
        val TV_GLASS = Color(22, 22, 22)
        val PHONE_GLASS = Color(30, 30, 30)

        /** The distances, in dp from the centre, that the reach of a shadow is looked for at. */
        val REACH_RADII = (0..13).map { 5.25f + it * 0.5f }

        /** A step of lightness (CIE L*) that shows, and a loss of it that is none. */
        const val VISIBLE_DROP = 0.5
        const val NO_DROP = 0.1
        const val LINEAR_PART = 216.0 / 24389
        const val LINEAR_SLOPE = 24389.0 / 27

        const val MIN_DIFFERENCE = 12
        const val SAME_SHAPE = 4
        const val VISIBLE = 0.04f
        const val STRONG = 0.4
        const val ROUND = 0.03f
        const val HUE = 0.03f
        const val NONE = 0.01f
        const val PIXEL_TOLERANCE = 0.02f
        const val NEAR_BLACK = 0.05
        const val NON_TEXT_RATIO = 3.0
    }
}
