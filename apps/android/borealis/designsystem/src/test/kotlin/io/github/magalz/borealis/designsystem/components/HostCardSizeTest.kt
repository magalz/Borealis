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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A host card is the same size whatever it shows: online, offline or not yet paired. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HostCardSizeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `on a TV every variant is as high as the token`() {
        showAll(FormFactor.Tv, 260.dp)

        Variant.entries.forEach {
            assertThat(height(it)).isEqualTo(BorealisTokens.Size.HostCard.HeightTv)
        }
    }

    @Test
    fun `on a handheld every variant is as high as on a TV`() {
        showAll(FormFactor.Handheld, 260.dp)

        Variant.entries.forEach {
            assertThat(height(it)).isEqualTo(BorealisTokens.Size.HostCard.HeightTv)
        }
    }

    @Test
    fun `on a phone every card is as high as its content`() {
        showAll(FormFactor.Phone, 328.dp)

        val online = height(Variant.Online)
        val offline = height(Variant.Offline)
        val bare = height(Variant.Bare)
        // The fullest card is the tallest, and a card with less content is shorter, as it was
        // before the height token.
        assertThat(offline).isLessThan(online)
        assertThat(bare).isLessThan(offline)
        assertThat(online).isLessThan(BorealisTokens.Size.HostCard.HeightTv)
    }

    @Test
    fun `the token is the height of the fullest card, which has no button to make room for`() {
        composeRule.setContent { FullestCard(FormFactor.Tv, 260.dp) }
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(FULLEST).getUnclippedBoundsInRoot()
        val runningApp =
            composeRule
                .onNodeWithText("Desktop running", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        // The last line of the fullest card sits the card padding above its bottom edge.
        assertThat(card.bottom - card.top).isEqualTo(BorealisTokens.Size.HostCard.HeightTv)
        assertThat(card.bottom - runningApp.bottom).isEqualTo(18.dp)
    }

    @Test
    fun `on a TV the text of a card with less content stays at the top`() {
        showAll(FormFactor.Tv, 260.dp)

        val card = composeRule.onNodeWithTag(Variant.Offline.name).getUnclippedBoundsInRoot()
        val name =
            composeRule
                .onNodeWithText("Living Room PC", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        assertThat(name.top - card.top).isEqualTo(18.dp)
    }

    @Test
    fun `the tallest variant has room for its action above the card padding`() {
        showAll(FormFactor.Tv, 260.dp)

        assertThat(actionGap(Variant.Online, "Open")).isAtLeast(18.dp)
    }

    @Test
    fun `on a TV the action sits at the bottom edge in every variant that has one`() {
        showAll(FormFactor.Tv, 260.dp)

        val online = actionGap(Variant.Online, "Open")
        assertThat(actionGap(Variant.Offline, "Wake")).isEqualTo(online)
        assertThat(actionGap(Variant.Unpaired, "Pair")).isEqualTo(online)
    }

    private fun showAll(formFactor: FormFactor, width: Dp) {
        composeRule.setContent { Specimen(formFactor, width) }
        composeRule.waitForIdle()
    }

    private fun height(variant: Variant): Dp {
        val card = composeRule.onNodeWithTag(variant.name).getUnclippedBoundsInRoot()
        return card.bottom - card.top
    }

    private fun actionGap(variant: Variant, action: String): Dp {
        val card = composeRule.onNodeWithTag(variant.name).getUnclippedBoundsInRoot()
        val button =
            composeRule.onNodeWithText(action, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return card.bottom - button.bottom
    }

    @Composable
    private fun FullestCard(formFactor: FormFactor, width: Dp) {
        BorealisTheme(formFactor) {
            HostCard(
                name = "Desk PC",
                status = DotStatus.Online,
                statusText = "Online",
                detail = "LuminalShine 26.09.1 · 192.168.1.20",
                tags = listOf("HEVC 10-bit", "HDR", "4:4:4"),
                runningApp = "Desktop running",
                onClick = {},
                modifier = Modifier.width(width).testTag(FULLEST),
            )
        }
    }

    private companion object {
        const val FULLEST = "fullest"
    }

    private enum class Variant {
        Online,
        Offline,
        Unpaired,
        Bare,
    }

    @Composable
    private fun Specimen(formFactor: FormFactor, width: Dp) {
        BorealisTheme(formFactor) {
            Column(
                Modifier.width(width).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    detail = "LuminalShine 26.09.1 · 192.168.1.20",
                    tags = listOf("HEVC 10-bit", "HDR", "4:4:4"),
                    runningApp = "Desktop running",
                    action = HostCardAction("Open") {},
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Online.name),
                )
                HostCard(
                    name = "Living Room PC",
                    status = DotStatus.Offline,
                    statusText = "Offline",
                    detail = "Last seen 2 days ago · 192.168.1.31",
                    action = HostCardAction("Wake") {},
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Offline.name),
                )
                HostCard(
                    name = "Studio",
                    status = DotStatus.Attention,
                    statusText = "Not paired",
                    detail = "LuminalShine 26.09.1 · found on this network",
                    action = HostCardAction("Pair") {},
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Unpaired.name),
                )
                HostCard(
                    name = "Spare",
                    status = DotStatus.Offline,
                    statusText = "Unknown",
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Bare.name),
                )
            }
        }
    }
}
