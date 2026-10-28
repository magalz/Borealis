package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.Edge
import io.github.magalz.borealis.designsystem.testing.colourOutside
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thicker focus outline (7dp from the element at its outer edge, a little more for a tile that
 * grows on focus) is not cut off in the grids of the Library (ADR 0057, decision 11): the Continue
 * card and the host cards of Hosts, and the cover tiles of Apps, on a TV and on a phone, in every
 * Card density (ADR 0058, decision 7), Dense, whose grids have the least padding, included. The
 * outline is read from the pixels just inside its outer edge, past the edge of the focused item.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = TV)
class ThickFocusClippingTest(density: CardDensity) {
    @get:Rule val composeRule = createComposeRule()

    private val thick = BorealisLook(thickFocus = true, cardDensity = density)
    private val outline = BorealisColors().ink
    private val names = listOf("Desktop", "Steam", "Playnite", "Hades II", "Elden Ring", "Doom")

    @Test
    fun `the outline of the Continue card and of the host cards of Hosts is whole`() {
        show(FormFactor.Tv) { Hosts(it) }

        assertWhole("continue:desk", Edge.Left, Edge.Right)
        assertWhole("host:desk", Edge.Left)
        assertWhole("host:studio", Edge.Right)
    }

    @Test
    fun `the outline of the cover tiles of Apps is whole, also as the tile grows`() {
        show(FormFactor.Tv) { Apps(it) }

        assertWholeTile(names.first(), Edge.Left, Edge.Top)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the outline of the host cards of Hosts is whole on a phone`() {
        show(FormFactor.Phone) { Hosts(it) }

        assertWhole("continue:desk", Edge.Left, Edge.Right)
        assertWhole("host:desk", Edge.Left, Edge.Right)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the outline of the cover tiles of Apps is whole on a phone, in the first and last column`() {
        show(FormFactor.Phone) { Apps(it) }

        assertWholeTile(names.first(), Edge.Left, Edge.Top)
        // The last tile of the row is the third one of Comfortable and the fourth of the others.
        val lastOfRow = names[if (thick.cardDensity == CardDensity.Comfortable) 2 else 3]
        assertWholeTile(lastOfRow, Edge.Right, Edge.Top)
    }

    private fun show(formFactor: FormFactor, screen: @Composable (FormFactor) -> Unit) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = thick,
            ) {
                screen(formFactor)
            }
        }
        composeRule.setContentInKeyboardMode(content)
    }

    @Composable
    private fun Hosts(formFactor: FormFactor) {
        HostsScreen(
            state = withHosts,
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onHostSelected = {},
            onContinue = {},
            onWake = {},
            onForget = {},
            onDetails = {},
            onDetailsDismissed = {},
            onAddHost = {},
            onSettings = {},
        )
    }

    @Composable
    private fun Apps(formFactor: FormFactor) {
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = "Desk PC",
                    items =
                        names.mapIndexed { index, name ->
                            AppItemUiState(
                                index + 1,
                                name,
                                running = false,
                                hidden = false,
                                hdrSupported = false,
                            )
                        },
                ),
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onAppSelected = {},
            onActions = {},
            onActionChosen = {},
            onActionsDismissed = {},
            onDetailsDismissed = {},
            onToggleShowHidden = {},
        )
    }

    private fun assertWholeTile(name: String, vararg edges: Edge) {
        val tile = composeRule.onNodeWithContentDescription(name)
        tile.requestFocus()
        composeRule.waitForIdle()
        val bounds = tile.fetchSemanticsNode().boundsInRoot
        val density = composeRule.onRoot().fetchSemanticsNode().layoutInfo.density.density
        // The tile grows by 2 percent of its width at the sides and of its height at the top.
        for (edge in edges) {
            val size = if (edge == Edge.Top) bounds.height else bounds.width
            val dp = size / density * TILE_GROWTH + INSIDE_OUTER_EDGE_DP
            val seen = composeRule.colourOutside(tile, edge, dp)
            assertWithMessage("the outline of $name $dp dp past its $edge edge is $seen")
                .that(seen.isNear(outline))
                .isTrue()
        }
    }

    private fun assertWhole(tag: String, vararg edges: Edge) {
        val card = composeRule.onNodeWithTag(tag)
        card.requestFocus()
        composeRule.waitForIdle()
        for (edge in edges) {
            val seen = composeRule.colourOutside(card, edge, INSIDE_OUTER_EDGE_DP)
            assertWithMessage("the outline of $tag past its $edge edge is $seen")
                .that(seen.isNear(outline))
                .isTrue()
        }
    }

    private companion object {
        /** 6.5dp: the outline covers 2dp to 7dp from the element. */
        const val INSIDE_OUTER_EDGE_DP = 6.5f

        /** A tile of a cover grows by 2 percent of its size on each side when it has focus. */
        const val TILE_GROWTH = 0.02f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun densities(): List<CardDensity> = CardDensity.entries
    }
}
