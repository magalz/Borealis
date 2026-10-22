package io.github.magalz.borealis.stream.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
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

    @Test
    fun `an overlay that is too tall for its room shrinks to it and nothing is cut`() {
        spacing = TextSpacing.Wider
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                Box(Modifier.width(300.dp).height(150.dp)) {
                    PerfOverlay(
                        OverlayMode.DETAILED,
                        aPerf(),
                        aSessionInfo,
                        Modifier.testTag(PERF_OVERLAY_TAG),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag(PERF_OVERLAY_TAG).getUnclippedBoundsInRoot()
        assertThat(bounds.bottom - bounds.top).isAtMost(150.dp)
        assertThat(bounds.bottom - bounds.top).isAtLeast(140.dp)
        composeRule.assertNoClippedText()
    }

    @Test
    fun `an overlay that has the room is not shrunk`() {
        spacing = TextSpacing.Wider
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(textSpacing = spacing)) {
                Box(Modifier.width(300.dp).height(1000.dp)) {
                    PerfOverlay(OverlayMode.COMPACT, aPerf(), aSessionInfo)
                }
            }
        }
        composeRule.waitForIdle()

        val layout = composeRule.onNodeWithText(COMPACT, useUnmergedTree = true).textLayout()
        val bounds =
            composeRule.onNodeWithText(COMPACT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val density = layout.layoutInput.density.density
        assertThat((bounds.right - bounds.left).value)
            .isWithin(0.5f)
            .of(layout.size.width / density)
    }

    private companion object {
        const val COMPACT = "60 fps · host 2.0 ms · net 12 ms · HEVC"
    }
}
