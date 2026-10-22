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
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The size of the host cards of a TV (ADR 0058, decision 7): their width, which the fit tests of
 * the card assume, and their height, which is the same for all the cards of a row. A card is at
 * least as high as the height token; a card that needs more room (a name that wraps, tags that wrap
 * to a second row, a narrower card in a denser layout) makes the cards beside it as high as itself
 * and no more, and the next row does not follow it. A handheld, which has little room to spare,
 * keeps the height that each card needs.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class HostsCardSizeTest(private val density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(cardDensity = density)
    private val perRow = if (density == CardDensity.Comfortable) 3 else 4

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
    private val hosts = listOf(fullest) + (2..perRow).map(::shortHost) + shortHost(perRow + 1)

    @Test
    @Config(qualifiers = TV)
    fun `a host card on a TV is as wide as the fit tests of the card assume`() {
        show(FormFactor.Tv)

        val width = composeRule.onNodeWithTag("host:a").getUnclippedBoundsInRoot().width
        assertThat(width.value).isWithin(1f).of(tvHostCardWidth(density).value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the cards of a row are as high as the tallest of them`() {
        show(FormFactor.Tv)

        val heights = (listOf("a") + (2..perRow).map { "h$it" }).map(::height)
        assertThat(heights.first().value).isGreaterThan(tvHostCardMinHeight(density).value)
        for (height in heights) assertThat(height.value).isWithin(HALF_DP).of(heights.first().value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the next row does not follow the tallest card and keeps the token`() {
        show(FormFactor.Tv)

        val below = height("h${perRow + 1}")
        assertThat(below.value).isWithin(HALF_DP).of(tvHostCardMinHeight(density).value)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld each card keeps the height that it needs`() {
        show(FormFactor.Handheld)

        val fullest = height("a")
        val shortest = height("h2")
        assertThat(shortest.value).isLessThan(fullest.value - 1f)
    }

    private fun height(id: String): Dp =
        composeRule.onNodeWithTag("host:$id").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun show(formFactor: FormFactor) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = look,
            ) {
                HostsOf(HostsUiState(loading = false, searching = false, items = hosts), formFactor)
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val HALF_DP = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
