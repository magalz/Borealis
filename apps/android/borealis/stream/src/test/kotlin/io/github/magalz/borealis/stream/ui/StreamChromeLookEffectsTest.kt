package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.Edge
import io.github.magalz.borealis.designsystem.testing.colourOutside
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.setContentInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The stream chrome follows the visual choices of the look that the stream started with (ADR 0057,
 * decision 11): the colours of high contrast and of the palette, the text size and the thicker
 * focus outline, as the Library does.
 */
@RunWith(AndroidJUnit4::class)
class StreamChromeLookEffectsTest {
    @get:Rule val composeRule = createComposeRule()

    private var colors: BorealisColors? = null
    private var density: Density? = null

    @Composable
    private fun Probe() {
        colors = LocalBorealisColors.current
        density = LocalDensity.current
    }

    @Test
    @Config(qualifiers = TV)
    fun `the chrome has the colours of high contrast and of the palette`() {
        val look = BorealisLook(highContrast = true, colourVision = ColourVision.RedGreen)
        composeRule.setContent { StreamChromeTheme(look) { Probe() } }
        composeRule.waitForIdle()

        assertThat(colors).isEqualTo(borealisColors(look))
        assertThat(colors?.ink).isEqualTo(BorealisTokens.HighContrast.Ink)
        assertThat(colors?.success).isEqualTo(BorealisTokens.ColourVision.RedGreen.Success)
    }

    @Test
    @Config(qualifiers = TV, fontScale = 1.3f)
    fun `the chrome has the text size times the font scale of the system`() {
        composeRule.setContent {
            StreamChromeTheme(BorealisLook(textSize = TextSize.Largest)) { Probe() }
        }
        composeRule.waitForIdle()

        assertThat(density?.fontScale).isWithin(0.001f).of(1.3f * 1.3f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the focused row of the menu has the thicker outline with the setting`() {
        assertOutlineOfBackToStream(BorealisLook(thickFocus = true), BorealisColors().ink)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the focused row of the menu has the thicker outline in white with high contrast`() {
        assertOutlineOfBackToStream(
            BorealisLook(highContrast = true),
            BorealisTokens.HighContrast.Ink,
        )
    }

    private fun assertOutlineOfBackToStream(
        look: BorealisLook,
        expected: androidx.compose.ui.graphics.Color,
    ) {
        composeRule.setContentInKeyboardMode {
            StreamChromeTheme(look) {
                StreamingLayer(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(Panel.MENU, OverlayMode.OFF),
                    null,
                    RecordingIntents(),
                )
            }
        }
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithText("Back to stream")
        row.requestFocus()
        composeRule.waitForIdle()

        // 6.5dp outside the left edge, which is inside the 2dp to 7dp of the thicker outline; the
        // outline of the first row is whole, not cut off by the padding of the panel.
        val seen = composeRule.colourOutside(row, Edge.Left, 6.5f)
        assertWithMessage("6.5dp left of the focused row is $seen")
            .that(seen.isNear(expected))
            .isTrue()
    }
}
