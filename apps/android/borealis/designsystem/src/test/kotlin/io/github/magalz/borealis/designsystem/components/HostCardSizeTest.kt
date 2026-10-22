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
import io.github.magalz.borealis.designsystem.testing.tvHostCardMinHeight
import io.github.magalz.borealis.designsystem.testing.tvHostCardWidth
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A host card on a TV is the same size whatever it shows: online, offline or not yet paired. On a
 * handheld and a phone it is as high as its content.
 */
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
    fun `on a handheld every card is as high as its content`() {
        showAll(FormFactor.Handheld, 260.dp)

        val online = height(Variant.Online)
        val offline = height(Variant.Offline)
        val bare = height(Variant.Bare)
        // As on a phone: the fullest card is the tallest, a card with less content is shorter, and
        // none is held to the TV height.
        assertThat(offline).isLessThan(online)
        assertThat(bare).isLessThan(offline)
        assertThat(online).isLessThan(BorealisTokens.Size.HostCard.HeightTv)
    }

    @Test
    fun `on a handheld a card ends where its content ends`() {
        composeRule.setContent { FullestCard(FormFactor.Handheld, 260.dp) }
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(FULLEST).getUnclippedBoundsInRoot()
        val runningApp =
            composeRule
                .onNodeWithText("Desktop running", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        // The card padding and the 2dp padding of the chip are all that is under the last line.
        assertThat((card.bottom - runningApp.bottom).value).isWithin(1f).of(20f)
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
    fun `in the Compact layout no card is lower than the token`() =
        assertNoCardLowerThanToken(CardDensity.Compact)

    @Test
    fun `in the Dense layout no card is lower than the token`() =
        assertNoCardLowerThanToken(CardDensity.Dense)

    /**
     * The cards of a denser layout are narrower, so the fullest one needs more than the token. The
     * token is still the least that any card has: the cards that need less keep it.
     */
    private fun assertNoCardLowerThanToken(density: CardDensity) {
        showAll(FormFactor.Tv, tvHostCardWidth(density), density)

        Variant.entries.forEach { assertThat(height(it)).isAtLeast(tvHostCardMinHeight(density)) }
        assertThat(height(Variant.Bare)).isEqualTo(tvHostCardMinHeight(density))
    }

    @Test fun `the fullest card fits within the token`() = assertFullestCardFits()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-tvdpi")
    fun `the fullest card fits within the token at tvdpi`() = assertFullestCardFits()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-hdpi")
    fun `the fullest card fits within the token at hdpi`() = assertFullestCardFits()

    private fun assertFullestCardFits() {
        composeRule.setContent { FullestCard(FormFactor.Tv, 260.dp) }
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(FULLEST).getUnclippedBoundsInRoot()
        val runningApp =
            composeRule
                .onNodeWithText("Desktop running", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        // The card is the token high (to the pixel, under 1dp at these densities) and the text of
        // its last line sits at least the card padding and the 2dp padding of its chip above the
        // bottom edge, so the rounding of each line does not make it taller than its neighbours.
        assertThat((card.bottom - card.top).value)
            .isWithin(1f)
            .of(BorealisTokens.Size.HostCard.HeightTv.value)
        assertThat(card.bottom - runningApp.bottom).isAtLeast(20.dp)
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

    private fun showAll(
        formFactor: FormFactor,
        width: Dp,
        density: CardDensity = CardDensity.Comfortable,
    ) {
        composeRule.setContent { Specimen(formFactor, width, density) }
        composeRule.waitForIdle()
    }

    private fun height(variant: Variant): Dp {
        val card = composeRule.onNodeWithTag(variant.name).getUnclippedBoundsInRoot()
        return card.bottom - card.top
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
    private fun Specimen(formFactor: FormFactor, width: Dp, density: CardDensity) {
        BorealisTheme(formFactor, BorealisLook(cardDensity = density)) {
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
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Online.name),
                )
                HostCard(
                    name = "Living Room PC",
                    status = DotStatus.Offline,
                    statusText = "Offline",
                    detail = "Last seen 2 days ago · 192.168.1.31",
                    onClick = {},
                    modifier = Modifier.testTag(Variant.Offline.name),
                )
                HostCard(
                    name = "Studio",
                    status = DotStatus.Attention,
                    statusText = "Not paired",
                    detail = "LuminalShine 26.09.1 · found on this network",
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
