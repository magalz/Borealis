package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.cardFitCases
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The host card in the 2 columns that Compact and Dense give a phone of 360dp: 158dp wide, which is
 * 130dp of text in Compact and 138dp in Dense. At the largest text, 130 percent over a system font
 * scale of 1.3, in every font at Normal, no text of the card runs past its width, is cut off or
 * ends in an ellipsis. At Wider the denser layouts keep the column of Comfortable, so there is no
 * run of it here: the card of 158dp at Wider is in `HostCardInkEdgeTest`, where the name "Living
 * Room PC" in Andika, the widest case, has a line that Android wraps 2.5 px wider than the box and
 * that the letter spacing at its end does not cover.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HostCardPhoneFitTest(case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    private val look =
        BorealisLook(
            font = case.font,
            textSpacing = case.spacing,
            textSize = TextSize.Largest,
            cardDensity = case.density,
        )

    @Test
    fun `no text of a host card in two columns is cut off`() {
        composeRule.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, SYSTEM_SCALE)) {
                BorealisTheme(FormFactor.Phone, look) {
                    Column(
                        Modifier.width(CARD_WIDTH).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Cards()
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    @androidx.compose.runtime.Composable
    private fun Cards() {
        HostCard(
            name = "Desk PC",
            status = DotStatus.Online,
            statusText = "Online",
            detail = "192.168.1.20",
            tags = listOf("HEVC 10-bit", "HDR"),
            runningApp = "Desktop",
            onClick = {},
        )
        HostCard(
            name = "Living Room PC",
            status = DotStatus.Offline,
            statusText = "Offline",
            detail = "192.168.1.31",
            onClick = {},
        )
        HostCard(
            name = "Studio",
            status = DotStatus.Attention,
            statusText = "Not paired",
            detail = "Found on this network",
            onClick = {},
        )
    }

    companion object {
        private val CARD_WIDTH = 158.dp
        private const val SYSTEM_SCALE = 1.3f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> =
            cardFitCases().filter {
                it.density != CardDensity.Comfortable && it.spacing != TextSpacing.Wider
            }
    }
}
