package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHostOver
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * An icon that stands straight on the animated background, outside any glass, has the halo of the
 * text behind it (issue #33): the back arrow of the bars, the actions of the Hosts bar and the
 * toggle of the Apps bar. Where there is no background, the plain page, there is no halo. The halo
 * round a stroke is the one that the notes choose, which `contrast.py halo --icons` keeps over 3:1.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BackdropIconTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()

    // Where the halo is drawn

    @Test
    fun `the halo is drawn over a background of every style that draws one`() {
        show(BackgroundStyle.AuroraCurtains) { BackdropIcon(Stem, "Stem") }

        for (next in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
            change(style = next)

            assertWithMessage("over $next").that(haloOf("Stem")).isTrue()
        }
    }

    @Test
    fun `the halo is drawn where the glass is Off or High contrast, for the background is still behind`() {
        show(BackgroundStyle.AuroraCurtains, BorealisLook(transparency = Transparency.Off)) {
            BackdropIcon(Stem, "Stem")
        }
        assertThat(haloOf("Stem")).isTrue()

        change(look = BorealisLook(highContrast = true))
        assertThat(haloOf("Stem")).isTrue()
    }

    @Test
    fun `no halo is drawn on the plain page`() {
        show(BackgroundStyle.Off) { BackdropIcon(Stem, "Stem") }

        assertThat(haloOf("Stem")).isFalse()
    }

    @Test
    fun `no halo is drawn outside a host, which has no background`() {
        composeRule.setContent { BorealisTheme(FormFactor.Tv) { BackdropIcon(Stem, "Stem") } }

        assertThat(haloOf("Stem")).isFalse()
    }

    // The arrow of the bars

    @Test
    fun `the back arrow has the halo over a background and none on the plain page`() {
        show(BackgroundStyle.NorthernLights) { BorealisBackButton(onClick = {}, "Back") }
        assertThat(haloOf("Back")).isTrue()

        change(style = BackgroundStyle.Off)
        assertThat(haloOf("Back")).isFalse()
    }

    @Test
    fun `the arrow of the phone bar is on the background`() {
        show(BackgroundStyle.Starfield) {
            BorealisPhoneBar(onBack = {}, backDescription = "Back") { BackdropText("Hosts") }
        }

        assertThat(haloOf("Back")).isTrue()
        // The title, which is the other thing on the bar that stands on the background.
        composeRule
            .onAllNodes(
                SemanticsMatcher.expectValue(BackdropTextHalo, true),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    // The icon

    @Test
    fun `the icon is the content colour by default`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem") }
        }

        assertThat(pixelAtStem(composeRule.frame(HOST)).near(colors.ink)).isTrue()
    }

    @Test
    fun `the icon is the tint it is given`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem", tint = Color.Yellow) }
        }

        assertThat(pixelAtStem(composeRule.frame(HOST)).near(Color.Yellow)).isTrue()
    }

    // The drawing

    @Test
    fun `the halo darkens the bright background round the icon and the icon is drawn over it`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem") }
        }

        val pixels = composeRule.frame(HOST)

        assertThat(pixels.count { it.luminance() < DARK }).isGreaterThan(MIN_HALO_PIXELS)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_ICON_PIXELS)
    }

    @Test
    fun `no halo is drawn on the plain page, so nothing round the icon is dark`() {
        show(BackgroundStyle.Off, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem") }
        }

        val pixels = composeRule.frame(HOST)

        assertThat(pixels.count { it.luminance() < DARK }).isEqualTo(0)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_ICON_PIXELS)
    }

    @Test
    fun `the halo round a stroke has the alpha that the notes choose, under the icon`() {
        val choice = NotesChoice.read()
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem") }
        }
        val pixels = composeRule.frame(HOST)

        for (distance in DISTANCES_PX) {
            val at = STEM_LEFT_PX - distance
            val seen = alphaOver(pixels[at, STEM_ROW_PX].green)
            assertWithMessage("alpha at $distance px from the stroke")
                .that(seen)
                .isWithin(ALPHA_TOLERANCE)
                .of(expectedAlpha(choice, distance - 0.5f))
        }
        // The halo is behind the icon: the middle of the stroke is the ink, and far from it is
        // white.
        assertThat(pixels[STEM_LEFT_PX + STEM_WIDTH_PX / 2, STEM_ROW_PX].near(colors.ink)).isTrue()
        assertThat(pixels[STEM_LEFT_PX - FAR_PX, STEM_ROW_PX].near(Color.White)).isTrue()
    }

    @Test
    fun `the halo is not cut at the bounds of the icon`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(PAD.dp)) { BackdropIcon(Stem, "Stem") }
        }
        val pixels = composeRule.frame(HOST)

        // The stroke is 3 dp from the left edge of the icon, so 12 px of halo are 6 px outside it.
        val outside = ICON_LEFT_PX - 1
        assertThat(STEM_LEFT_PX - DISTANCES_PX.last()).isLessThan(ICON_LEFT_PX)
        assertThat(pixels[outside, STEM_ROW_PX].luminance()).isLessThan(DARK)
    }

    // Helpers

    private var style by mutableStateOf(BackgroundStyle.Off)
    private var look by mutableStateOf(BorealisLook())

    /** [content] in a host of [style], over white, under [look]. */
    private fun show(
        style: BackgroundStyle,
        look: BorealisLook = BorealisLook(),
        host: String? = null,
        content: @Composable () -> Unit,
    ) {
        this.style = style
        this.look = look
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, this.look) {
                GlassHostOver(
                    this.style,
                    background = { Box(Modifier.fillMaxSize().background(Color.White)) },
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
    private fun change(style: BackgroundStyle = this.style, look: BorealisLook = this.look) {
        composeRule.runOnIdle {
            this.style = style
            this.look = look
        }
        composeRule.waitForIdle()
    }

    private fun pixelAtStem(pixels: PixelMap) =
        pixels[STEM_LEFT_PX + STEM_WIDTH_PX / 2, STEM_ROW_PX]

    /** The alpha of `page` that makes [green] of white. */
    private fun alphaOver(green: Float) = (1f - green) / (1f - colors.page.green)

    /**
     * The alpha that a halo of [choice] has at [distance] px outside the edge of a stroke
     * [STEM_WIDTH_PX] px wide: the bar of the stroke widened by the halo on both sides, blurred by
     * the Gaussian of the blur, at the opacity. It is worked out along a line across the stroke,
     * far from its ends.
     */
    private fun expectedAlpha(choice: NotesChoice, distance: Float): Float {
        val stroke = choice.strokeDp * PIXELS_PER_DP
        val sigma = BLUR_SIGMA_PER_PX * choice.blurDp * PIXELS_PER_DP + BLUR_SIGMA_BASE
        var covered = 0.0
        var step = -(stroke + 6 * sigma)
        while (step <= STEM_WIDTH_PX + stroke + 6 * sigma) {
            if (step >= -stroke && step <= STEM_WIDTH_PX + stroke) {
                val offset = (step - (-distance)) / sigma
                covered +=
                    kotlin.math.exp(-offset * offset / 2) /
                        (sigma * kotlin.math.sqrt(2 * Math.PI)) * STEP
            }
            step += STEP
        }
        return (covered * choice.opacity / 100).toFloat()
    }

    private fun haloOf(description: String): Boolean {
        val node = composeRule.onNodeWithContentDescription(description, useUnmergedTree = true)
        node.assert(SemanticsMatcher.keyIsDefined(BackdropIconHalo))
        return node.fetchSemanticsNode().config[BackdropIconHalo]
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
        /** A stroke 3 dp from the left edge of an icon of 24 dp, 2 dp wide, with round caps. */
        val Stem: ImageVector by lazy {
            ImageVector.Builder(
                    name = "Stem",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .addPath(
                    pathData = PathParser().parsePathString("M4 3v18").toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                .build()
        }

        const val HOST = "backdrop-icon-host"
        const val HOST_WIDTH = 100
        const val HOST_HEIGHT = 80

        /** The tests run at xhdpi, where one dp is two pixels. */
        const val PIXELS_PER_DP = 2f
        const val PAD = 24
        const val ICON_LEFT_PX = 48

        /** The stroke is at x = 4 of 24 and 2 wide: from 3 dp to 5 dp of the icon, in pixels. */
        const val STEM_LEFT_PX = ICON_LEFT_PX + 6
        const val STEM_WIDTH_PX = 4

        /** The middle of the stroke, 12 dp down the icon. */
        const val STEM_ROW_PX = 48 + 24

        /** A pixel of the halo over white is `page` at 80 percent over it: well under this. */
        const val DARK = 0.5f
        const val MIN_HALO_PIXELS = 300
        const val MIN_ICON_PIXELS = 30
        const val NEAR = 0.03f

        /** Skia turns the radius of a blur in pixels into a sigma: 0.57735 * radius + 0.5. */
        const val BLUR_SIGMA_PER_PX = 0.57735
        const val BLUR_SIGMA_BASE = 0.5
        const val STEP = 0.05

        /** How far outside the stroke the halo is read, in pixels, and how far is far. */
        val DISTANCES_PX = listOf(2, 5, 8, 12)
        const val FAR_PX = 40
        const val ALPHA_TOLERANCE = 0.07f
    }
}
