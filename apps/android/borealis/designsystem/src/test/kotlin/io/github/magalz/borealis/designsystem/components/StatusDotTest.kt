package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The status dot of a host (WCAG 1.4.1, colour is not the only cue): each state has a shape of its
 * own, besides its colour. In a 10dp box: Online is a filled circle, Attention a filled triangle
 * and Offline a ring that is hollow. Unknown looks like Offline.
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

    /** The pixel at ([x], [y]) dp of the dot, which is 8dp into the frame. */
    private fun assertAt(x: Float, y: Float, expected: Color) {
        composeRule.onNodeWithTag(FRAME).assertPixel(x = MARGIN + x, y = MARGIN + y, expected)
    }

    /** Which pixels of the dot are not the page, for a status in the look. */
    private fun shapeOf(status: DotStatus, look: BorealisLook = BorealisLook()): List<Boolean> {
        show(status, look)
        val frame = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val from = (MARGIN * PIXELS_PER_DP).toInt()
        val to = ((MARGIN + DOT) * PIXELS_PER_DP).toInt()
        val page = BorealisTokens.Color.Page
        return (from until to).flatMap { y ->
            (from until to).map { x ->
                val pixel = frame[x, y]
                abs(pixel.red - page.red) +
                    abs(pixel.green - page.green) +
                    abs(pixel.blue - page.blue) > COVERED
            }
        }
    }

    private var shown by mutableStateOf(Dot(DotStatus.Online, null, BorealisLook()))
    private var started = false

    private class Dot(val status: DotStatus, val description: String?, val look: BorealisLook)

    /** Shows the dot of [status]; the first call sets the content, the others change it. */
    private fun show(
        status: DotStatus,
        look: BorealisLook = BorealisLook(),
        description: String? = null,
    ) {
        shown = Dot(status, description, look)
        if (!started) {
            started = true
            composeRule.setContent { Specimen(shown) }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Specimen(dot: Dot) {
        BorealisTheme(look = dot.look) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(MARGIN.dp)) {
                StatusDot(dot.status, contentDescription = dot.description)
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val MARGIN = 8f
        const val DOT = 10f
        const val COVERED = 0.15f
        const val MIN_DIFFERENCE = 12
        const val SAME_SHAPE = 4
    }
}
