package io.github.magalz.borealis.stream.ui.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.aStreaming
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The focus outline and its casing of a focused control, whole on every side, over the lightest
 * glass (a white frame). The content of the Quit popup and of the menu is on an offscreen layer,
 * which clips what it draws to its bounds, and an outline reaches outside its control. Read in the
 * pixels, on a TV and on a handheld, the devices that have a focus.
 *
 * The popup: the outline of Cancel, which is at the left and at the bottom of its content, keeps
 * 3:1 against its casing on all four sides, the normal one and the thicker one. The menu: with the
 * thicker outline, the casing under the last row is the whole 1.5dp, where the panel is taller than
 * its content, and at the left edge of the panel it reaches the edge.
 */
@RunWith(AndroidJUnit4::class)
class MenuGlassCasingTest {
    @get:Rule val composeRule = createComposeRule()

    private enum class Side {
        Left,
        Right,
        Top,
        Bottom,
    }

    private fun menuOver(
        screen: VideoBounds,
        formFactor: FormFactor,
        look: BorealisLook,
    ): GlassFixture {
        val source =
            FakeVideoSource(
                bounds = screen,
                frameColor = android.graphics.Color.WHITE,
                completing = true,
            )
        return GlassFixture(composeRule, source).apply {
            this.formFactor = formFactor
            this.look = look
            keyboardMode = true
            phase = aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)
        }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < NEAR

    private fun pixels(): PixelMap = composeRule.onRoot().captureToImage().toPixelMap()

    private fun reachPx() = (OUTLINE_REACH_DP * composeRule.density.density).toInt()

    /**
     * The pixel [step] away from the edge of [bounds] on [side], at the middle of that edge: step 0
     * is the first pixel outside it, and a negative step is inside it.
     */
    private fun PixelMap.beyond(bounds: Rect, side: Side, step: Int): Color {
        val x =
            when (side) {
                Side.Left -> bounds.left.roundToInt() - 1 - step
                Side.Right -> bounds.right.roundToInt() + step
                else -> bounds.center.x.roundToInt()
            }
        val y =
            when (side) {
                Side.Top -> bounds.top.roundToInt() - 1 - step
                Side.Bottom -> bounds.bottom.roundToInt() + step
                else -> bounds.center.y.roundToInt()
            }
        return this[x, y]
    }

    /** The steps out of [bounds] on [side] where the line has the colour of the outline. */
    private fun PixelMap.outlineSteps(bounds: Rect, side: Side, outline: Color): List<Int> =
        (0 until reachPx()).filter { beyond(bounds, side, it).near(outline) }

    /**
     * The ratio of the [outline] colour over the pixel two away from it on each side, along the
     * line out of [bounds] on [side]: what is between the outline and the control, and past it.
     */
    private fun ratiosAcross(bounds: Rect, side: Side, outline: Color): List<Double> {
        val pixels = pixels()
        val line = pixels.outlineSteps(bounds, side, outline)
        assertWithMessage("the outline is on the $side of the control").that(line).isNotEmpty()
        return listOf(line.first() - 2, line.last() + 2).map {
            contrastOver(outline, wcagLuminance(pixels.beyond(bounds, side, it)))
        }
    }

    private fun outlineColor(colors: BorealisColors, thick: Boolean) =
        if (thick) colors.ink else colors.accentSoft

    private fun cancelOutlineKeeps3To1(
        formFactor: FormFactor,
        thick: Boolean,
        screen: VideoBounds,
    ) {
        val look = BorealisLook(thickFocus = thick)
        val menu = menuOver(screen, formFactor, look)
        menu.show()
        menu.openQuitFromKeys()
        menu.run(SETTLE_MS)
        val outline = outlineColor(borealisColors(look), thick)
        val cancel = composeRule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot

        for (side in Side.entries) {
            for (ratio in ratiosAcross(cancel, side, outline)) {
                assertWithMessage("the outline of Cancel, on its $side")
                    .that(ratio)
                    .isAtLeast(FOCUS_RATIO)
            }
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the outline of Cancel keeps 3 to 1 over the lightest glass on every side`() =
        cancelOutlineKeeps3To1(FormFactor.Tv, thick = false, TV_SCREEN)

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the thicker outline of Cancel keeps 3 to 1 over the lightest glass`() =
        cancelOutlineKeeps3To1(FormFactor.Tv, thick = true, TV_SCREEN)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the outline of Cancel keeps 3 to 1 over the lightest glass`() =
        cancelOutlineKeeps3To1(FormFactor.Handheld, thick = false, HANDHELD_SCREEN)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the thicker outline of Cancel keeps 3 to 1 over the lightest glass`() =
        cancelOutlineKeeps3To1(FormFactor.Handheld, thick = true, HANDHELD_SCREEN)

    // The menu, with the thicker outline: it and its casing reach 8.5dp outside a row, and the
    // padding of the list is 8dp.

    private fun thickMenu(): BorealisColors {
        val look = BorealisLook(thickFocus = true)
        menuOver(TALL_TV_SCREEN, FormFactor.Tv, look).show()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        return borealisColors(look)
    }

    @Test
    @Config(qualifiers = TALL_TV)
    fun `the casing of the thicker outline is whole under the last row of the menu`() {
        val colors = thickMenu()
        composeRule.onNodeWithText("Quit Desktop").requestFocus()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithText("Quit Desktop").fetchSemanticsNode().boundsInRoot
        val pixels = pixels()
        val outline = pixels.outlineSteps(row, Side.Bottom, colors.ink)
        assertWithMessage("the outline is under the last row").that(outline).isNotEmpty()

        val casingPx = (CASING_DP * composeRule.density.density).roundToInt()
        for (step in 1..casingPx) {
            assertWithMessage("the casing, $step px past the outline")
                .that(pixels.beyond(row, Side.Bottom, outline.last() + step).near(colors.page))
                .isTrue()
        }
    }

    @Test
    @Config(qualifiers = TALL_TV)
    fun `the casing of the thicker outline reaches the left edge of the panel`() {
        val colors = thickMenu()
        val row = composeRule.onNodeWithText("Back to stream").fetchSemanticsNode().boundsInRoot
        val panelLeft =
            composeRule
                .onNode(hasScrollAction())
                .fetchSemanticsNode()
                .boundsInRoot
                .left
                .roundToInt()
        val pixels = pixels()
        val outline = pixels.outlineSteps(row, Side.Left, colors.ink)
        assertWithMessage("the outline is left of the first row").that(outline).isNotEmpty()

        // The panel clips at its own edge: the casing is whole up to it.
        var step = outline.last() + 1
        while (row.left.roundToInt() - 1 - step >= panelLeft) {
            assertWithMessage("the casing, $step px out of the row")
                .that(pixels.beyond(row, Side.Left, step).near(colors.page))
                .isTrue()
            step++
        }
        assertThat(step).isGreaterThan(outline.last() + 1)
    }

    private companion object {
        const val FOCUS_RATIO = 3.0
        const val OUTLINE_REACH_DP = 9
        const val CASING_DP = 1.5
        const val NEAR = 0.08f
        const val SETTLE_MS = 100L
        val TV_SCREEN = VideoBounds(0, 0, 1920, 1080)
        val HANDHELD_SCREEN = VideoBounds(0, 0, 1200, 720)

        // A screen taller than the menu, so that the panel has room under its last row.
        const val TALL_TV = "w960dp-h1500dp-television-xhdpi"
        val TALL_TV_SCREEN = VideoBounds(0, 0, 1920, 3000)
    }
}
