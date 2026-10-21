package io.github.magalz.borealis.library.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.adaptive.appGridColumns
import io.github.magalz.borealis.designsystem.components.AuroraLoader
import io.github.magalz.borealis.designsystem.components.BackdropIcon
import io.github.magalz.borealis.designsystem.components.BackdropInk
import io.github.magalz.borealis.designsystem.components.BackdropText
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.BorealisPhoneBar
import io.github.magalz.borealis.designsystem.components.BorealisSnackbarHost
import io.github.magalz.borealis.designsystem.components.CoverTile
import io.github.magalz.borealis.designsystem.components.DotStatus
import io.github.magalz.borealis.designsystem.components.EmptyState
import io.github.magalz.borealis.designsystem.components.StatusDot
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.library.R

/**
 * An outline reaches BorealisDimens.outlineReach outside a tile, thickOutlineReach when it is the
 * thicker one, and a focused tile grows by 2 percent: the grid keeps 12dp or more at its edges and
 * BorealisDimens.listGap or more between tiles.
 */
private val TILE_GAP = 12.dp
private val ROW_GAP = 16.dp
private val GRID_VERTICAL_PADDING = 12.dp
private val STATUS_GAP = 6.dp
private val SNACKBAR_PADDING = 16.dp
private val LOADER_SIZE = 64.dp

/**
 * The apps of one host as a grid of covers: [AppsUiState.items] in order, the running app first. A
 * tap or OK runs [onAppSelected]; a long press, holding OK or X runs [onActions]. The actions and
 * details of [AppsUiState] open as a sheet and a dialog over the grid, and Back closes them before
 * anything else. A phone has a top bar with a back arrow, which calls [onBack], and the toggle for
 * hidden apps; a TV or handheld has a header with the toggle as a button and no back arrow, because
 * the system Back there is handled by the navigation of the shell. Messages go through
 * [snackbarHostState]. Provides its own theme for [formFactor].
 */
@Composable
fun AppsScreen(
    state: AppsUiState,
    formFactor: FormFactor,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAppSelected: (appId: Int) -> Unit,
    onActions: (appId: Int) -> Unit,
    onActionChosen: (AppAction) -> Unit,
    onActionsDismissed: () -> Unit,
    onDetailsDismissed: () -> Unit,
    onToggleShowHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorealisTheme(formFactor) {
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                if (formFactor == FormFactor.Phone) {
                    BorealisPhoneBar(
                        onBack,
                        stringResource(R.string.apps_back),
                        actions = { ShowHiddenToggle(state, onToggleShowHidden) },
                    ) {
                        Title(state)
                    }
                } else {
                    Header(state, onToggleShowHidden)
                }
                Content(state, formFactor, onAppSelected, onActions, Modifier.weight(1f))
            }
            BorealisSnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).padding(SNACKBAR_PADDING),
            )
        }
        state.actions?.let { AppActionsSheet(it, onActionChosen, onActionsDismissed) }
        state.details?.let { AppDetailsDialog(it, onDetailsDismissed) }
    }
}

/** The toggle of the phone bar, as an icon: a TV or handheld has a button in [Header]. */
@Composable
private fun ShowHiddenToggle(state: AppsUiState, onToggleShowHidden: () -> Unit) {
    IconButton(onClick = onToggleShowHidden) {
        BackdropIcon(
            // The icon shows what the toggle does: open eye shows them, crossed eye hides.
            if (state.showHidden) BorealisIcons.VisibilityOff else BorealisIcons.Visibility,
            stringResource(toggleLabel(state)),
        )
    }
}

@Composable
private fun Header(state: AppsUiState, onToggleShowHidden: () -> Unit) {
    val gutter = LocalBorealisSpacing.current.gutter
    Row(
        Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Title(state, Modifier.weight(1f))
        BorealisButton(
            stringResource(toggleLabel(state)),
            onToggleShowHidden,
            style = BorealisButtonStyle.Secondary,
        )
    }
}

private fun toggleLabel(state: AppsUiState) =
    if (state.showHidden) R.string.apps_hide_hidden else R.string.apps_show_hidden

/** The host name with what runs on it. */
@Composable
private fun Title(state: AppsUiState, modifier: Modifier = Modifier) {
    val running = state.runningAppId != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BorealisDimens.stackedTextGap)) {
        BackdropText(
            state.hostName.orEmpty(),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(STATUS_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) StatusDot(DotStatus.Online, contentDescription = null)
            BackdropText(
                when {
                    state.runningAppName != null ->
                        stringResource(R.string.apps_status_running, state.runningAppName)
                    running -> stringResource(R.string.apps_status_running_unknown)
                    else -> stringResource(R.string.apps_status_idle)
                },
                style = MaterialTheme.typography.bodySmall,
                ink = BackdropInk.Ink3,
            )
        }
    }
}

@Composable
private fun Content(
    state: AppsUiState,
    formFactor: FormFactor,
    onAppSelected: (Int) -> Unit,
    onActions: (Int) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        when {
            state.items.isNotEmpty() ->
                Grid(
                    state.items,
                    appGridColumns(formFactor, maxWidth.value.toInt()),
                    onAppSelected,
                    onActions,
                )
            state.loading -> {
                val description = stringResource(R.string.apps_loading)
                AuroraLoader(
                    Modifier.align(Alignment.Center).semantics { contentDescription = description },
                    LOADER_SIZE,
                )
            }
            else ->
                EmptyState(
                    stringResource(R.string.apps_empty_title),
                    stringResource(R.string.apps_empty_body),
                    Modifier.align(Alignment.Center),
                )
        }
    }
}

@Composable
private fun Grid(
    items: List<AppItemUiState>,
    columns: Int,
    onAppSelected: (Int) -> Unit,
    onActions: (Int) -> Unit,
) {
    val gutter = LocalBorealisSpacing.current.gutter
    val running = stringResource(R.string.apps_running)
    val hidden = stringResource(R.string.apps_hidden)
    val options = stringResource(R.string.apps_options)
    // Focus starts on the app that was first when the grid appeared: showing the hidden apps must
    // not pull it to another tile, and a TV or gamepad starts on a tile, not on the header.
    val firstId = remember { items.first().id }
    val firstFocus = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = gutter, vertical = GRID_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        items(items, key = { it.id }) { app ->
            CoverTile(
                name = app.name,
                onClick = { onAppSelected(app.id) },
                modifier = if (app.id == firstId) Modifier.initialFocus(firstFocus) else Modifier,
                runningLabel = running.takeIf { app.running },
                hidden = app.hidden,
                hiddenLabel = hidden,
                onOptions = { onActions(app.id) },
                optionsLabel = options,
            )
        }
    }
}
