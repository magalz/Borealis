package io.github.magalz.borealis.designsystem.background

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The drawn backgrounds keep the fixed Borealis violet whatever the accent and with High contrast
 * (ADR 0058, decision 5), so the glass contrast table, which is measured over them, stays valid.
 * The page colour is the only colour that they take from the theme.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AccentBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    private var colors by mutableStateOf(BorealisColors())
    private var style by mutableStateOf(BackgroundStyle.AuroraCurtains)

    private val drawn = listOf(BackgroundStyle.AuroraCurtains, BackgroundStyle.SoftGlow)

    /** The frame that the style [shown] draws in [theme], on its still frame. */
    private fun pixels(shown: BackgroundStyle, theme: BorealisColors): IntArray {
        colors = theme
        style = shown
        composeRule.waitForIdle()
        return composeRule.frame(BG).buffer
    }

    private fun showBackground() {
        composeRule.setContent {
            CompositionLocalProvider(LocalBorealisColors provides colors) {
                DrawnBackground(style, animated = false, Modifier.size(SIZE).testTag(BG))
            }
        }
    }

    @Test
    fun `a drawn background has the same pixels with the teal colours as with the tokens`() {
        showBackground()
        // The role sets of teal and white, as the theme gives them to the content.
        val teal = BorealisColors(accent = Color(0xFF4BECD0), accentStrong = Color(0xFF06AF97))
        val white = BorealisColors(accent = Color(0xFFEFF3FF), accentStrong = Color(0xFFB4B8C4))

        for (shown in drawn) {
            val violet = pixels(shown, BorealisColors())
            assertWithMessage("$shown with teal").that(pixels(shown, teal)).isEqualTo(violet)
            assertWithMessage("$shown with white").that(pixels(shown, white)).isEqualTo(violet)
        }
    }

    @Test
    fun `a drawn background has the same pixels with high contrast as the violet of the tokens`() {
        showBackground()
        val high = BorealisColors().withHighContrast(AccentChoice.Violet)

        for (shown in drawn) {
            assertWithMessage("$shown")
                .that(pixels(shown, high))
                .isEqualTo(pixels(shown, BorealisColors()))
        }
    }

    @Test
    fun `a drawn background has the same pixels under every accent of a look, with or without high contrast`() {
        showBackground()

        for (shown in drawn) {
            val violet = pixels(shown, BorealisColors())
            for (accent in AccentChoice.entries) {
                for (high in listOf(false, true)) {
                    val theme = borealisColors(BorealisLook(accent = accent, highContrast = high))

                    assertWithMessage("$shown $accent high contrast $high")
                        .that(pixels(shown, theme))
                        .isEqualTo(violet)
                }
            }
        }
    }

    @Test
    fun `the page colour of the theme is still the colour under the light`() {
        // The comparisons above can fail: a theme with another page draws other pixels.
        showBackground()

        for (shown in drawn) {
            val plain = pixels(shown, BorealisColors())
            val other = pixels(shown, BorealisColors(page = Color(0xFF301010)))

            assertWithMessage("$shown").that(other).isNotEqualTo(plain)
            assertThat(other.size).isEqualTo(plain.size)
        }
    }

    private companion object {
        const val BG = "background"

        /** Small, so that a test holds the pixels of a dozen frames. */
        val SIZE = DpSize(160.dp, 280.dp)
    }
}
