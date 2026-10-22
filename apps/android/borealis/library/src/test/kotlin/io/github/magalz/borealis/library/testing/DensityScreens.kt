package io.github.magalz.borealis.library.testing

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.DpRect
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.hosts.HostAction
import io.github.magalz.borealis.library.hosts.HostItemUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.HostsUiState

/**
 * Hosts and Apps with [count] cards, for the tests of the Card density: more cards than the widest
 * row, so that the first row of a grid is a full one. The hosts are `host:h1`, `host:h2` and so on,
 * and the tiles are described as `App 1`, `App 2` and so on.
 */
internal fun manyHosts(count: Int): HostsUiState =
    HostsUiState(
        loading = false,
        searching = false,
        items =
            (1..count).map {
                HostItemUiState(
                    "h$it",
                    "Host $it",
                    Reachability.ONLINE,
                    true,
                    null,
                    HostAction.OPEN,
                )
            },
    )

internal fun manyApps(count: Int, running: Int? = null): AppsUiState =
    AppsUiState(
        loading = false,
        hostName = "Desk PC",
        runningAppId = running,
        runningAppName = running?.let { "App $it" },
        items =
            (1..count).map {
                AppItemUiState(
                    it,
                    "App $it",
                    running = it == running,
                    hidden = false,
                    hdrSupported = false,
                )
            },
    )

@Composable
internal fun HostsOf(state: HostsUiState, formFactor: FormFactor) {
    HostsScreen(
        state = state,
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
internal fun AppsOf(state: AppsUiState, formFactor: FormFactor) {
    AppsScreen(
        state = state,
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

internal val isHostCard =
    SemanticsMatcher("is a host card") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("host:") == true
    }

/** The cover of a tile is the node that has the name of its app for a description. */
internal val isAppTile =
    SemanticsMatcher("is an app tile") {
        val description = it.config.getOrNull(SemanticsProperties.ContentDescription)
        description?.firstOrNull()?.startsWith("App ") == true
    }

/** The bounds of the nodes that [matcher] matches, in the order of the tree. */
internal fun ComposeTestRule.boundsOf(matcher: SemanticsMatcher): List<DpRect> {
    val nodes = onAllNodes(matcher)
    return nodes.fetchSemanticsNodes().indices.map { nodes[it].getUnclippedBoundsInRoot() }
}

/** The host cards that are on screen, in the order of the grid. */
internal fun ComposeTestRule.hostCards(): List<DpRect> = boundsOf(isHostCard)

/** The cover tiles that are on screen, in the order of the grid. */
internal fun ComposeTestRule.appTiles(): List<DpRect> = boundsOf(isAppTile)
