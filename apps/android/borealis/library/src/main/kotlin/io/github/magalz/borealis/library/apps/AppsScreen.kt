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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
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
 * Above this font scale, which the text size and the system font make together (WCAG 1.4.4 asks for
 * 2.0 without loss), the title stops sharing its row with the controls: a long host name at 2.6
 * needs the whole width, and beside a button or two icons it took the height of the screen.
 */
private const val STACKED_HEADER_FONT_SCALE = 2f

/**
 * The apps of one host as a grid of covers: [AppsUiState.items] in order, the running app first. A
 * tap or OK runs [onAppSelected]; a long press, holding OK or X runs [onActions]. The actions and
 * details of [AppsUiState] open as a sheet and a dialog over the grid, and Back closes them before
 * anything else. A phone has a top bar with a back arrow, which calls [onBack], and the toggle for
 * hidden apps; a TV or handheld has a header with the toggle as a button and no back arrow, because
 * the system Back there is handled by the navigation of the shell. Above a font scale of 2.0 the
 * title takes the whole width: under the bar of a phone, above the toggle of a TV or handheld
 * ([STACKED_HEADER_FONT_SCALE]). When the last of these windows closes, the tile of the app they
 * were for has the focus again ([RestoreOpenerFocus]); after Hide, which takes the app off the
 * grid, it is the tile that takes its place ([openerAfterHide]). Messages go through
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
        // The app that the last window over the grid was for, whichever way it opened: a tap or OK
        // on an app that asks, X or a long press, or a launch request; after Hide, the tile that
        // takes its place. A phone starts with no focus, so there is nothing to give back.
        var openerId by remember { mutableStateOf<Int?>(null) }
        val windowAppId = state.actions?.app?.id ?: state.details?.id
        LaunchedEffect(windowAppId) { if (windowAppId != null) openerId = windowAppId }
        val opener = openerId.takeIf { formFactor != FormFactor.Phone }
        val openerFocus = remember { FocusRequester() }
        val stacked = LocalDensity.current.fontScale > STACKED_HEADER_FONT_SCALE
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                if (formFactor == FormFactor.Phone) {
                    BorealisPhoneBar(
                        onBack,
                        stringResource(R.string.apps_back),
                        actions = { ShowHiddenToggle(state, onToggleShowHidden) },
                    ) {
                        if (!stacked) Title(state)
                    }
                    if (stacked) {
                        val gutter = LocalBorealisSpacing.current.gutter
                        Title(state, Modifier.padding(horizontal = gutter))
                    }
                } else {
                    Header(state, stacked, onToggleShowHidden)
                }
                Content(
                    state,
                    formFactor,
                    onAppSelected,
                    onActions,
                    opener,
                    openerFocus,
                    Modifier.weight(1f),
                )
            }
            BorealisSnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).padding(SNACKBAR_PADDING),
            )
        }
        state.actions?.let { sheet ->
            AppActionsSheet(
                sheet,
                onChosen = { action ->
                    if (action == AppAction.HIDE) {
                        openerId = state.openerAfterHide(sheet.app.id, openerId)
                    }
                    onActionChosen(action)
                },
                onDismiss = onActionsDismissed,
            )
        }
        state.details?.let { AppDetailsDialog(it, onDetailsDismissed) }
        RestoreOpenerFocus(
            open = state.actions != null || state.details != null,
            openerListed = state.items.any { it.id == opener },
            focus = openerFocus,
        )
    }
}

/**
 * Asks for [focus], the requester of the tile of the app that opened the windows, once the last
 * window over the grid has closed: the actions, the quit confirmation inside them or the details.
 * On the TV the focus was lost after Back (Sprint 14 UAT, U11); the tests assume that the window
 * took it from the screen and that Compose gave it back to the first tile, not to the one that had
 * it. Nothing happens when the app is already gone from the grid ([openerListed] is false), and
 * nothing when the screen starts with a window already open: that is not a close. After Hide the
 * removal is asynchronous (the ViewModel launches it), so the app may still be in the grid when the
 * sheet closes: the screen has made the app that opened the windows the tile that takes its place
 * ([openerAfterHide]), so the tile that asks for the focus is one that stays. A tile that the grid
 * does not compose (many apps, scrolled away while a window was open) has no node for [focus]:
 * Compose logs it and answers false, so nothing fails. This is the effect of `RestoreOpenerFocus`
 * of the hosts screen, written for apps because that one takes the host list and ids.
 */
@Composable
private fun RestoreOpenerFocus(open: Boolean, openerListed: Boolean, focus: FocusRequester) {
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (wasOpen && !open && openerListed) focus.requestFocus()
        wasOpen = open
    }
}

/**
 * The app whose tile has the focus when the windows close after Hide on [appId], given that the app
 * that opened them is [openerId]. While the hidden apps are not shown (the filter of the ViewModel
 * keeps `showHidden || !hidden`), the tile is about to go, and the focus must not wait for it: it
 * is the tile that takes its place, the next one in the grid, or the previous one when it was the
 * last. It stays [openerId] when the hidden apps are shown, [appId] is not that app, is not listed
 * or has no neighbour (no tile is left, and the focus goes where Compose puts it).
 */
private fun AppsUiState.openerAfterHide(appId: Int, openerId: Int?): Int? {
    val index = items.indexOfFirst { it.id == appId }
    val neighbour =
        if (!showHidden && openerId == appId && index >= 0) {
            items.getOrNull(index + 1) ?: items.getOrNull(index - 1)
        } else {
            null
        }
    return neighbour?.id ?: openerId
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

/** The toggle of the header of a TV or handheld, as a button: a phone has [ShowHiddenToggle]. */
@Composable
private fun ShowHiddenButton(state: AppsUiState, onToggleShowHidden: () -> Unit) {
    BorealisButton(
        stringResource(toggleLabel(state)),
        onToggleShowHidden,
        style = BorealisButtonStyle.Secondary,
    )
}

/** The header of a TV or handheld; [stacked] puts the toggle under the title. */
@Composable
private fun Header(state: AppsUiState, stacked: Boolean, onToggleShowHidden: () -> Unit) {
    val gutter = LocalBorealisSpacing.current.gutter
    val padding = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = ROW_GAP)
    if (stacked) {
        Column(padding, verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            Title(state)
            ShowHiddenButton(state, onToggleShowHidden)
        }
    } else {
        Row(padding, verticalAlignment = Alignment.CenterVertically) {
            Title(state, Modifier.weight(1f))
            ShowHiddenButton(state, onToggleShowHidden)
        }
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
                    // An unknown host may have an app running that the last poll did not see.
                    state.hostChecking -> stringResource(R.string.hosts_status_checking)
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
    openerId: Int?,
    openerFocus: FocusRequester,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        when {
            state.items.isNotEmpty() -> {
                val look = LocalBorealisLook.current
                Grid(
                    state.items,
                    appGridColumns(
                        formFactor,
                        maxWidth.value.toInt(),
                        look.cardDensity,
                        look.textSpacing,
                    ),
                    onAppSelected,
                    onActions,
                    openerId,
                    openerFocus,
                )
            }
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
    openerId: Int?,
    openerFocus: FocusRequester,
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
                modifier =
                    Modifier.then(
                            if (app.id == firstId) Modifier.initialFocus(firstFocus) else Modifier
                        )
                        .then(
                            if (app.id == openerId) Modifier.focusRequester(openerFocus)
                            else Modifier
                        ),
                runningLabel = running.takeIf { app.running },
                hidden = app.hidden,
                hiddenLabel = hidden,
                onOptions = { onActions(app.id) },
                optionsLabel = options,
            )
        }
    }
}
