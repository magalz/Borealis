package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.Edge
import io.github.magalz.borealis.designsystem.testing.colourOutside
import io.github.magalz.borealis.designsystem.testing.isNear
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.setContentInKeyboardMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The thicker focus outline (7dp from the element at its outer edge, a little more for a tile that
 * grows on focus) is not cut off in the grids of the Library (ADR 0057, decision 11): the Continue
 * card and the host cards of Hosts, and the cover tiles of Apps. The outline is read from the
 * pixels just inside its outer edge, past the edge of the focused item.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class ThickFocusClippingTest {
    @get:Rule val composeRule = createComposeRule()

    private val thick = BorealisLook(thickFocus = true)
    private val outline = BorealisColors().ink

    @Test
    fun `the outline of the Continue card and of the host cards of Hosts is whole`() {
        composeRule.setContentInKeyboardMode {
            LibraryScaffold(
                FormFactor.Tv,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = thick,
            ) {
                HostsScreen(
                    state = withHosts,
                    formFactor = FormFactor.Tv,
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
        }

        assertWhole("continue:desk", Edge.Left, Edge.Right)
        assertWhole("host:desk", Edge.Left)
        assertWhole("host:studio", Edge.Right)
    }

    @Test
    fun `the outline of the cover tiles of Apps is whole, also as the tile grows`() {
        composeRule.setContentInKeyboardMode {
            LibraryScaffold(
                FormFactor.Tv,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = thick,
            ) {
                AppsScreen(
                    state =
                        AppsUiState(
                            loading = false,
                            hostName = "Desk PC",
                            items =
                                listOf("Desktop", "Steam", "Playnite", "Hades II", "Elden Ring")
                                    .mapIndexed { index, name ->
                                        AppItemUiState(
                                            index + 1,
                                            name,
                                            running = false,
                                            hidden = false,
                                            hdrSupported = false,
                                        )
                                    },
                        ),
                    formFactor = FormFactor.Tv,
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
        }

        val tile = composeRule.onNodeWithContentDescription("Desktop")
        tile.requestFocus()
        composeRule.waitForIdle()
        val bounds = tile.fetchSemanticsNode().boundsInRoot
        val density = composeRule.onRoot().fetchSemanticsNode().layoutInfo.density.density
        // The tile grows by 2 percent of its width at the sides and of its height at the top.
        for ((edge, size) in mapOf(Edge.Left to bounds.width, Edge.Top to bounds.height)) {
            val dp = size / density * TILE_GROWTH + INSIDE_OUTER_EDGE_DP
            val seen = composeRule.colourOutside(tile, edge, dp)
            assertWithMessage("the outline of the first tile $dp dp past its $edge edge is $seen")
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
    }
}
