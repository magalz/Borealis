package io.github.magalz.borealis.settings.testing

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHost
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
 * Takes the animation from the system, so that a host of a video style shows the still of its first
 * frame, which is the same at every run. The still of Flowing light is bright: a tint of the dark
 * `surface` colour over it is far from `surface`, and a solid surface is exactly it.
 */
fun noAnimations() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
}

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

/** The colour of the text that [text] is in the unmerged tree of the screen. */
fun ComposeTestRule.textColorOf(text: String): Color {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(results)
    return results.single().layoutInput.style.color
}

/**
 * Reads the surfaces of a screen over the still of Flowing light, which is what a glass host draws
 * with no animation. Each of the [probes] is a title and how far across, in dp: the colour is read
 * beside the title, in the padding of the surface that it is on, and compared with the background
 * itself at the same height in the margin of the window. A colour's distance is how far it is from
 * the `surface` colour, in the sum of its three channels. Over a background that shows through,
 * Standard is about 0.6 of the distance of the margin and Reduced about 0.15 of it, and a solid
 * surface is the `surface` colour, which is no distance at all.
 */
class GlassProbes(
    private val rule: ComposeTestRule,
    private val probes: List<Pair<String, Float>>,
) {
    private val surface = BorealisTokens.Color.Surface

    private fun distance(colour: Color): Float =
        abs(colour.red - surface.red) +
            abs(colour.green - surface.green) +
            abs(colour.blue - surface.blue)

    private fun fractions(): List<Float> = probes.map { (title, x) ->
        distance(rule.colorBeside(title, x)) / distance(rule.colorBeside(title, MARGIN_X))
    }

    /** Each probe is the surface under the Standard tint, which shows the background through. */
    fun assertStandard() =
        fractions().forEach { assertThat(it).isIn(Range.open(SOLID, STANDARD_AT_MOST)) }

    /** Each probe is under the Reduced tint, which shows the background through less. */
    fun assertReduced() =
        fractions().forEach { assertThat(it).isIn(Range.open(SOLID, REDUCED_AT_MOST)) }

    /** Each probe is the surface colour. */
    fun assertSolid() = fractions().forEach { assertThat(it).isLessThan(SOLID) }

    private companion object {
        /** In the margin, which the surfaces leave to the background. */
        const val MARGIN_X = 1f

        /** Less than this is the surface colour, and more is a surface that shows through. */
        const val SOLID = 0.05f

        /** The Standard glass is 0.6 of the margin; a screen with no surface at all is 1. */
        const val STANDARD_AT_MOST = 0.8f

        /** The Reduced glass is 0.15 of the margin, and Standard is 0.6. */
        const val REDUCED_AT_MOST = 0.3f
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
    protected abstract val probes: List<Pair<String, Float>>

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
