package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.PIXELS_PER_DP
import io.github.magalz.borealis.designsystem.testing.tvHostCardWidth
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How near the edge of a host card the ink of its text comes. At Wider the words of a name or a
 * detail are spaced half an em apart, and Android wraps such a line as if the spans on its spaces
 * were not there, so a line can end up to one word spacing past the box that the text was given.
 * That spacing grows with the text, so what the card keeps free of text at its end has to grow with
 * it: at the largest text, 130 percent over a system font scale of 1.3, in every font, in each
 * density, on a phone and a TV, no pixel of text is drawn within the border of the card and 1dp of
 * it, whatever the name and the detail say.
 *
 * The card is read pixel by pixel for each of the texts: the ink is what differs from the card in
 * the columns next to its right edge, which are the border and the surface all the way down but for
 * the rounded corners, and those are left out.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1600dp-xhdpi", fontScale = SYSTEM_SCALE)
class HostCardInkEdgeTest(private val case: InkCase) {
    @get:Rule val composeRule = createComposeRule()

    /** What a run changes: the font, the density of the cards and the form factor. */
    class InkCase(val font: FontChoice, val density: CardDensity, val formFactor: FormFactor) {
        override fun toString() = "$font-$density-$formFactor"
    }

    private class Texts(val name: String, val detail: String)

    private var texts by mutableStateOf(Texts("Desk PC", "192.168.1.20"))

    private val look =
        BorealisLook(
            font = case.font,
            textSpacing = TextSpacing.Wider,
            textSize = TextSize.Largest,
            cardDensity = case.density,
        )

    /** A card of two columns in a phone of 360dp, and a card of the grid of a TV of 960dp. */
    private val width: Dp =
        if (case.formFactor == FormFactor.Tv) tvHostCardWidth(case.density) else PHONE_CARD_WIDTH

    @Test
    fun `no text of a host card is drawn at its border`() {
        composeRule.setContent {
            val shown = texts
            BorealisTheme(case.formFactor, look) {
                HostCard(
                    name = shown.name,
                    status = DotStatus.Online,
                    statusText = "Online and ready to stream",
                    onClick = {},
                    modifier = Modifier.width(width).testTag(CARD),
                    detail = shown.detail,
                    tags = listOf("HEVC 10-bit", "HDR", "4:4:4", "AV1 10-bit"),
                    runningApp = "Steam Big Picture Mode",
                )
            }
        }

        val tooNear =
            textsToTry().mapNotNull { shown ->
                texts = shown
                composeRule.waitForIdle()
                val distance =
                    closestInk(composeRule.onNodeWithTag(CARD).captureToImage().toPixelMap())
                if (distance >= LEAST_DISTANCE_PX) null
                else "\"${shown.name}\" / \"${shown.detail}\": $distance px"
            }

        assertWithMessage("texts with ink within $LEAST_DISTANCE_PX px of the edge of the card")
            .that(tooNear)
            .isEmpty()
    }

    /**
     * The pixels from the right edge of the card to the ink that is nearest to it, looking at the
     * [SCAN_DP] of the card at the edge, or [Int.MAX_VALUE] when there is none. The first pixel
     * next to the edge is 0.
     */
    private fun closestInk(image: PixelMap): Int {
        val corner = CORNER_DP * PIXELS_PER_DP
        val rows = corner until image.height - corner
        for (distance in 0 until SCAN_DP * PIXELS_PER_DP) {
            val x = image.width - 1 - distance
            val surface = majority(image, x, rows)
            if (rows.any { y -> differs(image[x, y], surface) }) return distance
        }
        return Int.MAX_VALUE
    }

    /** The colour that most of the [rows] of column [x] have: the card, whose text is sparse. */
    private fun majority(image: PixelMap, x: Int, rows: IntRange): Color {
        var candidate = image[x, rows.first]
        var votes = 0
        for (y in rows) {
            val pixel = image[x, y]
            when {
                votes == 0 -> {
                    candidate = pixel
                    votes = 1
                }
                pixel == candidate -> votes++
                else -> votes--
            }
        }
        return candidate
    }

    private fun differs(pixel: Color, surface: Color) =
        abs(pixel.red - surface.red) +
            abs(pixel.green - surface.green) +
            abs(pixel.blue - surface.blue) > INK

    /**
     * Names and details of a few words each, from a seed so that a run is the same every time, and
     * the name that was the widest case of the fit tests.
     */
    private fun textsToTry(): List<Texts> {
        val random = Random(SEED)

        fun phrase(words: Int) = (1..words).joinToString(" ") { WORDS[random.nextInt(WORDS.size)] }
        return listOf(Texts("Living Room PC", "192.168.1.31")) +
            List(VARIANTS) { Texts(phrase(2 + random.nextInt(4)), phrase(3 + random.nextInt(6))) }
    }

    private companion object {
        const val CARD = "card"
        const val SEED = 7
        const val VARIANTS = 70

        /** The darkest ink is far above this, and the pixels of the card are the same to it. */
        const val INK = 0.1f

        /** The border of the card, 1dp, and the 1dp that no text may come within. */
        const val LEAST_DISTANCE_DP = 2
        const val LEAST_DISTANCE_PX = LEAST_DISTANCE_DP * PIXELS_PER_DP

        /**
         * How far from the edge the card is read: more than the 18dp of the most padding a card
         * has.
         */
        const val SCAN_DP = 20

        /** The corners of the card are rounded by 12dp, and have no straight edge to read. */
        const val CORNER_DP = 14
        val PHONE_CARD_WIDTH = 158.dp

        val WORDS =
            ("Living Room PC Media Center Gaming Desktop Upstairs Left Office Workstation Number " +
                    "Two Steam Deck Studio Main Den Found on this network and ready to pair now " +
                    "Big Picture")
                .split(' ')

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<InkCase> =
            FontChoice.entries.flatMap { font ->
                CardDensity.entries.flatMap { density ->
                    listOf(FormFactor.Phone, FormFactor.Tv).map { InkCase(font, density, it) }
                }
            }
    }
}

/** The system font scale that goes with the largest text of the app: 1.69 in all. */
private const val SYSTEM_SCALE = 1.3f
