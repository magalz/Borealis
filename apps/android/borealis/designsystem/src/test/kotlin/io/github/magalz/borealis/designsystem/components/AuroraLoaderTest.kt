package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import java.io.File
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The loader is 108dp wide here, so a unit of its 108-unit icon geometry is one dp. Its box starts
 * at unit 10 from the top: a unit (x, y) is at (x, y - 10) dp from the node's top-left corner. The
 * four probes sit inside the teal band and inside the violet band, near each end. The upper band is
 * the teal of the accent, as the app icon has it (issue #50, ADR 0058, decision 5).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AuroraLoaderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the bands fill from the left and the loader starts empty`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertEmpty(TEAL_LEFT, TEAL_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `part way through the bands cover the left of the box and not the right`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(HALF_WAY_MILLIS)

        assertDrawn(TEAL_LEFT, VIOLET_LEFT)
        assertEmpty(TEAL_RIGHT, VIOLET_RIGHT)
    }

    @Test
    fun `at the end of the fill both bands are full`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(FULL_MILLIS)

        assertDrawn(TEAL_LEFT, TEAL_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `the fill repeats from empty`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        composeRule.mainClock.advanceTimeBy(RESTARTED_MILLIS)

        assertEmpty(TEAL_LEFT, TEAL_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `with animations removed the bands are full and static from the first frame`() {
        noAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }

        assertDrawn(TEAL_LEFT, TEAL_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
        composeRule.mainClock.advanceTimeBy(RESTARTED_MILLIS)
        assertDrawn(TEAL_LEFT, TEAL_RIGHT, VIOLET_LEFT, VIOLET_RIGHT)
    }

    @Test
    fun `the upper band is the teal of the accent, from the accent to the strong accent`() {
        showFull()

        val teal = borealisColors(BorealisLook(accent = AccentChoice.Teal))
        assertBand(
            teal.accent.copy(alpha = TOP_ALPHA),
            teal.accentStrong.copy(alpha = BOTTOM_ALPHA),
        )
    }

    @Test
    fun `the upper band has the colours of the launcher icon`() {
        showFull()

        val (top, bottom) = iconBand()
        assertBand(top, bottom)
    }

    private fun showFull() {
        noAnimations()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Specimen() }
    }

    /**
     * Asserts that the loader, full, has at [BAND_PROBE] the colour of a gradient from [top] to
     * [bottom] over the page: the gradient of the band runs from unit 14 to unit 80, as the icon's.
     */
    private fun assertBand(top: Color, bottom: Color) {
        val at = (BAND_PROBE.second - BAND_TOP) / (BAND_BOTTOM - BAND_TOP)
        fun mixed(from: Float, to: Float) = from + (to - from) * at
        val alpha = mixed(top.alpha, bottom.alpha)
        val page = BorealisTokens.Color.Page
        val pixel =
            composeRule.onNodeWithTag(FRAME).pixelAt(BAND_PROBE.first, BAND_PROBE.second - TOP)
        val expected =
            Triple(
                mixed(top.red, bottom.red) * alpha + page.red * (1 - alpha),
                mixed(top.green, bottom.green) * alpha + page.green * (1 - alpha),
                mixed(top.blue, bottom.blue) * alpha + page.blue * (1 - alpha),
            )
        val distance =
            abs(pixel.red - expected.first) +
                abs(pixel.green - expected.second) +
                abs(pixel.blue - expected.third)
        assertWithMessage("the band at $BAND_PROBE is $pixel, not about $expected")
            .that(distance)
            .isLessThan(BAND_TOLERANCE)
    }

    /**
     * The colours of the upper band of the launcher icon, as `design/icon/generate.py` wrote them
     * into the foreground layer: the gradient that runs from unit 14 to unit 80.
     */
    private fun iconBand(): Pair<Color, Color> {
        assertWithMessage("the launcher layer is not at ${ICON_FOREGROUND.absolutePath}")
            .that(ICON_FOREGROUND.isFile)
            .isTrue()
        val found = ICON_BAND.findAll(ICON_FOREGROUND.readText()).toList()
        assertWithMessage("the foreground layer has to have the band gradient once")
            .that(found)
            .hasSize(1)
        val (top, bottom) = found.single().destructured
        return argb(top) to argb(bottom)
    }

    private fun argb(hex: String): Color = Color(hex.toLong(radix = 16).toInt())

    private fun assertDrawn(vararg probes: Pair<Float, Float>) = probes.forEach {
        assertWithMessage("probe $it is drawn")
            .that(distanceFromPage(it))
            .isGreaterThan(MIN_DISTANCE)
    }

    private fun assertEmpty(vararg probes: Pair<Float, Float>) = probes.forEach {
        assertWithMessage("probe $it is empty").that(distanceFromPage(it)).isLessThan(MAX_EMPTY)
    }

    private fun distanceFromPage(probe: Pair<Float, Float>): Float {
        val pixel = composeRule.onNodeWithTag(FRAME).pixelAt(probe.first, probe.second - TOP)
        val page = BorealisTokens.Color.Page
        return abs(pixel.red - page.red) +
            abs(pixel.green - page.green) +
            abs(pixel.blue - page.blue)
    }

    @Composable
    private fun Specimen() {
        BorealisTheme {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page)) {
                AuroraLoader(size = 108.dp)
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val TOP = 10f
        const val MIN_DISTANCE = 0.1f
        const val MAX_EMPTY = 0.02f

        // The gradient of the upper band: its units from the top to the bottom, its opacities and
        // the probe in it, near the top and the right end where the colour is still the top one.
        const val BAND_TOP = 14f
        const val BAND_BOTTOM = 80f
        const val TOP_ALPHA = 0.95f
        const val BOTTOM_ALPHA = 0.10f
        const val BAND_TOLERANCE = 0.06f
        val BAND_PROBE = 104f to 20f

        /**
         * The layer is generated into the resources of `:app`, which is next to the borealis
         * modules.
         */
        val ICON_FOREGROUND =
            File("../../app/src/main/res/drawable/borealis_launcher_foreground.xml")
        val ICON_BAND =
            Regex(
                """android:startY="14"\s+android:endX="54"\s+android:endY="80">\s*""" +
                    """<item android:offset="0" android:color="#([0-9A-F]{8})" />\s*""" +
                    """<item android:offset="1" android:color="#([0-9A-F]{8})" />"""
            )

        // Probes in the 108-unit geometry of the icon: inside the band, near each end.
        val TEAL_LEFT = 8f to 66f
        val TEAL_RIGHT = 100f to 26f
        val VIOLET_LEFT = 8f to 76f
        val VIOLET_RIGHT = 100f to 44f

        // The fill takes 1100ms, holds full to 1400ms and starts again.
        const val HALF_WAY_MILLIS = 400L
        const val FULL_MILLIS = 1250L
        const val RESTARTED_MILLIS = 1450L
    }
}
