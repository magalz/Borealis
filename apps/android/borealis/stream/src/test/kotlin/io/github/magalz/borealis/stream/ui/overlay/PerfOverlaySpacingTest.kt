package io.github.magalz.borealis.stream.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.letterSpacingEm
import io.github.magalz.borealis.designsystem.testing.lineHeightEm
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The performance overlay carries the Text spacing of the look (ADR 0058, decision 4): its lines
 * are in the font of the codes, built from its size and family, so the level is given on top. At
 * Normal it is drawn as it always was.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class PerfOverlaySpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private var spacing by mutableStateOf(TextSpacing.Normal)

    private fun show() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                PerfOverlay(OverlayMode.COMPACT, aPerf(), aSessionInfo)
            }
        }
        composeRule.waitForIdle()
    }

    private fun line(): TextStyle =
        composeRule.onNodeWithText(COMPACT, useUnmergedTree = true).textLayout().layoutInput.style

    @Test
    fun `at Normal the overlay has no spacing of its own`() {
        show()

        assertThat(line().letterSpacing.isSpecified).isFalse()
        assertThat(line().lineHeight.isSpecified).isFalse()
    }

    @Test
    fun `at Wider the overlay has the letter spacing and the line height of the level`() {
        spacing = TextSpacing.Wider
        show()

        assertThat(line().letterSpacingEm()).isAtLeast(TextSpacing.Wider.letterEm)
        assertThat(line().lineHeightEm() ?: 0f).isAtLeast(TextSpacing.Wider.lineHeightEm)
    }

    @Test
    fun `a change of the setting reaches the overlay`() {
        show()

        spacing = TextSpacing.Wide
        composeRule.waitForIdle()
        assertThat(line().letterSpacingEm()).isAtLeast(TextSpacing.Wide.letterEm)
    }

    private companion object {
        const val COMPACT = "60 fps · host 2.0 ms · net 12 ms · HEVC"
    }
}
