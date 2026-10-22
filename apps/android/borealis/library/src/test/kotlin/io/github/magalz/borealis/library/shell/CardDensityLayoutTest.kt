package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.google.common.truth.Expect
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.library.testing.AppsOf
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.HostsOf
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.appTiles
import io.github.magalz.borealis.library.testing.hostCards
import io.github.magalz.borealis.library.testing.manyApps
import io.github.magalz.borealis.library.testing.manyHosts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Card density of Appearance on Hosts and Apps (ADR 0058, decision 7): how many cards sit in a
 * row on each device, and how much padding the grid keeps around them. Comfortable is the layout of
 * before the setting. Compact and Dense add one column everywhere, the wide phone of 600dp
 * included, and every density keeps the gutter of the screen (34dp on a TV and a handheld, 16dp on
 * a phone) as the padding of the grids, so that the cards line up with the title. Dense takes less
 * padding inside the cards: `CardDensityPaddingTest`. With Text spacing at Wider the cards keep the
 * columns of Comfortable in every density: the words of a card need the room that the extra column
 * would take (device round P2: host names broke inside a word, an address wrapped before its last
 * digit and a chip took two lines). Normal and Wide add the column. The screens are drawn alone,
 * with no rail, so that the padding is the distance of a card from the edge of the window.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardDensityLayoutTest(private val case: Case) {
    @get:Rule val composeRule = createComposeRule()

    /** The columns and the padding are both told, when both are wrong. */
    @get:Rule val expect: Expect = Expect.create()

    /** What a run changes: the density of the cards and the Text spacing. */
    class Case(val density: CardDensity, val spacing: TextSpacing) {
        override fun toString() = "$density-$spacing"
    }

    private val look = BorealisLook(cardDensity = case.density, textSpacing = case.spacing)

    /** The columns of [comfortable], one more for each denser layout, but at Wider. */
    private fun columns(comfortable: Int) =
        if (case.density == CardDensity.Comfortable || case.spacing == TextSpacing.Wider) {
            comfortable
        } else {
            comfortable + 1
        }

    @Test
    @Config(qualifiers = TV)
    fun `Hosts on a TV have the columns and the padding of the density`() {
        showHosts(FormFactor.Tv)

        expect.that(hostsPerRow()).isEqualTo(columns(3))
        assertHostPadding(TV_GUTTER)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Hosts on a handheld have the columns and the padding of the density`() {
        showHosts(FormFactor.Handheld)

        expect.that(hostsPerRow()).isEqualTo(columns(2))
        assertHostPadding(TV_GUTTER)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts on a phone have the columns and the padding of the density`() {
        showHosts(FormFactor.Phone)

        expect.that(hostsPerRow()).isEqualTo(columns(1))
        assertHostPadding(PHONE_GUTTER)
    }

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `Hosts on a wide phone have the columns and the padding of the density`() {
        showHosts(FormFactor.Phone)

        expect.that(hostsPerRow()).isEqualTo(columns(2))
        assertHostPadding(PHONE_GUTTER)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Apps on a TV have the columns and the padding of the density`() {
        showApps(FormFactor.Tv)

        expect.that(appsPerRow()).isEqualTo(columns(5))
        assertAppPadding(TV_GUTTER)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Apps on a handheld have the columns and the padding of the density`() {
        showApps(FormFactor.Handheld)

        expect.that(appsPerRow()).isEqualTo(columns(4))
        assertAppPadding(TV_GUTTER)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Apps on a phone have the columns and the padding of the density`() {
        showApps(FormFactor.Phone)

        expect.that(appsPerRow()).isEqualTo(columns(3))
        assertAppPadding(PHONE_GUTTER)
    }

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `Apps on a wide phone have the columns and the padding of the density`() {
        showApps(FormFactor.Phone)

        expect.that(appsPerRow()).isEqualTo(columns(4))
        assertAppPadding(PHONE_GUTTER)
    }

    private fun showHosts(formFactor: FormFactor) {
        composeRule.setContent { Themed(formFactor) { HostsOf(manyHosts(CARDS), formFactor) } }
        composeRule.waitForIdle()
    }

    private fun showApps(formFactor: FormFactor) {
        composeRule.setContent { Themed(formFactor) { AppsOf(manyApps(CARDS), formFactor) } }
        composeRule.waitForIdle()
    }

    /** The screen with the look of the density, as the shell gives it. */
    @Composable
    private fun Themed(formFactor: FormFactor, content: @Composable () -> Unit) {
        BorealisTheme(formFactor, look) { Box { content() } }
    }

    private fun hostsPerRow() =
        composeRule.hostCards().let { cards -> cards.count { it.top == cards[0].top } }

    private fun appsPerRow() =
        composeRule.appTiles().let { tiles -> tiles.count { it.top == tiles[0].top } }

    /**
     * The first card of a row is [padding] dp from the left edge and the last one from the right.
     */
    private fun assertHostPadding(padding: Float) = assertPadding(composeRule.hostCards(), padding)

    private fun assertAppPadding(padding: Float) = assertPadding(composeRule.appTiles(), padding)

    private fun assertPadding(cards: List<DpRect>, padding: Float) {
        val width = composeRule.onRoot().getUnclippedBoundsInRoot().right.value
        val row = cards.filter { it.top == cards[0].top }
        expect.that(row.first().left.value).isWithin(EPSILON).of(padding)
        expect.that(width - row.last().right.value).isWithin(EPSILON).of(padding)
    }

    private companion object {
        const val WIDE_PHONE = "w600dp-h960dp-xhdpi"

        /** The cards on screen, more than the widest row, so that the first row is a full one. */
        const val CARDS = 12

        /** The gutter tokens, which the grids have as their padding in every density. */
        const val TV_GUTTER = 34f
        const val PHONE_GUTTER = 16f

        const val EPSILON = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Case> =
            CardDensity.entries.flatMap { density ->
                TextSpacing.entries.map { spacing -> Case(density, spacing) }
            }
    }
}
