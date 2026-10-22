package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.tvHostCardMinHeight
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Dense takes from the cards (ADR 0058, decision 7): the padding inside a host card and the
 * Continue card, 12dp on a TV and a handheld and 10dp on a phone instead of 18dp and 14dp; the
 * cover of the Continue card, 44dp instead of 56dp; and the least height of a host card on a TV,
 * 12dp less than the token, which is what the padding gives back at the top and the bottom.
 * Comfortable and Compact have the cards as they were.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardDensityPaddingTest(private val density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(cardDensity = density)

    /** The padding of a card on a TV and a handheld: 18dp, and 12dp in Dense. */
    private val tvPadding = if (density == CardDensity.Dense) 12.dp else 18.dp

    /** The padding of a card on a phone: 14dp, and 10dp in Dense. */
    private val phonePadding = if (density == CardDensity.Dense) 10.dp else 14.dp

    @Test
    @Config(qualifiers = TV)
    fun `a host card on a TV has the padding of the density`() =
        assertHostCardPadding(FormFactor.Tv, tvPadding)

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a host card on a handheld has the padding of the density`() =
        assertHostCardPadding(FormFactor.Handheld, tvPadding)

    @Test
    @Config(qualifiers = PHONE)
    fun `a host card on a phone has the padding of the density`() =
        assertHostCardPadding(FormFactor.Phone, phonePadding)

    @Test
    @Config(qualifiers = TV)
    fun `a host card on a TV has the least height of the density`() {
        showHostCard(FormFactor.Tv)

        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        assertThat((card.bottom - card.top).value)
            .isWithin(HALF_DP)
            .of(tvHostCardMinHeight(density).value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Continue card on a TV has the padding and the cover of the density`() {
        showContinueCard(FormFactor.Tv)

        assertContinueCard(tvPadding, if (density == CardDensity.Dense) 44.dp else 56.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Continue card on a phone has the padding and the cover of the density`() {
        showContinueCard(FormFactor.Phone)

        assertContinueCard(phonePadding, if (density == CardDensity.Dense) 44.dp else 56.dp)
    }

    private fun assertHostCardPadding(formFactor: FormFactor, padding: Dp) {
        showHostCard(formFactor)

        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val name = composeRule.onNodeWithText("Desk PC", useUnmergedTree = true)
        val text = name.getUnclippedBoundsInRoot()
        assertThat((text.left - card.left).value).isWithin(HALF_DP).of(padding.value)
        assertThat((text.top - card.top).value).isWithin(HALF_DP).of(padding.value)
    }

    /** The text of the card starts after the padding, the cover and the 14dp between them. */
    private fun assertContinueCard(padding: Dp, cover: Dp) {
        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val name = composeRule.onNodeWithText("Desktop", useUnmergedTree = true)
        val text = name.getUnclippedBoundsInRoot()
        assertThat((text.left - card.left).value)
            .isWithin(HALF_DP)
            .of((padding + cover + CONTENT_GAP).value)
    }

    private fun showHostCard(formFactor: FormFactor) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Offline,
                    statusText = "Offline",
                    onClick = {},
                    modifier = Modifier.width(CARD_WIDTH).testTag(CARD),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun showContinueCard(formFactor: FormFactor) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                ContinueCard(
                    appName = "Desktop",
                    detail = "Running on Desk PC",
                    resumeLabel = "Resume",
                    onClick = {},
                    modifier = Modifier.width(CONTINUE_WIDTH).testTag(CARD),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CARD = "card"
        const val HALF_DP = 0.5f
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h480dp-land-hdpi"
        val CARD_WIDTH = 260.dp
        val CONTINUE_WIDTH = 320.dp
        val CONTENT_GAP = 14.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
