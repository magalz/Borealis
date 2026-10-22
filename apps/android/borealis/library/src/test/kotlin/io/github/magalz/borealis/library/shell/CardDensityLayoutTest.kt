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
 * before the setting. Compact adds one column everywhere, the wide phone of 600dp included, and
 * Dense adds the same column and takes the padding of the grid down from the gutter (34dp on a TV
 * and a handheld, 16dp on a phone) to 12dp and 10dp. The screens are drawn alone, with no rail, so
 * that the padding is the distance of a card from the edge of the window.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardDensityLayoutTest(private val density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    /** The columns and the padding are both told, when both are wrong. */
    @get:Rule val expect: Expect = Expect.create()

    private val look = BorealisLook(cardDensity = density)

    /** The columns of [comfortable], one more for each denser layout. */
    private fun columns(comfortable: Int) =
        if (density == CardDensity.Comfortable) comfortable else comfortable + 1

    /** The padding of the grid: [gutter] until Dense, which has [dense]. */
    private fun padding(gutter: Float, dense: Float) =
        if (density == CardDensity.Dense) dense else gutter

    @Test
    @Config(qualifiers = TV)
    fun `Hosts on a TV have the columns and the padding of the density`() {
        showHosts(FormFactor.Tv)

        expect.that(hostsPerRow()).isEqualTo(columns(3))
        assertHostPadding(padding(TV_GUTTER, TV_DENSE))
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Hosts on a handheld have the columns and the padding of the density`() {
        showHosts(FormFactor.Handheld)

        expect.that(hostsPerRow()).isEqualTo(columns(2))
        assertHostPadding(padding(TV_GUTTER, TV_DENSE))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Hosts on a phone have the columns and the padding of the density`() {
        showHosts(FormFactor.Phone)

        expect.that(hostsPerRow()).isEqualTo(columns(1))
        assertHostPadding(padding(PHONE_GUTTER, PHONE_DENSE))
    }

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `Hosts on a wide phone have the columns and the padding of the density`() {
        showHosts(FormFactor.Phone)

        expect.that(hostsPerRow()).isEqualTo(columns(2))
        assertHostPadding(padding(PHONE_GUTTER, PHONE_DENSE))
    }

    @Test
    @Config(qualifiers = TV)
    fun `Apps on a TV have the columns and the padding of the density`() {
        showApps(FormFactor.Tv)

        expect.that(appsPerRow()).isEqualTo(columns(5))
        assertAppPadding(padding(TV_GUTTER, TV_DENSE))
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Apps on a handheld have the columns and the padding of the density`() {
        showApps(FormFactor.Handheld)

        expect.that(appsPerRow()).isEqualTo(columns(4))
        assertAppPadding(padding(TV_GUTTER, TV_DENSE))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Apps on a phone have the columns and the padding of the density`() {
        showApps(FormFactor.Phone)

        expect.that(appsPerRow()).isEqualTo(columns(3))
        assertAppPadding(padding(PHONE_GUTTER, PHONE_DENSE))
    }

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `Apps on a wide phone have the columns and the padding of the density`() {
        showApps(FormFactor.Phone)

        expect.that(appsPerRow()).isEqualTo(columns(4))
        assertAppPadding(padding(PHONE_GUTTER, PHONE_DENSE))
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

        /** The gutter tokens, which the grids have as their padding today. */
        const val TV_GUTTER = 34f
        const val PHONE_GUTTER = 16f

        /** The padding of Dense (ADR 0058, decision 7). */
        const val TV_DENSE = 12f
        const val PHONE_DENSE = 10f
        const val EPSILON = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
