package io.github.magalz.borealis.library.hosts

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.tvHostCardMinHeight
import io.github.magalz.borealis.designsystem.testing.tvHostCardWidth
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.HostsOf
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The size of the host cards (ADR 0058, decision 7): on a TV their width, which the fit tests of
 * the card assume, and on every device their height, which is the same for all the cards of a row.
 * On a TV a card is at least as high as the height token; a card that needs more room (a name that
 * wraps, tags that wrap to a second row, a narrower card in a denser layout) makes the cards beside
 * it as high as itself and no more, and the next row does not follow it. A handheld and a phone
 * have no token: a card is as high as its content, and a row with more than one card has the height
 * of the tallest. A card alone in its row, as on a phone with one column, keeps its own height.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class HostsCardSizeTest(private val density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(cardDensity = density)

    /** The columns of [comfortable], one more in a denser layout. */
    private fun columns(comfortable: Int) =
        if (density == CardDensity.Comfortable) comfortable else comfortable + 1

    /** The fullest host, with tags that wrap to a second row: taller than the token. */
    private val fullest =
        HostItemUiState(
            "a",
            "Desk PC",
            Reachability.ONLINE,
            true,
            RunningApp(1, "Desktop"),
            HostAction.OPEN,
            address = "192.168.1.20",
            tags =
                listOf(
                    HostCapability.HEVC_10_BIT,
                    HostCapability.HDR,
                    HostCapability.YUV_444,
                    HostCapability.AV1_10_BIT,
                ),
        )

    private fun shortHost(index: Int) =
        HostItemUiState(
            "h$index",
            "Host $index",
            Reachability.OFFLINE,
            true,
            null,
            HostAction.WAKE,
            address = "192.168.1.$index",
        )

    /** The fullest host and the others of its row, then one host in the row below it. */
    private fun hosts(perRow: Int) =
        listOf(fullest) + (2..perRow).map(::shortHost) + shortHost(perRow + 1)

    @Test
    @Config(qualifiers = TV)
    fun `a host card on a TV is as wide as the fit tests of the card assume`() {
        show(FormFactor.Tv, columns(3))

        val width = composeRule.onNodeWithTag("host:a").getUnclippedBoundsInRoot().width
        assertThat(width.value).isWithin(1f).of(tvHostCardWidth(density).value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the cards of a row are as high as the tallest of them`() {
        show(FormFactor.Tv, columns(3))

        val heights = row(columns(3))
        assertThat(heights.first().value).isGreaterThan(tvHostCardMinHeight(density).value)
        for (height in heights) assertThat(height.value).isWithin(HALF_DP).of(heights.first().value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the next row does not follow the tallest card and keeps the token`() {
        show(FormFactor.Tv, columns(3))

        val below = height("h${columns(3) + 1}")
        assertThat(below.value).isWithin(HALF_DP).of(tvHostCardMinHeight(density).value)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the cards of a row are as high as the tallest of them`() =
        assertRowsShareTheTallest(FormFactor.Handheld, columns(2))

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun `on a wide phone the cards of a row are as high as the tallest of them`() =
        assertRowsShareTheTallest(FormFactor.Phone, columns(2))

    /** One column in Comfortable: a card alone in its row keeps its own height. */
    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the cards of a row are as high as the tallest of them`() =
        assertRowsShareTheTallest(FormFactor.Phone, columns(1))

    /**
     * The cards of the first row, which has the fullest card in it, are as high as it, which is
     * higher than a card that has the same content as the others; and the next row does not follow
     * it, so its card keeps the height of its own content.
     */
    private fun assertRowsShareTheTallest(formFactor: FormFactor, perRow: Int) {
        show(formFactor, perRow)

        val heights = row(perRow)
        val own = height("h${perRow + 1}")
        assertThat(heights.first().value).isGreaterThan(own.value + 1f)
        for (height in heights) assertThat(height.value).isWithin(HALF_DP).of(heights.first().value)
    }

    /** The heights of the cards of the first row: the fullest host and the [perRow] - 1 others. */
    private fun row(perRow: Int): List<Dp> =
        (listOf("a") + (2..perRow).map { "h$it" }).map(::height)

    private fun height(id: String): Dp =
        composeRule.onNodeWithTag("host:$id").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun show(formFactor: FormFactor, perRow: Int) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = look,
            ) {
                HostsOf(
                    HostsUiState(loading = false, searching = false, items = hosts(perRow)),
                    formFactor,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val HALF_DP = 0.5f
        const val WIDE_PHONE = "w600dp-h960dp-xhdpi"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
