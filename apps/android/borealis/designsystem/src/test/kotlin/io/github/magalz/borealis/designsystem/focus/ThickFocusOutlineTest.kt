package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.backdropLuminance
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.glassSpec
import io.github.magalz.borealis.designsystem.glass.luminance
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.contrastRatio
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The thicker focus outline (ADR 0057, decision 11): 5dp instead of 3dp, 2dp outside the element as
 * before, in the ink colour instead of accent-soft. It is drawn with the Thicker focus setting and
 * always with high contrast. Positions are in dp from the top-left corner of a frame that holds a
 * 100dp square 24dp in (xhdpi: 2 pixels per dp).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ThickFocusOutlineTest {
    @get:Rule val composeRule = createComposeRule()
    private val focus = FocusRequester()
    private val elsewhere = FocusRequester()

    @Test
    fun `with the thicker focus the outline is 5dp wide, starting 2dp outside the element`() {
        composeRule.setContentInKeyboardMode { Specimen(BorealisLook(thickFocus = true)) }

        focusTheSquare()

        // Between 2dp and 7dp outside: the 3dp outline would have ended at 5dp.
        assertPixel(x = 74f, y = 24f - 2.5f, THICK)
        assertPixel(x = 74f, y = 24f - 6f, THICK)
        assertPixel(x = 74f, y = 24f + 100f + 6f, THICK)
        assertPixel(x = 24f - 6f, y = 74f, THICK)
        assertPixel(x = 24f + 100f + 6f, y = 74f, THICK)
    }

    @Test
    fun `the thicker outline keeps the 2dp gap and ends 7dp out`() {
        composeRule.setContentInKeyboardMode { Specimen(BorealisLook(thickFocus = true)) }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 1f, PAGE)
        assertPixel(x = 74f, y = 24f - 7.5f, PAGE)
        assertPixel(x = 74f, y = 24f + 100f + 7.5f, PAGE)
    }

    @Test
    fun `the thicker outline is lighter than the accent-soft outline`() {
        val colors = BorealisColors()

        assertThat(luminance(focusOutlineColor(colors, thick = true)))
            .isGreaterThan(luminance(focusOutlineColor(colors, thick = false)))
    }

    @Test
    fun `without the setting the outline is 3dp wide in accent-soft`() {
        composeRule.setContentInKeyboardMode { Specimen(BorealisLook()) }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 3.5f, THIN)
        assertPixel(x = 74f, y = 24f - 6f, PAGE)
    }

    @Test
    fun `high contrast draws the thicker outline in its own ink`() {
        composeRule.setContentInKeyboardMode { Specimen(BorealisLook(highContrast = true)) }

        focusTheSquare()

        assertPixel(x = 74f, y = 24f - 6f, BorealisTokens.HighContrast.Ink)
        assertPixel(x = 74f, y = 24f - 7.5f, PAGE)
    }

    @Test
    fun `the setting changes the outline of an element that has focus`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContentInKeyboardMode { Specimen(look) }
        focusTheSquare()
        assertPixel(x = 74f, y = 24f - 6f, PAGE)

        look = BorealisLook(thickFocus = true)
        composeRule.waitForIdle()

        assertPixel(x = 74f, y = 24f - 6f, THICK)

        look = BorealisLook()
        composeRule.waitForIdle()

        assertPixel(x = 74f, y = 24f - 6f, PAGE)
    }

    @Test
    fun `the thicker outline keeps 3 to 1 on the page and the surface`() {
        val outline = focusOutlineColor(BorealisColors(), thick = true)

        assertThat(contrastRatio(outline, BorealisColors().page)).isAtLeast(3.0)
        assertThat(contrastRatio(outline, BorealisColors().surface)).isAtLeast(3.0)
    }

    @Test
    fun `the thicker outline keeps 3 to 1 on the brightest backdrop of every glass`() {
        val outline = focusOutlineColor(BorealisColors(), thick = true)
        val tints = listOf(30, 40, 50, 60, 70)
        val blurs = listOf(8, 16, 24, 32, 48)
        val looks =
            tints.flatMap { tint ->
                blurs.map { blur -> BorealisLook(glassTintPercent = tint, glassBlurDp = blur) }
            } + BorealisLook(transparency = Transparency.Reduced)
        val brightest = mutableListOf<Pair<String, Double>>()

        for (style in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
            for (look in looks) {
                for (sdk in listOf(30, 34)) {
                    val spec = glassSpec(look, style, sdk)
                    val backdrop = backdropLuminance(style, spec.blurDp, spec.tintPercent)
                    brightest += "$style ${spec.tintPercent}% ${spec.blurDp}dp" to backdrop
                }
            }
        }

        for ((glass, backdrop) in brightest) {
            assertWithMessage("outline on $glass").that(contrast(outline, backdrop)).isAtLeast(3.0)
        }
    }

    @Composable
    private fun Specimen(look: BorealisLook) {
        BorealisTheme(look = look) {
            Column {
                Box(Modifier.testTag(FRAME).background(PAGE).padding(24.dp)) {
                    Box(
                        Modifier.size(100.dp)
                            .borealisFocusIndication(RoundedCornerShape(8.dp))
                            .background(FILL, RoundedCornerShape(8.dp))
                            .focusRequester(focus)
                            .focusable()
                    )
                }
                Box(Modifier.size(10.dp).focusRequester(elsewhere).focusable())
            }
        }
    }

    private fun focusTheSquare() {
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
    }

    private fun assertPixel(x: Float, y: Float, expected: Color) =
        composeRule.onNodeWithTag(FRAME).assertPixel(x, y, expected)

    private companion object {
        const val FRAME = "frame"
        val PAGE = BorealisTokens.Color.Page
        val FILL = BorealisTokens.Color.SurfaceRaised
        val THIN = BorealisTokens.Color.AccentSoft
        val THICK = BorealisTokens.Color.Ink
    }
}
