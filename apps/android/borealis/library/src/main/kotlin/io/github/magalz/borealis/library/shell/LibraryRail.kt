package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.BorealisBackground
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.NavigationRail
import io.github.magalz.borealis.designsystem.components.RailItem
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.R

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
 * The frame draws the [backgroundStyle] once, behind the rail and the screen: the screens have no
 * page of their own, so it shows through them, and the rail keeps its own colour.
 * [BackgroundStyle.Off] is the plain page. It gives the screens the [look] that the viewer chose;
 * the theme that each screen wears inside it keeps that look.
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
    backgroundStyle: BackgroundStyle = BackgroundStyle.Off,
    look: BorealisLook = BorealisLook(),
    content: @Composable () -> Unit,
) {
    BorealisTheme(formFactor, look) {
        val rail = formFactor != FormFactor.Phone
        Box(modifier.fillMaxSize()) {
            BorealisBackground(backgroundStyle, Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxSize()
                    .then(
                        if (rail) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        else Modifier
                    )
            ) {
                if (rail) LibraryRail(selected, actions)
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/** The items of the rail, in order: Hosts, Add host and Settings. */
@Composable
internal fun libraryRailItems(): List<RailItem> =
    listOf(
        RailItem(
            stringResource(R.string.hosts_title),
            BorealisIcons.Hosts,
        ),
        RailItem(
            stringResource(R.string.hosts_add_host),
            BorealisIcons.Add,
        ),
        RailItem(
            stringResource(R.string.hosts_settings),
            BorealisIcons.Gear,
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
    )
}
