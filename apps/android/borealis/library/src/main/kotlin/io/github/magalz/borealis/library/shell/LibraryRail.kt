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

/** The rail item that stands for the screen beside the rail. */
internal enum class RailSelection {
    Hosts,
    AddHost,

    /** Settings, and Privacy and diagnostics and the Audit trail that open from it. */
    Settings,
}

/**
 * The frame of the Library screens: on a TV or handheld, [content] with the [LibraryRail] beside it
 * on every screen, with [selected] marked. A phone has no rail.
 *
 * The frame owns the system bar insets where it has the rail, so the rail and the screen beside it
 * start at the same edge and nothing moves when the screen changes; the screens of a phone draw
 * their own insets.
 */
@Composable
internal fun LibraryScaffold(
    formFactor: FormFactor,
    selected: RailSelection,
    actions: RailActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BorealisTheme(formFactor) {
        val rail = formFactor != FormFactor.Phone
        Row(
            modifier
                .fillMaxSize()
                .background(LocalBorealisColors.current.page)
                .then(
                    if (rail) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier
                )
        ) {
            if (rail) LibraryRail(selected, actions)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** The items of the rail, in order: Hosts, Add host and Settings. */
@Composable
internal fun libraryRailItems(): List<RailItem> =
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

/**
 * Hosts (also for Apps and Pairing, which are part of it), Add host and Settings (also for Privacy
 * and diagnostics and the Audit trail).
 */
@Composable
internal fun LibraryRail(selected: RailSelection, actions: RailActions) {
    NavigationRail(
        items = libraryRailItems(),
        selectedIndex =
            when (selected) {
                RailSelection.Hosts -> RAIL_HOSTS
                RailSelection.AddHost -> RAIL_ADD_HOST
                RailSelection.Settings -> RAIL_SETTINGS
            },
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
