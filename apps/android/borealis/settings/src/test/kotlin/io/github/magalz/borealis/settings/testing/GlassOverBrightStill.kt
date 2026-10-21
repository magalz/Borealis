package io.github.magalz.borealis.settings.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHost
import io.github.magalz.borealis.designsystem.testing.noAnimations
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tests run at xhdpi, where one dp is two pixels. */
private const val PIXELS_PER_DP = 2

/**
 * [content] in a glass host of [style] under [look], as the shell of the Library draws a screen.
 * The screens wear a theme of their own and keep the look.
 */
@Composable
fun InGlassHost(
    formFactor: FormFactor,
    look: BorealisLook = BorealisLook(),
    style: BackgroundStyle = BackgroundStyle.FlowingLight,
    content: @Composable () -> Unit,
) {
    BorealisTheme(formFactor, look) { GlassHost(style, content = content) }
}

/**
 * The colour of the screen at [xDp] across and level with the middle of the node that has [text]: a
 * point beside it, in the padding of the surface that it is on, where nothing is drawn.
 */
fun ComposeTestRule.colorBeside(text: String, xDp: Float): Color {
    val bounds = onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val middle = (bounds.top.value + bounds.bottom.value) / 2
    return onRoot()
        .captureToImage()
        .toPixelMap()[(xDp * PIXELS_PER_DP).toInt(), (middle * PIXELS_PER_DP).toInt()]
}

/**
 * Where to read a surface: beside the node with [title], [x] dp across, in the padding of the
 * surface that it is on; and [outside] dp across, at the same height, where the background shows
 * bare beside that surface.
 */
data class Probe(val title: String, val x: Float, val outside: Float)

/**
 * Reads the surfaces of a screen over the still of Flowing light, which is what a glass host draws
 * with no animation. The colour of each of the [probes] is compared with the background itself,
 * read beside the surface. A colour's distance is how far it is from the `surface` colour, in the
 * sum of its three channels. A glass of tint t over a background shows (1 - t) of the distance of
 * the background: Standard is 40 percent, so about 0.6 of it; Reduced is 85 percent, so about 0.15;
 * and a solid surface is the `surface` colour, which is no distance at all.
 */
class GlassProbes(private val rule: ComposeTestRule, private val probes: List<Probe>) {
    private val surface = BorealisTokens.Color.Surface

    private fun distance(colour: Color): Float =
        abs(colour.red - surface.red) +
            abs(colour.green - surface.green) +
            abs(colour.blue - surface.blue)

    private fun fractions(): List<Float> = probes.map { (title, x, outside) ->
        distance(rule.colorBeside(title, x)) / distance(rule.colorBeside(title, outside))
    }

    /** Each probe is the surface under the Standard tint: about 0.6 of the background. */
    fun assertStandard() =
        fractions().forEach { assertThat(it).isIn(Range.closed(STANDARD_MIN, STANDARD_MAX)) }

    /** Each probe is under the Reduced tint: about 0.15 of the background. */
    fun assertReduced() =
        fractions().forEach { assertThat(it).isIn(Range.closed(REDUCED_MIN, REDUCED_MAX)) }

    /** Each probe is the surface colour. */
    fun assertSolid() = fractions().forEach { assertThat(it).isLessThan(SOLID) }

    private companion object {
        /** Less than this is the surface colour. */
        const val SOLID = 0.05f

        /** The Standard glass is 0.6 of the background, with a little room for the blur. */
        const val STANDARD_MIN = 0.5f
        const val STANDARD_MAX = 0.7f

        /**
         * The Reduced glass is 0.15 of the background; what is read at 8 bits is 0.115 to 0.134, so
         * the floor leaves room for a step of a channel (about 0.013) at the other end of a screen.
         */
        const val REDUCED_MIN = 0.08f
        const val REDUCED_MAX = 0.2f
    }
}

/**
 * The cases of [GlassProbes] for a screen that needs no more than a compose rule: Standard shows
 * the background through the surfaces of the screen, Reduced shows it less, and Off, High contrast
 * and the plain page are the `surface` colour.
 */
@RunWith(AndroidJUnit4::class)
abstract class FlowingLightGlassCases {
    @get:Rule val composeRule = createComposeRule()

    /** The title beside which the surface is read, and how far across, in dp. */
    protected abstract val probes: List<Probe>

    /** Shows the screen in a glass host of [style] under [look]. */
    protected abstract fun showScreen(look: BorealisLook, style: BackgroundStyle)

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private fun probes() = GlassProbes(composeRule, probes)

    @Test
    fun `Standard shows the background through the surface`() {
        showScreen(BorealisLook(), BackgroundStyle.FlowingLight)

        probes().assertStandard()
    }

    @Test
    fun `Reduced shows the background through less than Standard`() {
        showScreen(BorealisLook(transparency = Transparency.Reduced), BackgroundStyle.FlowingLight)

        probes().assertReduced()
    }

    @Test
    fun `Off is the surface colour`() {
        showScreen(BorealisLook(transparency = Transparency.Off), BackgroundStyle.FlowingLight)

        probes().assertSolid()
    }

    @Test
    fun `High contrast is the surface colour`() {
        showScreen(BorealisLook(highContrast = true), BackgroundStyle.FlowingLight)

        probes().assertSolid()
    }

    @Test
    fun `the plain page is the surface colour`() {
        showScreen(BorealisLook(), BackgroundStyle.Off)

        probes().assertSolid()
    }
}
