package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.NavigationRail
import io.github.magalz.borealis.designsystem.components.RailItem
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.library.R

private val RAIL_WIDTH = 200.dp

private const val RAIL_HOSTS = 0
private const val RAIL_ADD_HOST = 1
private const val RAIL_SETTINGS = 2

/** What the items of the [LibraryRail] do. */
internal class RailActions(
    val onHosts: () -> Unit,
    val onAddHost: () -> Unit,
    val onSettings: () -> Unit,
)

/** The rail item that stands for the screen beside it. */
internal enum class RailSelection {
    Hosts,
    AddHost,
}

/**
 * The frame of the Library screens: on a TV or handheld, [content] with the [LibraryRail] beside it
 * while [showRail] is set, which is the rail of Hosts and Apps. A phone has no rail.
 *
 * The frame owns the system bar insets while the rail shows, so the rail and the screen beside it
 * start at the same edge; the screens draw their own insets otherwise. [content] stays at the same
 * place in the composition when the rail comes and goes, so a screen keeps its state.
 */
@Composable
internal fun LibraryScaffold(
    formFactor: FormFactor,
    showRail: Boolean = true,
    actions: RailActions,
    modifier: Modifier = Modifier,
    selected: RailSelection = RailSelection.Hosts,
    content: @Composable () -> Unit,
) {
    BorealisTheme(formFactor) {
        val rail = showRail && formFactor != FormFactor.Phone
        Row(
            modifier
                .fillMaxSize()
                .background(LocalBorealisColors.current.page)
                .then(
                    if (rail) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier
                )
        ) {
            if (rail) LibraryRail(actions)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** Hosts (always selected: Hosts and Apps are the same area), Add host and Settings. */
@Composable
internal fun LibraryRail(actions: RailActions) {
    val items =
        listOf(
            RailItem(
                stringResource(R.string.hosts_title),
                ImageVector.vectorResource(R.drawable.ic_hosts_home),
            ),
            RailItem(
                stringResource(R.string.hosts_add_host),
                ImageVector.vectorResource(R.drawable.ic_hosts_add),
            ),
            RailItem(
                stringResource(R.string.hosts_settings),
                ImageVector.vectorResource(R.drawable.ic_hosts_settings),
            ),
        )
    NavigationRail(
        items = items,
        selectedIndex = RAIL_HOSTS,
        onSelect = { index ->
            when (index) {
                RAIL_HOSTS -> actions.onHosts()
                RAIL_ADD_HOST -> actions.onAddHost()
                RAIL_SETTINGS -> actions.onSettings()
            }
        },
        modifier = Modifier.width(RAIL_WIDTH),
    )
}
