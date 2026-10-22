package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The host card on a TV in every font (ADR 0058, decision 2). The height token is the least that a
 * card has, so a font that needs more room makes the card grow: the fullest card is never cut, its
 * last line keeps the card padding under it, and no text runs past its width or ends in an
 * ellipsis. [HostCardSizeTest] keeps the card at the height of the token in IBM Plex. The cards at
 * the largest text size are in the fit tests of Hosts.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HostCardFontFitTest(case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(font = case.font, textSpacing = case.spacing)

    @Test
    fun `the fullest card grows to hold its text and keeps its padding`() {
        show(look)

        val card = composeRule.onNodeWithTag(FULLEST).getUnclippedBoundsInRoot()
        val runningApp =
            composeRule
                .onNodeWithText("Desktop running", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        assertThat(card.bottom - card.top).isAtLeast(BorealisTokens.Size.HostCard.HeightTv)
        // The card padding and the 2dp padding of the chip are all that is under the last line.
        assertThat(card.bottom - runningApp.bottom).isAtLeast(20.dp)
    }

    @Test
    fun `no card has a text that is cut off`() {
        show(look)

        composeRule.assertNoClippedText()
    }

    private fun show(look: BorealisLook) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) {
                Column(
                    Modifier.width(260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Cards()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Cards() {
        HostCard(
            name = "Desk PC",
            status = DotStatus.Online,
            statusText = "Online",
            detail = "LuminalShine 26.09.1 · 192.168.1.20",
            tags = listOf("HEVC 10-bit", "HDR", "4:4:4"),
            runningApp = "Desktop running",
            onClick = {},
            modifier = Modifier.testTag(FULLEST),
        )
        HostCard(
            name = "Living Room PC",
            status = DotStatus.Offline,
            statusText = "Offline",
            detail = "Last seen 2 days ago · 192.168.1.31",
            onClick = {},
        )
        HostCard(
            name = "Studio",
            status = DotStatus.Attention,
            statusText = "Not paired",
            detail = "LuminalShine 26.09.1 · found on this network",
            onClick = {},
        )
    }

    companion object {
        private const val FULLEST = "fullest"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> = fitCases()
    }
}
